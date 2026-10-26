package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.dashboard.TurnoverService;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Quotes and pro formas: own numbering, no accounting value, turned into invoices. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE", "VIEW_DASHBOARD"})
class QuoteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private TurnoverService turnoverService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;


    private Client client;

    @BeforeEach
    void client() {
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Devis");
        client = clientRepository.save(client);
    }

    private Invoice draft(DocumentKind kind, PaymentMethod paymentMethod) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Site vitrine");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("5000.00"));
        line.setVatRate(BigDecimal.ZERO);
        InvoiceForm form = new InvoiceForm();
        form.setKind(kind);
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 6, 1));
        form.setDueDate(LocalDate.of(2026, 7, 1));
        form.setPaymentMethod(paymentMethod);
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    @Test
    void aQuoteIsIssuedAcceptedThenInvoicedOnce() {
        Invoice quote = invoiceService.issue(draft(DocumentKind.QUOTE, null).getId());
        assertThat(quote.getInvoiceNumber()).isEqualTo("DV-2026-0001");
        assertThatThrownBy(() -> invoiceService.pay(quote.getId())).hasMessageContaining("facturez-le d'abord");
        assertThatThrownBy(() -> invoiceService.createCreditNote(quote.getId())).isInstanceOf(BusinessException.class);

        invoiceService.accept(quote.getId());
        assertThat(invoiceService.findById(quote.getId()).getStatus()).isEqualTo(InvoiceStatus.ACCEPTED);

        Invoice invoice = invoiceService.convertToInvoice(quote.getId());
        assertThat(invoice.getKind()).isEqualTo(DocumentKind.INVOICE);
        assertThat(invoice.isDraft()).isTrue();
        assertThat(invoice.getSourceDocument().getId()).isEqualTo(quote.getId());
        assertThat(invoice.getClient().getId()).isEqualTo(client.getId());
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5000.00");
        // Asking again opens the same invoice
        assertThat(invoiceService.convertToInvoice(quote.getId()).getId()).isEqualTo(invoice.getId());
        // The invoice numbering is separate: the first invoice is still FA-2026-0001
        assertThat(invoiceService.issue(invoice.getId()).getInvoiceNumber()).isEqualTo("FA-2026-0001");
    }

    @Test
    void invoicingAQuoteNotAnsweredYetAcceptsIt() {
        Invoice quote = invoiceService.issue(draft(DocumentKind.QUOTE, null).getId());
        invoiceService.convertToInvoice(quote.getId());
        assertThat(invoiceService.findById(quote.getId()).getStatus()).isEqualTo(InvoiceStatus.ACCEPTED);
    }

    @Test
    void aRefusedQuoteCannotBeInvoiced() {
        Invoice draft = draft(DocumentKind.QUOTE, null);
        assertThatThrownBy(() -> invoiceService.accept(draft.getId())).hasMessageContaining("devis émis");
        Invoice quote = invoiceService.issue(draft.getId());
        invoiceService.refuse(quote.getId());
        assertThatThrownBy(() -> invoiceService.convertToInvoice(quote.getId())).hasMessageContaining("peut être facturé");
    }

    @Test
    void aProformaHasItsOwnNumberAndNoStampDuty() {
        Invoice proforma = invoiceService.issue(draft(DocumentKind.PROFORMA, PaymentMethod.CASH).getId());
        assertThat(proforma.getInvoiceNumber()).isEqualTo("PF-2026-0001");
        assertThat(proforma.getStampDutyAmount()).isEqualByComparingTo("0");
        // The invoice made from it, paid in cash, carries the duty
        Invoice invoice = invoiceService.convertToInvoice(proforma.getId());
        assertThat(invoice.getStampDutyAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void quotesStayOutOfTurnoverTheRegisterAndTheDashboard() throws Exception {
        invoiceService.issue(draft(DocumentKind.QUOTE, null).getId());
        invoiceService.issue(draft(DocumentKind.PROFORMA, null).getId());
        draft(DocumentKind.QUOTE, null);

        assertThat(invoiceService.totalBilled(2026)).isEqualByComparingTo("0");
        assertThat(turnoverService.report(2026).getBilled()).isEqualByComparingTo("0");
        assertThat(invoiceService.countDrafts()).isZero();
        assertThat(invoiceService.countUnpaid()).isZero();
        assertThat(invoiceService.findRecent()).isEmpty();

        mockMvc.perform(get("/invoices"))
            .andExpect(content().string(not(containsString("DV-2026-0001"))))
            .andExpect(content().string(not(containsString("PF-2026-0001"))));
        mockMvc.perform(get("/quotes"))
            .andExpect(content().string(containsString("DV-2026-0001")))
            .andExpect(content().string(containsString("PF-2026-0001")));
        MvcResult csv = mockMvc.perform(get("/invoices/export/csv")).andReturn();
        assertThat(csv.getResponse().getContentAsString()).doesNotContain("DV-2026-0001");
    }

    @Test
    void theClientIdentifiersAreCheckedOnTheInvoiceNotOnTheQuote() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setLegalForm("SARL");
        issuer.setShareCapital(new BigDecimal("100000"));
        issuer.setRcNumber("16/00-1");
        issuer.setTaxArticle("1601");
        client.setType(ClientType.PROFESSIONNEL);
        client.setNif("987654321012345");

        Invoice quote = invoiceService.issue(draft(DocumentKind.QUOTE, null).getId());
        Invoice invoice = invoiceService.convertToInvoice(quote.getId());
        assertThatThrownBy(() -> invoiceService.issue(invoice.getId())).hasMessageContaining("registre du commerce");
    }

    @Test
    void thePdfIsADevisThatIsNotAnInvoice() throws Exception {
        Invoice quote = invoiceService.issue(draft(DocumentKind.QUOTE, null).getId());
        String text;
        try (PDDocument pdf = PDDocument.load(pdfService.render(invoiceService.findWithLines(quote.getId())))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        }
        assertThat(text).contains("Devis DV-2026-0001").contains("VALABLE JUSQU'AU").contains("01/07/2026")
            .contains("Arrêté le présent devis à la somme de : cinq mille").contains("dinars algériens.")
            .contains("Ce document n'est pas une facture.");
    }
}
