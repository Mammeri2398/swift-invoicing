package com.swiftlabs.invoicing.accounting;

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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The accounting entries of a period for the accountant: sales, credit notes, cancellations, payments, exports. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountingTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Autowired
    private AccountingService accountingService;

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
        client.setName("Client Compta");
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

    private Invoice issued(LocalDate date, PaymentMethod method, InvoiceLineForm... lines) {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(date);
        form.setPaymentMethod(method);
        form.setLines(List.of(lines));
        return invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    private static List<AccountingJournal.Entry> of(AccountingJournal journal, String piece) {
        return journal.entries().stream().filter(entry -> entry.piece().equals(piece)).toList();
    }

    @Test
    void anInvoiceDebitsTheClientAndCreditsSalesTvaAndStampDuty() {
        // Cash: 1 000 HT at 19 % + 500 HT at 9 % = 1 735 TTC, droit de timbre 1 % = 17,35 rounded up to 18
        Invoice invoice = issued(LocalDate.of(2026, 9, 10), PaymentMethod.CASH, line("1000.00", "19"), line("500.00", "9"));

        AccountingJournal journal = accountingService.journal(FROM, TO);

        assertThat(of(journal, invoice.getInvoiceNumber()))
            .extracting(AccountingJournal.Entry::journal, AccountingJournal.Entry::account, AccountingJournal.Entry::debit,
                AccountingJournal.Entry::credit)
            .containsExactly(
                tuple("VT", "411", new BigDecimal("1753.00"), BigDecimal.ZERO),
                tuple("VT", "706", BigDecimal.ZERO, new BigDecimal("1500.00")),
                tuple("VT", "4457", BigDecimal.ZERO, new BigDecimal("190.00")),
                tuple("VT", "4457", BigDecimal.ZERO, new BigDecimal("45.00")),
                tuple("VT", "447", BigDecimal.ZERO, new BigDecimal("18.00")));
        assertThat(journal.entries().get(2).label()).isEqualTo("Facture " + invoice.getInvoiceNumber() + " Client Compta TVA 19 %");
        assertThat(journal.isBalanced()).isTrue();
        assertThat(journal.salesTotal()).isEqualByComparingTo("1753.00");
    }

    @Test
    void aCreditNoteIsReversed() {
        Invoice invoice = issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"), line("200.00", "19"));
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(invoiceService.createCreditNote(invoice.getId()).getId()));
        form.setLines(List.of(form.getLines().get(0)));
        form.setIssueDate(LocalDate.of(2026, 9, 20));
        Invoice creditNote = invoiceService.issue(invoiceService.saveDraft(form).getId());

        AccountingJournal journal = accountingService.journal(FROM, TO);

        assertThat(of(journal, creditNote.getInvoiceNumber()))
            .extracting(AccountingJournal.Entry::account, AccountingJournal.Entry::debit, AccountingJournal.Entry::credit)
            .containsExactly(tuple("411", BigDecimal.ZERO, new BigDecimal("1190.00")),
                tuple("706", new BigDecimal("1000.00"), BigDecimal.ZERO),
                tuple("4457", new BigDecimal("190.00"), BigDecimal.ZERO));
        assertThat(journal.sales()).extracting(AccountingJournal.Sale::total)
            .containsExactly(new BigDecimal("1428.00"), new BigDecimal("-1190.00"));
        assertThat(journal.sales().get(1).originalInvoice()).isEqualTo(invoice.getInvoiceNumber());
        assertThat(journal.isBalanced()).isTrue();
    }

    @Test
    void aCancelledInvoiceKeepsItsEntryAndIsReversedOnTheDayOfTheCancellation() {
        Invoice invoice = issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"));
        invoiceService.cancel(invoice.getId());
        LocalDate today = LocalDate.now();

        // September: the invoice as the accountant booked it
        assertThat(of(accountingService.journal(FROM, TO), invoice.getInvoiceNumber())).first()
            .satisfies(entry -> assertThat(entry.debit()).isEqualByComparingTo("1190.00"));
        // The day of the cancellation: the reversal
        AccountingJournal cancellation = accountingService.journal(today, today);
        assertThat(of(cancellation, invoice.getInvoiceNumber()))
            .extracting(AccountingJournal.Entry::account, AccountingJournal.Entry::credit)
            .containsExactly(tuple("411", new BigDecimal("1190.00")), tuple("706", BigDecimal.ZERO), tuple("4457", BigDecimal.ZERO));
        assertThat(cancellation.sales()).singleElement().satisfies(sale -> {
            assertThat(sale.cancellation()).isTrue();
            assertThat(sale.total()).isEqualByComparingTo("-1190.00");
        });
        // A draft cancelled before being issued has no entry
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(today);
        form.setPaymentMethod(PaymentMethod.CASH);
        form.setLines(List.of(line("50.00", "19")));
        invoiceService.cancel(invoiceService.saveDraft(form).getId());
        assertThat(accountingService.journal(today, today).sales()).hasSize(1);
    }

    @Test
    void aPaymentGoesToTheBankOrToTheCash() {
        Invoice invoice = issued(LocalDate.of(2026, 8, 10), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"));
        paymentService.record(invoice.getId(), new PaymentForm(new BigDecimal("700.00"), LocalDate.of(2026, 9, 5),
            PaymentMethod.BANK_TRANSFER, "VIR-1"));
        paymentService.record(invoice.getId(), new PaymentForm(new BigDecimal("490.00"), LocalDate.of(2026, 9, 6),
            PaymentMethod.CASH, null));

        AccountingJournal journal = accountingService.journal(FROM, TO);

        assertThat(journal.entries())
            .extracting(AccountingJournal.Entry::journal, AccountingJournal.Entry::account, AccountingJournal.Entry::debit,
                AccountingJournal.Entry::credit)
            .containsExactly(
                tuple("BQ", "512", new BigDecimal("700.00"), BigDecimal.ZERO),
                tuple("BQ", "411", BigDecimal.ZERO, new BigDecimal("700.00")),
                tuple("CA", "53", new BigDecimal("490.00"), BigDecimal.ZERO),
                tuple("CA", "411", BigDecimal.ZERO, new BigDecimal("490.00")));
        assertThat(journal.sales()).isEmpty();
        assertThat(journal.receiptsTotal()).isEqualByComparingTo("1190.00");
    }

    @Test
    void theAccountsCanBeChanged() {
        AccountingForm form = AccountingForm.from(accountingService.settings());
        form.setSalesAccount("7061");
        form.setBankJournal("bq1");
        accountingService.save(form);
        issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER, line("100.00", "19"));

        AccountingJournal journal = accountingService.journal(FROM, TO);

        assertThat(journal.entries()).extracting(AccountingJournal.Entry::account).contains("7061").doesNotContain("706");
        assertThat(accountingService.settings().getBankJournal()).isEqualTo("BQ1");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_DASHBOARD", "EDIT_ISSUER"})
    void thePageShowsTheEntriesAndSavesTheAccounts() throws Exception {
        Invoice invoice = issued(LocalDate.of(2026, 9, 10), PaymentMethod.BANK_TRANSFER, line("1000.00", "19"));
        mockMvc.perform(get("/accounting").param("from", "2026-09-01").param("to", "2026-09-30"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(invoice.getInvoiceNumber())))
            .andExpect(content().string(containsString("Écritures équilibrées")))
            .andExpect(content().string(containsString("1 190,00 DA")))
            .andExpect(content().string(containsString("Enregistrer")));
        mockMvc.perform(get("/accounting").param("from", "2026-09-01").param("to", "2026-09-30").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("Entries balanced")));
        // Without dates: the previous month
        mockMvc.perform(get("/accounting")).andExpect(status().isOk());

        mockMvc.perform(post("/accounting/accounts").with(csrf()).param("from", "2026-09-01").param("to", "2026-09-30")
                .param("customerAccount", "411").param("salesAccount", "70A").param("vatAccount", "4457").param("stampAccount", "447")
                .param("bankAccount", "512").param("cashAccount", "53").param("salesJournal", "VT").param("bankJournal", "BQ")
                .param("cashJournal", "CA"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("accounts", "salesAccount"));
        mockMvc.perform(post("/accounting/accounts").with(csrf()).param("from", "2026-09-01").param("to", "2026-09-30")
                .param("customerAccount", "411").param("salesAccount", "700").param("vatAccount", "4457").param("stampAccount", "447")
                .param("bankAccount", "512").param("cashAccount", "53").param("salesJournal", "VT").param("bankJournal", "BQ")
                .param("cashJournal", "CA"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/accounting?from=2026-09-01&to=2026-09-30"));
        assertThat(accountingService.settings().getSalesAccount()).isEqualTo("700");
    }

    @Test
    @WithMockUser(authorities = "VIEW_DASHBOARD")
    void withoutTheSettingsPrivilegeTheAccountsAreReadOnly() throws Exception {
        mockMvc.perform(get("/accounting"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("canEdit", false))
            .andExpect(content().string(not(containsString("<span class=\"icon\">save</span>"))));
        mockMvc.perform(post("/accounting/accounts").with(csrf()).param("salesAccount", "700"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    void thePageNeedsTheDashboardPrivilege() throws Exception {
        mockMvc.perform(get("/accounting")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VIEW_DASHBOARD")
    void thePeriodIsExportedToExcelAndCsv() throws Exception {
        Invoice invoice = issued(LocalDate.of(2026, 9, 10), PaymentMethod.CASH, line("1000.00", "19"));
        byte[] file = mockMvc.perform(get("/accounting/export/xlsx").param("from", "2026-09-01").param("to", "2026-09-30"))
            .andExpect(header().string("Content-Disposition", containsString("comptabilite-2026-09-01-2026-09-30.xlsx")))
            .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Écritures");
            assertThat(workbook.getSheetName(1)).isEqualTo("Ventes");
            assertThat(workbook.getSheetName(2)).isEqualTo("Encaissements");
            assertThat(workbook.getSheetName(3)).isEqualTo("Comptes");
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(2).getStringCellValue()).isEqualTo(invoice.getInvoiceNumber());
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(3).getStringCellValue()).isEqualTo("411");
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(6).getNumericCellValue()).isEqualTo(1202.0);
            // Total row: debit = credit
            assertThat(workbook.getSheetAt(0).getRow(7).getCell(6).getNumericCellValue())
                .isEqualTo(workbook.getSheetAt(0).getRow(7).getCell(7).getNumericCellValue());
        }
        byte[] csv = mockMvc.perform(get("/accounting/export/csv").param("from", "2026-09-01").param("to", "2026-09-30"))
            .andExpect(header().string("Content-Disposition", containsString(".csv")))
            .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Journal;Date;Pièce;Compte;Tiers;Libellé;Débit;Crédit\r\n");
        assertThat(text).contains("VT;10/09/2026;" + invoice.getInvoiceNumber() + ";411;Client Compta;Facture " + invoice.getInvoiceNumber()
            + " Client Compta;1202,00;0,00\r\n");
    }
}
