package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.config.ClockConfig;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
// Same database as the other tests (H2 in PostgreSQL mode): numbering uses PostgreSQL SQL
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InvoiceService.class, IssuerService.class, ClientService.class, InvoiceAudit.class, DocumentSequenceRepository.class, PaymentService.class, ClockConfig.class})
@EnableConfigurationProperties(InvoiceProperties.class)
class InvoiceServiceTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    private Client particular() {
        Client c = new Client();
        c.setType(ClientType.PARTICULIER);
        c.setName("Client Particulier");
        return clientRepository.save(c);
    }

    private InvoiceLineForm line(String description, String qty, String price) {
        InvoiceLineForm lf = new InvoiceLineForm();
        lf.setDescription(description);
        lf.setUnit("heure");
        lf.setQuantity(new BigDecimal(qty));
        lf.setUnitPrice(new BigDecimal(price));
        return lf;
    }

    private InvoiceForm form(Long id, Client client, LocalDate issueDate, InvoiceLineForm... lines) {
        InvoiceForm form = new InvoiceForm();
        form.setId(id);
        form.setClientId(client.getId());
        form.setIssueDate(issueDate);
        form.setLines(List.of(lines));
        return form;
    }

    private Invoice draft(Client client, LocalDate issueDate, BigDecimal qty, BigDecimal price) {
        return invoiceService.saveDraft(form(null, client, issueDate,
            line("Prestation", qty.toPlainString(), price.toPlainString())));
    }

    @Test
    void numberingIsSequentialWithoutGapsAndResetsPerYear() {
        Client c1 = particular();
        Client c2 = particular();

        Invoice issued1 = invoiceService.issue(draft(c1, LocalDate.of(2026, 1, 10), new BigDecimal("1.000"), new BigDecimal("100.00")).getId());
        assertThat(issued1.getInvoiceNumber()).isEqualTo("FA-2026-0001");
        assertThat(issued1.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issued1.getIssuedAt()).isNotNull();

        Invoice issued2 = invoiceService.issue(draft(c2, LocalDate.of(2026, 2, 5), new BigDecimal("1.000"), new BigDecimal("200.00")).getId());
        assertThat(issued2.getInvoiceNumber()).isEqualTo("FA-2026-0002");

        // restart at 0001 for a new year
        Invoice issued3 = invoiceService.issue(draft(c1, LocalDate.of(2025, 6, 1), new BigDecimal("1.000"), new BigDecimal("50.00")).getId());
        assertThat(issued3.getInvoiceNumber()).isEqualTo("FA-2025-0001");
    }

    @Test
    void issuingAnAlreadyIssuedInvoiceFails() {
        Client c = particular();
        Invoice issued = invoiceService.issue(draft(c, LocalDate.of(2026, 3, 1), new BigDecimal("1.000"), new BigDecimal("10.00")).getId());
        assertThatThrownBy(() -> invoiceService.issue(issued.getId()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void savingADraftOverAnIssuedInvoiceFails() {
        Client c = particular();
        Invoice issued = invoiceService.issue(draft(c, LocalDate.of(2026, 3, 1), new BigDecimal("1.000"), new BigDecimal("10.00")).getId());

        // Simulates POST /invoices/save with the id of an issued invoice (new object, status DRAFT)
        InvoiceForm forged = form(issued.getId(), c, LocalDate.of(2026, 3, 1), line("Modifié", "1.000", "999.00"));
        assertThatThrownBy(() -> invoiceService.saveDraft(forged))
            .isInstanceOf(BusinessException.class);

        Invoice reloaded = invoiceService.findById(issued.getId());
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(reloaded.getInvoiceNumber()).isEqualTo(issued.getInvoiceNumber());
    }

    private List<Invoice> searchYear(int year) {
        InvoiceFilter filter = new InvoiceFilter();
        filter.setYear(year);
        return invoiceService.search(filter, PageRequest.of(0, 20)).getContent();
    }

    @Test
    void listFilteredByYearIncludesDraftsByIssueDate() {
        Client c = particular();
        Invoice d = draft(c, LocalDate.of(2026, 5, 1), new BigDecimal("1.000"), new BigDecimal("10.00"));
        Invoice issued = invoiceService.issue(draft(c, LocalDate.of(2025, 5, 1), new BigDecimal("1.000"), new BigDecimal("10.00")).getId());

        assertThat(searchYear(2026)).extracting(Invoice::getId).containsExactly(d.getId());
        assertThat(searchYear(2025)).extracting(Invoice::getId).containsExactly(issued.getId());
    }

    @Test
    void invoiceNumberUniquenessCheckWorks() {
        Client c = particular();
        // Pre-seed an issued invoice with a number directly in the database
        Invoice seeded = new Invoice();
        seeded.setClient(c);
        seeded.setInvoiceNumber("FA-2026-0050");
        seeded.setInvoiceYear(2026);
        seeded.setSequenceNumber(50);
        seeded.setStatus(InvoiceStatus.ISSUED);
        seeded.setTotalAmount(BigDecimal.TEN);
        invoiceRepository.save(seeded);
        assertThat(invoiceRepository.existsByInvoiceNumber("FA-2026-0050")).isTrue();
    }

    @Test
    void totalIsSumOfLines() {
        Client c = particular();
        Invoice saved = invoiceService.saveDraft(form(null, c, LocalDate.of(2026, 4, 1),
            line("A", "2.000", "10.00"), line("B", "1.000", "33.33")));
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("53.33"));
    }
}
