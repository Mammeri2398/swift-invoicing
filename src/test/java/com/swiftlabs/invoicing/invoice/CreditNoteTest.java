package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.dashboard.TurnoverReport;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "samia", authorities = {"VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE", "VIEW_DASHBOARD"})
class CreditNoteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private InvoiceAudit audit;

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
        client.setName("Client Avoir");
        client = clientRepository.save(client);
    }

    private static InvoiceLineForm line(String description, String price, String vatRate) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setVatRate(new BigDecimal(vatRate));
        return line;
    }

    private Invoice issuedInvoice(InvoiceLineForm... lines) {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 3, 10));
        form.setLines(List.of(lines));
        return invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    /** Keeps only the given lines of a draft credit note (partial credit), dated in April. */
    private Invoice keepLines(Invoice creditNote, int... indexes) {
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(creditNote.getId()));
        List<InvoiceLineForm> kept = new java.util.ArrayList<>();
        for (int index : indexes) {
            kept.add(form.getLines().get(index));
        }
        form.setLines(kept);
        form.setIssueDate(LocalDate.of(2026, 4, 2));
        return invoiceService.saveDraft(form);
    }

    @Test
    void aFullCreditNoteCancelsTheInvoiceWithItsOwnNumber() {
        Invoice invoice = issuedInvoice(line("Développement", "1000.00", "0"), line("Hébergement", "500.00", "0"));
        Invoice draft = invoiceService.createCreditNote(invoice.getId());
        assertThat(draft.isCreditNote()).isTrue();
        assertThat(draft.getClient().getId()).isEqualTo(client.getId());
        assertThat(draft.getTotalAmount()).isEqualByComparingTo("1500.00");

        Invoice creditNote = invoiceService.issue(draft.getId());
        assertThat(creditNote.getInvoiceNumber()).isEqualTo("AV-2026-0001");
        assertThat(invoiceService.remainingToCredit(invoice)).isEqualByComparingTo("0");
        // The invoice keeps its status (it still counts, the credit note is subtracted)
        assertThat(invoiceService.findById(invoice.getId()).getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(audit.history(invoice.getId())).extracting(InvoiceEvent::getAction, InvoiceEvent::getDetail)
            .contains(tuple(InvoiceEvent.Action.CREDITED, "AV-2026-0001 (1 500,00 DA)"));

        assertThatThrownBy(() -> invoiceService.pay(invoice.getId())).hasMessageContaining("rien à encaisser");
        assertThatThrownBy(() -> invoiceService.cancel(invoice.getId())).hasMessageContaining("déjà un avoir");
        assertThatThrownBy(() -> invoiceService.createCreditNote(invoice.getId())).hasMessageContaining("entièrement annulée");
        assertThatThrownBy(() -> invoiceService.cancel(creditNote.getId())).hasMessage("Un avoir émis ne peut pas être annulé.");
        assertThatThrownBy(() -> invoiceService.pay(creditNote.getId())).hasMessage("Un avoir ne se marque pas payé.");
        assertThat(invoiceService.countUnpaid()).isZero();
        assertThat(invoiceService.totalUnpaid()).isEqualByComparingTo("0");
    }

    @Test
    void partialCreditNotesNeverExceedTheInvoice() {
        Invoice invoice = issuedInvoice(line("Développement", "1000.00", "0"), line("Hébergement", "500.00", "0"));
        Invoice first = invoiceService.issue(keepLines(invoiceService.createCreditNote(invoice.getId()), 1).getId());
        assertThat(first.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(invoiceService.remainingToCredit(invoice)).isEqualByComparingTo("1000.00");
        assertThat(invoiceService.countUnpaid()).isEqualTo(1);
        assertThat(invoiceService.totalUnpaid()).isEqualByComparingTo("1000.00");

        // A second full copy would go over the remaining 1 000,00 DA
        Invoice tooMuch = invoiceService.createCreditNote(invoice.getId());
        assertThatThrownBy(() -> invoiceService.issue(tooMuch.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("dépasse le reste à annuler")
            .hasMessageContaining("1 000,00 DA");
        Invoice second = invoiceService.issue(keepLines(tooMuch, 0).getId());
        assertThat(second.getInvoiceNumber()).isEqualTo("AV-2026-0002");
        assertThat(invoiceService.remainingToCredit(invoice)).isEqualByComparingTo("0");
    }

    @Test
    void creditNotesHaveTheirOwnNumberingAndRules() {
        Invoice invoice = issuedInvoice(line("Prestation", "100.00", "0"));
        assertThat(invoice.getInvoiceNumber()).isEqualTo("FA-2026-0001");
        Invoice draft = invoiceService.createCreditNote(invoice.getId());
        // Asking again returns the same draft
        assertThat(invoiceService.createCreditNote(invoice.getId()).getId()).isEqualTo(draft.getId());

        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.setIssueDate(LocalDate.of(2026, 3, 1));
        invoiceService.saveDraft(form);
        assertThatThrownBy(() -> invoiceService.issue(draft.getId())).hasMessageContaining("avant la facture");

        form.setIssueDate(LocalDate.of(2026, 3, 20));
        invoiceService.saveDraft(form);
        assertThat(invoiceService.issue(draft.getId()).getInvoiceNumber()).isEqualTo("AV-2026-0001");
        assertThat(issuedInvoice(line("Prestation", "100.00", "0")).getInvoiceNumber()).isEqualTo("FA-2026-0002");

        InvoiceForm other = new InvoiceForm();
        other.setClientId(client.getId());
        other.setIssueDate(LocalDate.of(2026, 3, 10));
        other.setLines(List.of(line("Brouillon", "10.00", "0")));
        Invoice unissued = invoiceService.saveDraft(other);
        assertThatThrownBy(() -> invoiceService.createCreditNote(unissued.getId())).hasMessageContaining("émise ou payée");
    }

    @Test
    void turnoverAndTvaAreReducedInTheMonthOfTheCreditNote() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setLegalForm("SARL");
        issuer.setShareCapital(new BigDecimal("100000"));
        issuer.setRcNumber("16/00-1");
        issuer.setTaxArticle("1601");
        Invoice invoice = issuedInvoice(line("Développement", "1000.00", "19"), line("Formation", "500.00", "9"));
        invoiceService.issue(keepLines(invoiceService.createCreditNote(invoice.getId()), 0).getId());

        TurnoverReport report = turnoverService.report(2026);
        TurnoverReport.Month march = report.getMonths().get(2);
        TurnoverReport.Month april = report.getMonths().get(3);
        assertThat(march.billed()).isEqualByComparingTo("1500.00");
        assertThat(april.credited()).isEqualByComparingTo("1000.00");
        assertThat(april.billed()).isEqualByComparingTo("-1000.00");
        assertThat(report.getBilled()).isEqualByComparingTo("500.00");
        // TVA: 190 + 45 collected in March, 190 given back in April
        assertThat(report.getVat()).isEqualByComparingTo("45.00");
        assertThat(invoiceService.totalBilled(2026)).isEqualByComparingTo("500.00");
    }

    @Test
    void thePdfIsAnAvoirThatNamesItsInvoice() throws Exception {
        Invoice invoice = issuedInvoice(line("Développement", "1000.00", "0"));
        Invoice creditNote = invoiceService.issue(invoiceService.createCreditNote(invoice.getId()).getId());
        String text;
        try (PDDocument pdf = PDDocument.load(pdfService.render(invoiceService.findWithLines(creditNote.getId())))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        }
        assertThat(text).contains("AVOIR").contains("AV-2026-0001").contains("Sur la facture FA-2026-0001 du 10/03/2026")
            .contains("Total avoir 1 000,00 DA").contains("Arrêté le présent avoir à la somme de : mille dinars algériens")
            .doesNotContain("FACTURE\n");
    }

    @Test
    void thePagesShowAndLinkTheCreditNotes() throws Exception {
        Invoice invoice = issuedInvoice(line("Développement", "1000.00", "0"));
        MvcResult created = mockMvc.perform(post("/invoices/" + invoice.getId() + "/credit-note").with(csrf()))
            .andExpect(flash().attributeExists("message"))
            .andReturn();
        String editUrl = created.getResponse().getRedirectedUrl();
        Long creditNoteId = Long.valueOf(editUrl.replaceAll("\\D+(\\d+)\\D*", "$1"));
        mockMvc.perform(get(editUrl))
            .andExpect(content().string(containsString("Avoir sur la facture FA-2026-0001")))
            .andExpect(content().string(not(containsString("Sélectionner un client"))));

        invoiceService.issue(creditNoteId);
        mockMvc.perform(get("/invoices/" + invoice.getId()))
            .andExpect(content().string(containsString("Annulée par avoir")))
            .andExpect(content().string(containsString("AV-2026-0001")))
            .andExpect(content().string(containsString("Avoir émis")))
            .andExpect(content().string(not(containsString("Marquer payée"))))
            .andExpect(content().string(not(containsString("Créer un avoir"))));
        mockMvc.perform(get("/invoices/" + creditNoteId))
            .andExpect(content().string(containsString("Avoir AV-2026-0001")))
            .andExpect(content().string(containsString("Avoir sur la facture")))
            .andExpect(content().string(containsString("Montant de l&#39;avoir")));
        mockMvc.perform(get("/invoices").param("kind", "CREDIT_NOTE"))
            .andExpect(content().string(containsString("- 1 000,00 DA")))
            .andExpect(content().string(not(containsString(">FA-2026-0001<"))));
        // List total: the invoice and its credit note cancel out
        mockMvc.perform(get("/invoices"))
            .andExpect(content().string(containsString("Total : <strong class=\"text-on-surface\">0,00 DA</strong>")));

        MvcResult csv = mockMvc.perform(get("/invoices/export/csv").param("kind", "CREDIT_NOTE")).andReturn();
        String row = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).split("\r\n")[1];
        assertThat(row).startsWith("AV-2026-0001;Émise;").contains(";-1000,00;0,00;-1000,00;").endsWith(";Avoir;FA-2026-0001;0,00");
    }
}
