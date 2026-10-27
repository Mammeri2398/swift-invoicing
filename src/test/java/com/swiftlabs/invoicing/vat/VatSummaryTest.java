package com.swiftlabs.invoicing.vat;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.invoice.PaymentForm;
import com.swiftlabs.invoicing.invoice.PaymentMethod;
import com.swiftlabs.invoicing.invoice.PaymentService;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The monthly TVA summary of a company (help for the G50 declaration), on invoicing and on collection. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VatSummaryTest {

    @Autowired
    private VatSummaryService vatService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private MockMvc mockMvc;

    private Client client;

    @BeforeEach
    void company() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setFullName("Atlas Services");
        issuer.setLegalForm("SARL");
        issuer.setRcNumber("16/00-1234567B21");
        issuer.setTaxArticle("16012345678");
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client TVA");
        client = clientRepository.save(client);
    }

    private static InvoiceLineForm line(String price, String vatRate) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation " + vatRate + " %");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setVatRate(new BigDecimal(vatRate));
        return line;
    }

    private Invoice draft(LocalDate date, PaymentMethod method, InvoiceLineForm... lines) {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(date);
        form.setPaymentMethod(method);
        form.setLines(List.of(lines));
        return invoiceService.saveDraft(form);
    }

    private Invoice issued(LocalDate date, PaymentMethod method, InvoiceLineForm... lines) {
        return invoiceService.issue(draft(date, method, lines).getId());
    }

    /** A credit note on the first line of the invoice only, dated as given. */
    private Invoice creditNote(Invoice invoice, LocalDate date) {
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(invoiceService.createCreditNote(invoice.getId()).getId()));
        form.setLines(List.of(form.getLines().get(0)));
        form.setIssueDate(date);
        return invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    @Test
    void onInvoicingTheMonthCountsItsInvoicesLessItsCreditNotes() {
        Invoice mixed = issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER,
            line("1000.00", "19"), line("500.00", "9"), line("200.00", "0"));
        // Not counted: a draft, a cancelled invoice, an invoice of the next month (issued in date order, as numbers follow dates)
        draft(LocalDate.of(2026, 9, 12), PaymentMethod.CASH, line("9999.00", "19"));
        invoiceService.cancel(issued(LocalDate.of(2026, 9, 15), PaymentMethod.CASH, line("7777.00", "19")).getId());
        // Cash: 1 190,00 TTC, droit de timbre 1 % = 11,90, rounded up to 12
        issued(LocalDate.of(2026, 9, 20), PaymentMethod.CASH, line("1000.00", "19"));
        creditNote(mixed, LocalDate.of(2026, 9, 25));
        issued(LocalDate.of(2026, 10, 1), PaymentMethod.BANK_TRANSFER, line("5555.00", "19"));

        VatSummary september = vatService.month(2026, 9, VatBasis.INVOICED);

        assertThat(september.rates()).extracting(r -> r.rate().stripTrailingZeros().toPlainString(), VatSummary.Rate::base, VatSummary.Rate::vat)
            .containsExactly(tuple("19", new BigDecimal("1000.00"), new BigDecimal("190.00")),
                tuple("9", new BigDecimal("500.00"), new BigDecimal("45.00")));
        assertThat(september.exemptBase()).isEqualByComparingTo("200.00");
        assertThat(september.stampDuty()).isEqualByComparingTo("12");
        assertThat(september.vat()).isEqualByComparingTo("235.00");
        assertThat(september.exclVat()).isEqualByComparingTo("1700.00");
        assertThat(september.entries()).hasSize(3);
        assertThat(september.entries().get(2).total()).isEqualByComparingTo("-1190.00");
    }

    @Test
    void onCollectionAPaymentCarriesItsShareOfTheTva() {
        Invoice invoice = issued(LocalDate.of(2026, 8, 5), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"));
        paymentService.record(invoice.getId(), new PaymentForm(new BigDecimal("595.00"), LocalDate.of(2026, 9, 12),
            PaymentMethod.BANK_TRANSFER, "VIR-1"));

        VatSummary september = vatService.month(2026, 9, VatBasis.COLLECTED);
        VatSummary august = vatService.month(2026, 8, VatBasis.COLLECTED);

        assertThat(september.rates()).singleElement().satisfies(rate -> {
            assertThat(rate.base()).isEqualByComparingTo("500.00");
            assertThat(rate.vat()).isEqualByComparingTo("95.00");
        });
        assertThat(september.exemptBase()).isEqualByComparingTo("0");
        assertThat(september.entries()).singleElement().satisfies(entry -> assertThat(entry.total()).isEqualByComparingTo("595.00"));
        assertThat(august.isEmpty()).isTrue();
        assertThat(vatService.year(2026, VatBasis.COLLECTED)).hasSize(12);
    }

    @Test
    @WithMockUser(authorities = "VIEW_DASHBOARD")
    void thePageShowsTheMonthByRate() throws Exception {
        issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"));
        mockMvc.perform(get("/vat").param("year", "2026").param("month", "9"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Opérations imposables à 19 %")))
            .andExpect(content().string(containsString("190,00 DA")))
            .andExpect(content().string(containsString("TVA collectée à déclarer")));
        mockMvc.perform(get("/vat").param("year", "2026").param("month", "9").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("Taxable transactions at 19 %")));
        // Without a month: the previous one, the month being declared
        mockMvc.perform(get("/vat")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "VIEW_DASHBOARD")
    void anAutoEntrepreneurIsToldTvaDoesNotApply() throws Exception {
        issuerService.getIssuer().setCompanyType(CompanyType.AUTO_ENTREPRENEUR);
        mockMvc.perform(get("/vat")).andExpect(content().string(containsString("TVA non applicable")));
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    void thePageNeedsTheDashboardPrivilege() throws Exception {
        mockMvc.perform(get("/vat")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VIEW_DASHBOARD")
    void theMonthIsExportedToExcel() throws Exception {
        issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"));
        byte[] file = mockMvc.perform(get("/vat/export").param("year", "2026").param("month", "9"))
            .andExpect(header().string("Content-Disposition", containsString("tva-2026-09.xlsx")))
            .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Récapitulatif");
            assertThat(workbook.getSheetAt(0).getRow(4).getCell(0).getStringCellValue()).isEqualTo("Opérations imposables à 19 %");
            assertThat(workbook.getSheetAt(0).getRow(4).getCell(2).getNumericCellValue()).isEqualTo(190.0);
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(1).getStringCellValue()).startsWith("FA-2026-");
        }
    }
}
