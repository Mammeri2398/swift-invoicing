package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Database-side filtering and pagination of the invoice list, and the lines mapping. */
@SpringBootTest
@Transactional
class InvoiceSearchTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Client atlas;
    private Client percent;

    @BeforeEach
    void data() {
        // Only this test's invoices (the in-memory database is shared with other tests)
        jdbcTemplate.update("DELETE FROM invoice_line");
        jdbcTemplate.update("DELETE FROM invoice");
        atlas = client("SARL Atlas");
        percent = client("Remise 100%_Max");
    }

    private Client client(String name) {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName(name);
        return clientRepository.save(client);
    }

    private InvoiceLineForm line(String description, String quantity, String price) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(price));
        return line;
    }

    private Invoice draft(Client client, LocalDate issueDate, String amount) {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(issueDate);
        form.setLines(List.of(line("Prestation", "1", amount)));
        return invoiceService.saveDraft(form);
    }

    private Page<Invoice> search(InvoiceFilter filter, int page, int size) {
        return invoiceService.search(filter, PageRequest.of(page, size, Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("id"))));
    }

    private List<Long> ids(InvoiceFilter filter) {
        return search(filter, 0, 50).getContent().stream().map(Invoice::getId).toList();
    }

    @Test
    void filtersByStatusYearClientAndSearch() {
        Invoice draft2026 = draft(atlas, LocalDate.of(2026, 3, 1), "100.00");
        Invoice issued2026 = invoiceService.issue(draft(atlas, LocalDate.of(2026, 6, 1), "200.00").getId());
        Invoice draft2025 = draft(percent, LocalDate.of(2025, 12, 31), "300.00");

        InvoiceFilter byStatus = new InvoiceFilter();
        byStatus.setStatus(InvoiceStatus.ISSUED);
        assertThat(ids(byStatus)).containsExactly(issued2026.getId());

        InvoiceFilter byYear = new InvoiceFilter();
        byYear.setYear(2025);
        assertThat(ids(byYear)).containsExactly(draft2025.getId());

        InvoiceFilter byClient = new InvoiceFilter();
        byClient.setClient(atlas.getId());
        assertThat(ids(byClient)).containsExactly(issued2026.getId(), draft2026.getId());

        InvoiceFilter byNumber = new InvoiceFilter();
        byNumber.setSearch(issued2026.getInvoiceNumber().toLowerCase());
        assertThat(ids(byNumber)).containsExactly(issued2026.getId());

        InvoiceFilter byClientName = new InvoiceFilter();
        byClientName.setSearch("  atlas ");
        assertThat(ids(byClientName)).containsExactlyInAnyOrder(issued2026.getId(), draft2026.getId());
    }

    @Test
    void searchTreatsPercentAndUnderscoreLiterally() {
        Invoice withPercent = draft(percent, LocalDate.of(2026, 1, 10), "10.00");
        draft(atlas, LocalDate.of(2026, 1, 11), "10.00");

        InvoiceFilter percentSign = new InvoiceFilter();
        percentSign.setSearch("%");
        assertThat(ids(percentSign)).containsExactly(withPercent.getId());

        InvoiceFilter underscore = new InvoiceFilter();
        underscore.setSearch("_");
        assertThat(ids(underscore)).containsExactly(withPercent.getId());
    }

    @Test
    void paginatesAndTotalsAllMatchingInvoices() {
        for (int day = 1; day <= 25; day++) {
            draft(atlas, LocalDate.of(2026, 2, day), "10.00");
        }
        InvoiceFilter all = new InvoiceFilter();

        Page<Invoice> first = search(all, 0, 20);
        Page<Invoice> second = search(all, 1, 20);
        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(5);
        assertThat(first.getContent().get(0).getIssueDate()).isEqualTo(LocalDate.of(2026, 2, 25));
        // The total covers the 25 invoices, not only the page shown
        assertThat(invoiceService.totalOf(all)).isEqualByComparingTo("250.00");
    }

    @Test
    void editingADraftReplacesItsLines() {
        Invoice invoice = draft(atlas, LocalDate.of(2026, 4, 1), "100.00");

        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(invoice.getId()));
        form.setLines(List.of(line("Conseil", "2", "50.00"), line("Déplacement", "1", "30.00")));
        invoiceService.saveDraft(form);
        entityManager.flush();
        entityManager.clear();

        Invoice reloaded = invoiceService.findWithLines(invoice.getId());
        assertThat(reloaded.getLines()).extracting(InvoiceLine::getDescription).containsExactly("Conseil", "Déplacement");
        assertThat(reloaded.getLines()).extracting(InvoiceLine::getPosition).containsExactly(1, 2);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("130.00");
        // The old line was deleted (orphan removal), not left behind
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_line WHERE invoice_id = ?",
            Integer.class, invoice.getId())).isEqualTo(2);
    }

    @Test
    void deletingADraftDeletesItsLines() {
        Invoice invoice = draft(atlas, LocalDate.of(2026, 4, 2), "100.00");
        invoiceService.delete(invoice.getId());
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_line WHERE invoice_id = ?",
            Integer.class, invoice.getId())).isZero();
    }
}
