package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvoiceAuditTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceAudit audit;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private MockMvc mockMvc;

    private Invoice draft(ClientType type, String nif) {
        Client client = new Client();
        client.setType(type);
        client.setName("Client audit");
        client.setNif(nif);
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("10.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 7, 1));
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    @Test
    @WithMockUser(username = "karim", authorities = {"CREATE_INVOICE", "ISSUE_INVOICE"})
    void everyActionIsRecordedWithItsAuthor() {
        Invoice invoice = draft(ClientType.PARTICULIER, null);
        invoiceService.issue(invoice.getId());
        invoiceService.pay(invoice.getId());
        invoiceService.cancel(invoice.getId());

        assertThat(audit.history(invoice.getId()))
            .extracting(InvoiceEvent::getAction, InvoiceEvent::getUsername)
            .containsExactly(
                tuple(InvoiceEvent.Action.CREATED, "karim"),
                tuple(InvoiceEvent.Action.ISSUED, "karim"),
                // "Payer le solde" records one payment of the whole balance, then the invoice is paid
                tuple(InvoiceEvent.Action.PAYMENT, "karim"),
                tuple(InvoiceEvent.Action.PAID, "karim"),
                tuple(InvoiceEvent.Action.CANCELLED, "karim"));
        assertThat(audit.history(invoice.getId())).allMatch(event -> event.getOccurredAt() != null);
    }

    @Test
    @WithMockUser(username = "karim")
    void editingADraftIsNotACreation() {
        Invoice invoice = draft(ClientType.PARTICULIER, null);
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(invoice.getId()));
        form.setNotes("Modifié");
        invoiceService.saveDraft(form);

        // Created once, then edited (with what changed), by the same user
        List<InvoiceEvent> history = audit.history(invoice.getId());
        assertThat(history).extracting(InvoiceEvent::getAction)
            .containsExactly(InvoiceEvent.Action.CREATED, InvoiceEvent.Action.EDITED);
        assertThat(history.get(1).getUsername()).isEqualTo("karim");
        assertThat(audit.changes(history.get(1))).containsExactly("Notes : — → Modifié");
    }

    @Test
    @WithMockUser(username = "karim")
    void aRefusedActionRecordsNothing() {
        // Professional client without NIF: issuing is refused
        Invoice invoice = draft(ClientType.PROFESSIONNEL, null);
        assertThatThrownBy(() -> invoiceService.issue(invoice.getId())).isInstanceOf(BusinessException.class);

        assertThat(audit.history(invoice.getId())).extracting(InvoiceEvent::getAction)
            .containsExactly(InvoiceEvent.Action.CREATED);
    }

    @Test
    @WithMockUser(username = "samia", authorities = {"VIEW_INVOICES", "ISSUE_INVOICE"})
    void historyIsShownOnTheInvoicePage() throws Exception {
        Invoice invoice = draft(ClientType.PARTICULIER, null);
        invoiceService.issue(invoice.getId());

        mockMvc.perform(get("/invoices/" + invoice.getId()))
            .andExpect(content().string(containsString("Historique")))
            .andExpect(content().string(containsString("Brouillon créé")))
            .andExpect(content().string(containsString("Facture émise")))
            .andExpect(content().string(containsString("samia")));
    }

    /** V7 rebuilds the history of invoices that existed before the audit trail, without author. */
    @Test
    void migrationRebuildsTheHistoryOfExistingInvoices() throws Exception {
        String url = "jdbc:h2:mem:v7migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("6").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO client (type, name) VALUES ('PARTICULIER', 'Ancien client')");
            st.executeUpdate("INSERT INTO invoice (invoice_number, invoice_year, sequence_number, client_id, issue_date, status,"
                + " created_at, issued_at, paid_at) VALUES ('FA-2026-0001', 2026, 1, (SELECT MAX(id) FROM client), DATE '2026-01-05',"
                + " 'PAID', TIMESTAMP '2026-01-05 09:00:00', TIMESTAMP '2026-01-05 10:00:00', TIMESTAMP '2026-02-01 08:00:00')");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("7").load().migrate();

            List<String> events = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT action, username FROM invoice_event ORDER BY occurred_at")) {
                while (rs.next()) {
                    events.add(rs.getString(1) + ":" + rs.getString(2));
                }
            }
            assertThat(events).containsExactly("CREATED:null", "ISSUED:null", "PAID:null");
        }
    }
}
