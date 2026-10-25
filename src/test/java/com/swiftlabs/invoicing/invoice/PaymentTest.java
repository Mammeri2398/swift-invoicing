package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Partial payments, balances, overdue invoices, statements and reminders. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "samia", authorities = {"VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE", "VIEW_DASHBOARD", "VIEW_CLIENTS"})
class PaymentTest {

    private static final LocalDate ISSUED_ON = LocalDate.of(2026, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private InvoiceMailService mailService;

    @Autowired
    private InvoiceAudit audit;

    @Autowired
    private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void client() {
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Paiements");
        client.setEmail("client@example.test");
        client = clientRepository.save(client);
    }

    private Invoice issued(String price, PaymentMethod method, LocalDate dueDate) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setVatRate(BigDecimal.ZERO);
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(ISSUED_ON);
        form.setDueDate(dueDate);
        form.setPaymentMethod(method);
        form.setLines(List.of(line));
        return invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    private InvoicePayment pay(Invoice invoice, String amount, LocalDate on) {
        return paymentService.record(invoice.getId(), new PaymentForm(new BigDecimal(amount), on, PaymentMethod.CHEQUE, "CHQ 0042"));
    }

    @Test
    void partialPaymentsThenTheBalanceMakeTheInvoicePaid() {
        Invoice invoice = issued("1000.00", PaymentMethod.CHEQUE, null);
        pay(invoice, "400.00", LocalDate.of(2026, 2, 1));
        PaymentService.Summary summary = paymentService.summary(invoice);
        assertThat(summary.paid()).isEqualByComparingTo("400.00");
        assertThat(summary.balance()).isEqualByComparingTo("600.00");
        assertThat(summary.isPartiallyPaid()).isTrue();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

        assertThatThrownBy(() -> pay(invoice, "700.00", LocalDate.of(2026, 2, 2)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("dépasse le solde de la facture (600,00 DA)");
        pay(invoice, "600.00", LocalDate.of(2026, 3, 1));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(audit.history(invoice.getId())).extracting(InvoiceEvent::getAction, InvoiceEvent::getDetail)
            .contains(org.assertj.core.groups.Tuple.tuple(InvoiceEvent.Action.PAYMENT, "400,00 DA le 01/02/2026 (chèque, CHQ 0042)"));
        assertThatThrownBy(() -> pay(invoice, "1.00", LocalDate.of(2026, 3, 2))).hasMessageContaining("pas encore payée");
    }

    @Test
    void datesAndDocumentsAreChecked() {
        Invoice invoice = issued("1000.00", null, null);
        assertThatThrownBy(() -> pay(invoice, "100.00", LocalDate.now().plusDays(1))).hasMessageContaining("futur");
        assertThatThrownBy(() -> pay(invoice, "100.00", ISSUED_ON.minusDays(1))).hasMessageContaining("précéder la facture");
        Invoice creditNote = invoiceService.issue(invoiceService.createCreditNote(invoice.getId()).getId());
        assertThatThrownBy(() -> pay(creditNote, "100.00", LocalDate.of(2026, 2, 1))).isInstanceOf(BusinessException.class);
    }

    @Test
    void deletingAPaymentReopensThePaidInvoice() {
        Invoice invoice = issued("1000.00", null, null);
        Invoice other = issued("50.00", null, null);
        InvoicePayment payment = pay(invoice, "1000.00", LocalDate.of(2026, 2, 1));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThatThrownBy(() -> paymentService.delete(other.getId(), payment.getId())).isInstanceOf(ResourceNotFoundException.class);

        paymentService.delete(invoice.getId(), payment.getId());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(paymentService.balance(invoice)).isEqualByComparingTo("1000.00");
        assertThat(audit.history(invoice.getId())).extracting(InvoiceEvent::getAction).contains(InvoiceEvent.Action.PAYMENT_DELETED);
    }

    @Test
    void aCreditNoteForTheRestSettlesAPartlyPaidInvoice() {
        Invoice invoice = issued("1000.00", null, null);
        pay(invoice, "600.00", LocalDate.of(2026, 2, 1));
        Invoice draft = invoiceService.createCreditNote(invoice.getId());
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.getLines().get(0).setUnitPrice(new BigDecimal("400.00"));
        invoiceService.saveDraft(form);
        invoiceService.issue(draft.getId());
        assertThat(invoiceService.findById(invoice.getId()).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void theBalanceOfACashInvoiceIncludesTheStampDuty() {
        Invoice invoice = issued("1000.00", PaymentMethod.CASH, null);
        assertThat(paymentService.balance(invoice)).isEqualByComparingTo("1010.00");
        invoiceService.pay(invoice.getId());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paymentService.paymentsOf(invoice.getId())).extracting(InvoicePayment::getAmount)
            .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("1010.00"));
    }

    @Test
    void unpaidFiguresAndOverdueInvoicesDeductPayments() throws Exception {
        Invoice late = issued("1000.00", null, LocalDate.now().minusDays(10));
        pay(late, "300.00", LocalDate.of(2026, 2, 1));
        Invoice notYetDue = issued("500.00", null, LocalDate.now().plusDays(10));
        Invoice paidLate = issued("200.00", null, LocalDate.now().minusDays(30));
        pay(paidLate, "200.00", LocalDate.of(2026, 2, 1));

        assertThat(invoiceService.countUnpaid()).isEqualTo(2);
        assertThat(invoiceService.totalUnpaid()).isEqualByComparingTo("1200.00");
        assertThat(paymentService.overdue()).extracting(o -> o.invoice().getId(), o -> o.balance().toPlainString(), PaymentService.Overdue::daysLate)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(late.getId(), "700.00", 10L));
        assertThat(paymentService.balancesByClient().get(client.getId())).isEqualByComparingTo("1200.00");

        mockMvc.perform(get("/invoices").param("overdue", "true"))
            .andExpect(content().string(containsString(late.getInvoiceNumber())))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(notYetDue.getInvoiceNumber()))));
        mockMvc.perform(get("/dashboard"))
            .andExpect(content().string(containsString("1 facture(s) en retard")))
            .andExpect(content().string(containsString("700,00 DA")));
        mockMvc.perform(get("/invoices/" + late.getId()))
            .andExpect(content().string(containsString("Reste à payer : 700,00 DA")))
            .andExpect(content().string(containsString("Partiellement payée")))
            .andExpect(content().string(containsString("En retard")));
        mockMvc.perform(get("/clients"))
            .andExpect(content().string(containsString("1 200,00 DA")));
    }

    @Test
    void theStatementCarriesTheBalanceOverThePeriod() throws Exception {
        Invoice invoice = issued("1000.00", null, null);
        pay(invoice, "400.00", LocalDate.of(2026, 2, 5));
        Invoice draft = invoiceService.createCreditNote(invoice.getId());
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.getLines().get(0).setUnitPrice(new BigDecimal("100.00"));
        form.setIssueDate(LocalDate.of(2026, 3, 1));
        invoiceService.saveDraft(form);
        invoiceService.issue(draft.getId());

        StatementService.Statement statement = statementService.statement(client.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 12, 31));
        assertThat(statement.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(statement.entries()).extracting(e -> e.credit().toPlainString(), e -> e.balance().toPlainString())
            .containsExactly(org.assertj.core.groups.Tuple.tuple("400.00", "600.00"), org.assertj.core.groups.Tuple.tuple("100.00", "500.00"));
        assertThat(statement.closingBalance()).isEqualByComparingTo("500.00");

        mockMvc.perform(get("/clients/" + client.getId() + "/statement").param("from", "2026-01-01").param("to", "2026-12-31"))
            .andExpect(content().string(containsString("Relevé de compte")))
            .andExpect(content().string(containsString("Paiement par chèque (CHQ 0042)")))
            .andExpect(content().string(containsString("500,00 DA")));
    }

    @Test
    void aReminderStatesWhatIsLeftToPay() throws Exception {
        Invoice invoice = issued("1000.00", null, LocalDate.of(2026, 2, 10));
        pay(invoice, "250.00", LocalDate.of(2026, 2, 1));
        InvoiceEmailForm reminder = mailService.prepareReminder(invoice.getId());
        assertThat(reminder.isReminder()).isTrue();
        assertThat(reminder.getSubject()).startsWith("Relance : facture " + invoice.getInvoiceNumber());
        assertThat(reminder.getMessage()).contains("échue le 10/02/2026").contains("750,00 DA");
        mockMvc.perform(get("/invoices/" + invoice.getId() + "/email").param("reminder", "true"))
            .andExpect(content().string(containsString("Relancer par email")));

        pay(invoice, "750.00", LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> mailService.prepareReminder(invoice.getId())).hasMessageContaining("rien à payer");
    }

    @Test
    void exportsShowWhatWasPaidAndWhatIsLeft() throws Exception {
        Invoice invoice = issued("1000.00", null, null);
        pay(invoice, "400.00", LocalDate.of(2026, 2, 1));
        MvcResult csv = mockMvc.perform(get("/invoices/export/csv")).andReturn();
        String[] rows = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).split("\r\n");
        assertThat(rows[0]).endsWith(";Payé (DA);Solde (DA)");
        assertThat(rows[1]).startsWith(invoice.getInvoiceNumber()).endsWith(";400,00;600,00");
    }

    /** V17: invoices already marked paid get one payment of what they owed, so their balance is zero. */
    @Test
    void migrationGivesPaidInvoicesTheirPayment() throws Exception {
        String url = "jdbc:h2:mem:v17migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("16").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO client (type, name) VALUES ('PARTICULIER', 'Ancien client')");
            st.executeUpdate("INSERT INTO invoice (invoice_number, invoice_year, sequence_number, client_id, issue_date, status, kind,"
                + " total_amount, subtotal_amount, stamp_duty_amount, payment_method, created_at, paid_at) VALUES ('FA-2026-0001', 2026, 1,"
                + " (SELECT MAX(id) FROM client), DATE '2026-01-05', 'PAID', 'INVOICE', 1000.00, 1000.00, 10.00, 'CASH',"
                + " TIMESTAMP '2026-01-05 09:00:00', TIMESTAMP '2026-02-01 10:00:00')");
            st.executeUpdate("INSERT INTO invoice (invoice_number, invoice_year, sequence_number, client_id, issue_date, status, kind,"
                + " total_amount, subtotal_amount, created_at) VALUES ('FA-2026-0002', 2026, 2, (SELECT MAX(id) FROM client),"
                + " DATE '2026-01-06', 'ISSUED', 'INVOICE', 500.00, 500.00, TIMESTAMP '2026-01-06 09:00:00')");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("17").load().migrate();

            try (ResultSet rs = st.executeQuery("SELECT amount, paid_on, method FROM invoice_payment")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("1010.00");
                assertThat(rs.getDate(2).toLocalDate()).isEqualTo(LocalDate.of(2026, 2, 1));
                assertThat(rs.getString(3)).isEqualTo("CASH");
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
