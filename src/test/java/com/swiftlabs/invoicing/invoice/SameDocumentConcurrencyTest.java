package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.company.CompanySchemas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The same document changed from several threads at once (a double click, two tabs), each in its own committed
 * transaction: one change goes through, the others are refused, nothing is done twice.
 */
@SpringBootTest
class SameDocumentConcurrencyTest {

    private static final String COMPANY = CompanySchemas.FIRST + ".";
    /** A year no other test uses, in the past (documents are not issued in the future). */
    private static final int YEAR = 2011;
    private static final int THREADS = 6;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Client client;

    @BeforeEach
    void createClient() {
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client double clic");
        client = clientRepository.save(client);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM " + COMPANY + "invoice WHERE client_id = ?", client.getId());
        jdbcTemplate.update("DELETE FROM " + COMPANY + "client WHERE id = ?", client.getId());
        jdbcTemplate.update("DELETE FROM " + COMPANY + "invoice_sequence WHERE invoice_year = ?", YEAR);
    }

    private Long draft() {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("1000.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(YEAR, 3, 1));
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form).getId();
    }

    /** Runs the task from every thread at once; returns how many went through. Only refusals may fail. */
    private int runConcurrently(Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Object> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                }
            }
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aDraftIssuedTwiceAtOnceTakesOneNumber() throws Exception {
        Long id = draft();

        assertThat(runConcurrently(() -> invoiceService.issue(id))).isEqualTo(1);

        assertThat(invoiceService.findById(id).getInvoiceNumber()).isEqualTo("FA-" + YEAR + "-0001");
        assertThat(jdbcTemplate.queryForObject("SELECT last_value FROM " + COMPANY + "invoice_sequence WHERE invoice_year = ?",
            Integer.class, YEAR)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + COMPANY + "invoice_event WHERE invoice_id = ? AND action = 'ISSUED'",
            Integer.class, id)).isEqualTo(1);
    }

    @Test
    void aPaymentSentTwiceAtOnceIsRecordedOnce() throws Exception {
        Long id = draft();
        invoiceService.issue(id);
        BigDecimal balance = paymentService.balance(invoiceService.findById(id));

        assertThat(runConcurrently(() -> paymentService.record(id,
            new PaymentForm(balance, LocalDate.of(YEAR, 3, 15), PaymentMethod.BANK_TRANSFER, null)))).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + COMPANY + "invoice_payment WHERE invoice_id = ?",
            Integer.class, id)).isEqualTo(1);
        assertThat(invoiceService.findById(id).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void aDraftSavedFromAnOlderPageIsRefused() {
        Long id = draft();
        InvoiceForm firstTab = InvoiceForm.from(invoiceService.findWithLines(id));
        InvoiceForm secondTab = InvoiceForm.from(invoiceService.findWithLines(id));
        firstTab.getLines().get(0).setDescription("Modifiée dans le premier onglet");
        invoiceService.saveDraft(firstTab);

        secondTab.getLines().get(0).setDescription("Modifiée dans le second onglet");
        assertThatThrownBy(() -> invoiceService.saveDraft(secondTab))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode())
            .isEqualTo("invoice.error.modifiedMeanwhile");
    }
}
