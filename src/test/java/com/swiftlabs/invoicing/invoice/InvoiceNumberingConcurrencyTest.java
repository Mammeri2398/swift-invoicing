package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issues invoices from several threads at once, each in its own committed transaction
 * (no @Transactional on the test), and checks that numbers stay unique and gap-free.
 */
@SpringBootTest
class InvoiceNumberingConcurrencyTest {

    /** Raw SQL runs outside the application's sessions: the company's tables are named with their schema. */
    private static final String COMPANY = CompanySchemas.FIRST + ".";

    /** A past year no other test uses (documents are not issued in the future): its numbering row does not exist yet. */
    private static final int YEAR = 2013;
    private static final int THREADS = 8;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Client client;

    @BeforeEach
    void createClient() {
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client concurrence");
        client = clientRepository.save(client);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM " + COMPANY + "invoice_line WHERE invoice_id IN (SELECT id FROM " + COMPANY + "invoice WHERE client_id = ?)", client.getId());
        jdbcTemplate.update("DELETE FROM " + COMPANY + "invoice WHERE client_id = ?", client.getId());
        jdbcTemplate.update("DELETE FROM " + COMPANY + "client WHERE id = ?", client.getId());
        jdbcTemplate.update("DELETE FROM " + COMPANY + "invoice_sequence WHERE invoice_year = ?", YEAR);
    }

    private List<Long> drafts(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            InvoiceLineForm line = new InvoiceLineForm();
            line.setDescription("Prestation " + i);
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(new BigDecimal("1000.00"));
            InvoiceForm form = new InvoiceForm();
            form.setClientId(client.getId());
            form.setIssueDate(LocalDate.of(YEAR, 1, 2));
            form.setLines(List.of(line));
            ids.add(invoiceService.saveDraft(form).getId());
        }
        return ids;
    }

    /** Issues all drafts at the same time and returns the numbers obtained. Fails on any error. */
    private Set<String> issueConcurrently(List<Long> draftIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(draftIds.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (Long id : draftIds) {
                Callable<String> task = () -> {
                    start.await();
                    return invoiceService.issue(id).getInvoiceNumber();
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            Set<String> numbers = new TreeSet<>();
            for (Future<String> result : results) {
                numbers.add(result.get(30, TimeUnit.SECONDS));
            }
            return numbers;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Set<String> expectedNumbers(int from, int to) {
        return IntStream.rangeClosed(from, to)
            .mapToObj(n -> String.format("FA-%d-%04d", YEAR, n))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void firstInvoicesOfANewYearGetUniqueGapFreeNumbers() throws Exception {
        Set<String> numbers = issueConcurrently(drafts(THREADS));
        assertThat(numbers).isEqualTo(expectedNumbers(1, THREADS));
    }

    @Test
    void concurrentInvoicesOfAnExistingYearGetUniqueGapFreeNumbers() throws Exception {
        issueConcurrently(drafts(1));
        Set<String> numbers = issueConcurrently(drafts(THREADS));
        assertThat(numbers).isEqualTo(expectedNumbers(2, THREADS + 1));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT last_value FROM " + COMPANY + "invoice_sequence WHERE invoice_year = ?", Integer.class, YEAR)).isEqualTo(THREADS + 1);
    }
}
