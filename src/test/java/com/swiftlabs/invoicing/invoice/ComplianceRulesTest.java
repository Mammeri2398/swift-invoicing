package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientForm;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerForm;
import com.swiftlabs.invoicing.issuer.IssuerService;

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Numbers that follow the dates, the auto-entrepreneur's own TVA mention, and the length of NIF and NIS. */
@SpringBootTest
@Transactional
class ComplianceRulesTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceProperties properties;

    @Autowired
    private Validator validator;

    private Client client;

    @BeforeEach
    void autoEntrepreneur() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.AUTO_ENTREPRENEUR);
        issuer.setAeCardNumber("AE-123456");
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Conformité");
        client = clientRepository.save(client);
    }

    private Invoice draft(LocalDate date) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("1000.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(date);
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    @Test
    void anInvoiceCannotBeDatedBeforeTheOneIssuedLast() {
        Invoice first = invoiceService.issue(draft(LocalDate.of(2026, 9, 20)).getId());
        Invoice late = draft(LocalDate.of(2026, 9, 15));

        assertThatThrownBy(() -> invoiceService.issue(late.getId()))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(MessageProperties.INVOICE_ERROR_DATE_BEFORE_LAST);
                assertThat(ex.getMessage()).contains(first.getInvoiceNumber()).contains("20/09/2026");
            });
        assertThat(invoiceService.findById(late.getId()).isDraft()).isTrue();

        // The same day is fine, and the refused attempt used no number
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(late.getId()));
        form.setIssueDate(LocalDate.of(2026, 9, 20));
        invoiceService.saveDraft(form);
        assertThat(invoiceService.issue(late.getId()).getSequenceNumber()).isEqualTo(first.getSequenceNumber() + 1);
    }

    @Test
    void aDocumentDatedInTheFutureIsNotIssued() {
        // 2062 typed for 2026 would otherwise take a number no later document of the year could follow
        Invoice ahead = draft(invoiceService.today().plusDays(1));
        assertThatThrownBy(() -> invoiceService.issue(ahead.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode())
            .isEqualTo(MessageProperties.INVOICE_ERROR_DATE_IN_FUTURE);
        assertThat(invoiceService.findById(ahead.getId()).isDraft()).isTrue();
        assertThat(invoiceService.issue(draft(invoiceService.today()).getId()).getInvoiceNumber()).isNotNull();
    }

    @Test
    void eachNumberingFollowsItsOwnDates() {
        Invoice invoice = invoiceService.issue(draft(LocalDate.of(2026, 9, 10)).getId());
        invoiceService.issue(draft(LocalDate.of(2026, 9, 20)).getId());
        // Credit notes have their own numbers: dated before the last invoice, after its own invoice
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(invoiceService.createCreditNote(invoice.getId()).getId()));
        form.setIssueDate(LocalDate.of(2026, 9, 15));
        Invoice creditNote = invoiceService.issue(invoiceService.saveDraft(form).getId());
        assertThat(creditNote.getInvoiceNumber()).startsWith("AV-2026-");
        // Another year has its own numbering (an earlier one: documents are not issued in the future)
        assertThat(invoiceService.issue(draft(LocalDate.of(2025, 12, 30)).getId()).getInvoiceNumber()).isEqualTo("FA-2025-0001");
    }

    @Test
    void theAutoEntrepreneurSetsTheMentionAndAnIssuedInvoiceKeepsIt() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setVatMention("TVA non applicable, article X");
        Invoice issued = invoiceService.issue(draft(LocalDate.of(2026, 9, 10)).getId());
        issuer.setVatMention("Nouvelle mention");
        Invoice draft = draft(LocalDate.of(2026, 9, 11));

        assertThat(issued.getIssuerVatMention()).isEqualTo("TVA non applicable, article X");
        assertThat(IssuerDetails.of(issued, issuer, properties.vatMention()).getVatMention()).isEqualTo("TVA non applicable, article X");
        assertThat(IssuerDetails.of(draft, issuer, properties.vatMention()).getVatMention()).isEqualTo("Nouvelle mention");
        // Left empty: the default
        issuer.setVatMention(null);
        assertThat(IssuerDetails.of(draft, issuer, properties.vatMention()).getVatMention()).isEqualTo(properties.vatMention());
    }

    @Test
    void anEmptyMentionInTheSettingsMeansTheDefault() {
        IssuerForm form = IssuerForm.from(issuerService.getIssuer());
        form.setNif("000016001234567");
        form.setVatMention("   ");
        assertThat(issuerService.save(form).getVatMention()).isNull();
        form.setVatMention(" TVA non applicable (régime auto-entrepreneur) ");
        assertThat(issuerService.save(form).getVatMention()).isEqualTo("TVA non applicable (régime auto-entrepreneur)");
    }

    @Test
    void aNifHas15Or20DigitsAndANis15() {
        ClientForm form = new ClientForm();
        form.setType(ClientType.PARTICULIER);
        form.setName("Client");
        for (String nif : List.of("000016001234567", "00001600123456789012", "")) {
            form.setNif(nif);
            assertThat(validator.validateProperty(form, "nif")).as(nif).isEmpty();
        }
        for (String nif : List.of("1234", "0000160012345678", "000 016 001 234 567")) {
            form.setNif(nif);
            assertThat(validator.validateProperty(form, "nif")).as(nif).isNotEmpty();
        }
        form.setNis("123456789012345");
        assertThat(validator.validateProperty(form, "nis")).isEmpty();
        form.setNis("12345678901234");
        assertThat(validator.validateProperty(form, "nis")).isNotEmpty();
    }
}
