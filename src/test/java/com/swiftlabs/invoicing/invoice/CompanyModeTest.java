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
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** The issuer as a company: identifiers, TVA per line, HT / TVA / TTC everywhere, no annual ceiling. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE", "VIEW_ISSUER", "EDIT_ISSUER", "VIEW_DASHBOARD"})
class CompanyModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private TurnoverService turnoverService;

    @Autowired
    private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void companyIssuer() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setFullName("Atlas Solutions");
        issuer.setLegalForm("SARL");
        issuer.setShareCapital(new BigDecimal("100000.00"));
        issuer.setRcNumber("16/00-1234567B21");
        issuer.setTaxArticle("16012345678");
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client TVA");
        client = clientRepository.save(client);
    }

    private static InvoiceLineForm line(String description, String price, String vatRate) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setVatRate(vatRate != null ? new BigDecimal(vatRate) : null);
        return line;
    }

    private Invoice draft(InvoiceLineForm... lines) {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 6, 15));
        form.setLines(List.of(lines));
        return invoiceService.saveDraft(form);
    }

    @Test
    void tvaIsComputedPerRateOnTheTotalOfItsLines() {
        Invoice invoice = draft(line("Développement", "1000.00", "19"), line("Formation", "333.33", "9"),
            line("Livre", "100.00", "0"));

        assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo("1433.33");
        // 333.33 x 9 % = 29.9997, rounded to 30.00
        assertThat(invoice.getVatBreakdown()).extracting(v -> v.rate().toPlainString(), v -> v.base().toPlainString(), v -> v.amount().toPlainString())
            .containsExactly(tuple("19", "1000.00", "190.00"), tuple("9", "333.33", "30.00"));
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("220.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1653.33");
    }

    @Test
    void onlyTheConfiguredRatesAreAccepted() {
        assertThatThrownBy(() -> draft(line("Prestation", "100.00", "7")))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getField()).isEqualTo("lines[0].vatRate"));
        assertThatThrownBy(() -> draft(line("Prestation", "100.00", null))).isInstanceOf(BusinessException.class);
    }

    @Test
    void anAutoEntrepreneurNeverChargesTva() {
        issuerService.getIssuer().setCompanyType(CompanyType.AUTO_ENTREPRENEUR);
        Invoice invoice = draft(line("Prestation", "1000.00", "19"));
        assertThat(invoice.getLines().get(0).getVatRate()).isEqualByComparingTo("0");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void issuingFreezesTheCompanyIdentifiersAndThePdfShowsTheTva() throws Exception {
        Invoice issued = invoiceService.issue(draft(line("Développement", "1000.00", "19"), line("Formation", "500.00", "9")).getId());
        assertThat(issued.getIssuerCompanyType()).isEqualTo(CompanyType.COMPANY);
        assertThat(issued.getIssuerRcNumber()).isEqualTo("16/00-1234567B21");

        // Switching the profile back does not change an issued invoice
        issuerService.getIssuer().setCompanyType(CompanyType.AUTO_ENTREPRENEUR);
        String text;
        try (PDDocument pdf = PDDocument.load(pdfService.render(invoiceService.findWithLines(issued.getId())))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        }
        // The header prints the legal status in capitals, as for an auto-entrepreneur
        assertThat(text).contains("SARL au capital de 100 000,00 DA")
            .contains("RC : 16/00-1234567B21").contains("Article d'imposition : 16012345678")
            .contains("Sous-total hors taxes 1 500,00 DA").contains("TVA 19 % 190,00 DA").contains("TVA 9 % 45,00 DA")
            .contains("Total TTC à payer 1 735,00 DA")
            .contains("à la somme de : mille sept").contains("cent trente-cinq dinars algériens")
            .doesNotContain("TVA non applicable").doesNotContain("Auto-entrepreneur");
    }

    @Test
    void aDraftWithTvaCannotBeIssuedOnceTheProfileIsAnAutoEntrepreneur() {
        Invoice invoice = draft(line("Prestation", "1000.00", "19"));
        issuerService.getIssuer().setCompanyType(CompanyType.AUTO_ENTREPRENEUR);
        assertThatThrownBy(() -> invoiceService.issue(invoice.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("contient de la TVA");
    }

    @Test
    void turnoverIsBeforeTvaWithTheTvaCollectedAndNoCeiling() throws Exception {
        invoiceService.issue(draft(line("Développement", "1000.00", "19")).getId());

        TurnoverReport report = turnoverService.report(2026);
        assertThat(report.isCompany()).isTrue();
        assertThat(report.getBilled()).isEqualByComparingTo("1000.00");
        assertThat(report.getVat()).isEqualByComparingTo("190.00");
        assertThat(report.getMonths().get(5).overCeiling()).isFalse();

        mockMvc.perform(get("/turnover").param("year", "2026"))
            .andExpect(content().string(containsString("TVA collectée")))
            .andExpect(content().string(not(containsString("Plafond annuel"))));
        mockMvc.perform(get("/dashboard"))
            .andExpect(content().string(not(containsString("Reste avant plafond"))))
            .andExpect(content().string(containsString("Chiffre d&#39;affaires HT")));
    }

    @Test
    void theFormOffersTheRatesAndTheInvoicePageShowsTheTotals() throws Exception {
        mockMvc.perform(get("/invoices/new"))
            .andExpect(content().string(containsString("data-vat-rate")))
            .andExpect(content().string(containsString(">Exonéré</option>")))
            .andExpect(content().string(containsString("Total TTC :")));
        Invoice invoice = draft(line("Développement", "1000.00", "19"));
        mockMvc.perform(get("/invoices/" + invoice.getId()))
            .andExpect(content().string(containsString("Total HT")))
            .andExpect(content().string(containsString("TVA 19 %")))
            .andExpect(content().string(containsString("1 190,00 DA")));
    }

    @Test
    void aCompanyProfileNeedsItsIdentifiers() throws Exception {
        mockMvc.perform(post("/settings/issuer/save").with(csrf())
                .param("id", "1").param("companyType", "COMPANY").param("fullName", "Atlas Solutions")
                .param("nif", "000016001234567").param("annualCeiling", "5000000"))
            .andExpect(model().attributeHasFieldErrors("issuer", "legalForm"));
        mockMvc.perform(post("/settings/issuer/save").with(csrf())
                .param("id", "1").param("companyType", "COMPANY").param("fullName", "Atlas Solutions")
                .param("nif", "000016001234567").param("annualCeiling", "5000000")
                .param("legalForm", "SARL").param("shareCapital", "100000").param("rcNumber", "16/00-1")
                .param("taxArticle", "16012345678"))
            .andExpect(redirectedUrl("/settings/issuer"));
        assertThat(issuerService.getIssuer().isCompany()).isTrue();

        // Back to auto-entrepreneur: the card number is required again
        mockMvc.perform(post("/settings/issuer/save").with(csrf())
                .param("id", "1").param("companyType", "AUTO_ENTREPRENEUR").param("fullName", "Karim Benali")
                .param("nif", "000016001234567").param("annualCeiling", "5000000"))
            .andExpect(model().attributeHasFieldErrors("issuer", "aeCardNumber"));
    }

    /** V12: invoices created before TVA keep their amount as HT and TTC, and are marked as auto-entrepreneur invoices. */
    @Test
    void migrationKeepsExistingInvoicesAsAutoEntrepreneurInvoices() throws Exception {
        String url = "jdbc:h2:mem:v12migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("11").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO client (type, name) VALUES ('PARTICULIER', 'Ancien client')");
            st.executeUpdate("INSERT INTO invoice (invoice_number, invoice_year, sequence_number, client_id, issue_date, status,"
                + " total_amount, created_at) VALUES ('FA-2026-0001', 2026, 1, (SELECT MAX(id) FROM client), DATE '2026-01-05',"
                + " 'ISSUED', 62000.00, TIMESTAMP '2026-01-05 09:00:00')");

            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("12").load().migrate();

            List<String> rows = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT subtotal_amount, vat_amount, total_amount, issuer_company_type FROM invoice")) {
                while (rs.next()) {
                    rows.add(rs.getBigDecimal(1).toPlainString() + "|" + rs.getBigDecimal(2).toPlainString() + "|"
                        + rs.getBigDecimal(3).toPlainString() + "|" + rs.getString(4));
                }
            }
            assertThat(rows).containsExactly("62000.00|0.00|62000.00|AUTO_ENTREPRENEUR");
            try (ResultSet rs = st.executeQuery("SELECT company_type FROM issuer WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("AUTO_ENTREPRENEUR");
            }
        }
    }
}
