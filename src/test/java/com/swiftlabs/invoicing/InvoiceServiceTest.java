package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.domain.Client;
import com.swiftlabs.invoicing.domain.ClientType;
import com.swiftlabs.invoicing.domain.Invoice;
import com.swiftlabs.invoicing.domain.InvoiceStatus;
import com.swiftlabs.invoicing.dto.InvoiceLineForm;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.repository.ClientRepository;
import com.swiftlabs.invoicing.repository.InvoiceRepository;
import com.swiftlabs.invoicing.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import com.swiftlabs.invoicing.service.IssuerService;
import com.swiftlabs.invoicing.service.ClientService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({InvoiceService.class, IssuerService.class, ClientService.class})
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

    private Invoice draft(Client client, LocalDate issueDate, BigDecimal qty, BigDecimal price) {
        Invoice inv = invoiceService.newDraft();
        inv.setClient(client);
        inv.setIssueDate(issueDate);
        InvoiceLineForm lf = new InvoiceLineForm();
        lf.setDescription("Prestation");
        lf.setUnit("heure");
        lf.setQuantity(qty);
        lf.setUnitPrice(price);
        return invoiceService.saveDraft(inv, List.of(lf));
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
        Invoice forged = invoiceService.newDraft();
        forged.setId(issued.getId());
        forged.setClient(c);
        InvoiceLineForm lf = new InvoiceLineForm();
        lf.setDescription("Modifié");
        lf.setQuantity(new BigDecimal("1.000"));
        lf.setUnitPrice(new BigDecimal("999.00"));
        assertThatThrownBy(() -> invoiceService.saveDraft(forged, List.of(lf)))
            .isInstanceOf(BusinessException.class);

        Invoice reloaded = invoiceService.findById(issued.getId());
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(reloaded.getInvoiceNumber()).isEqualTo(issued.getInvoiceNumber());
    }

    @Test
    void listFilteredByYearIncludesDraftsByIssueDate() {
        Client c = particular();
        Invoice d = draft(c, LocalDate.of(2026, 5, 1), new BigDecimal("1.000"), new BigDecimal("10.00"));
        Invoice issued = invoiceService.issue(draft(c, LocalDate.of(2025, 5, 1), new BigDecimal("1.000"), new BigDecimal("10.00")).getId());

        assertThat(invoiceService.list(null, 2026, null, null)).extracting(Invoice::getId).containsExactly(d.getId());
        assertThat(invoiceService.list(null, 2025, null, null)).extracting(Invoice::getId).containsExactly(issued.getId());
    }

    @Test
    void invoiceNumberUniquenessCheckWorks() {
        Client c = particular();
        // Pre-seed an issued invoice with a number directly in the database
        Invoice seeded = new com.swiftlabs.invoicing.domain.Invoice();
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
        Invoice inv = invoiceService.newDraft();
        inv.setClient(c);
        inv.setIssueDate(LocalDate.of(2026, 4, 1));
        InvoiceLineForm l1 = new InvoiceLineForm();
        l1.setDescription("A");
        l1.setQuantity(new BigDecimal("2.000"));
        l1.setUnitPrice(new BigDecimal("10.00"));
        InvoiceLineForm l2 = new InvoiceLineForm();
        l2.setDescription("B");
        l2.setQuantity(new BigDecimal("1.000"));
        l2.setUnitPrice(new BigDecimal("33.33"));
        Invoice saved = invoiceService.saveDraft(inv, List.of(l1, l2));
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("53.33"));
    }
}
