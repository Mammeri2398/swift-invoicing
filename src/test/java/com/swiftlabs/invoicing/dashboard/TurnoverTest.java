package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_DASHBOARD", "VIEW_ISSUER"})
class TurnoverTest {

    @Autowired
    private TurnoverService turnoverService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void data() {
        issuerService.getIssuer().setAnnualCeiling(new BigDecimal("1000.00"));
        Invoice paid = issue("600.00", LocalDate.of(2026, 1, 15));
        invoiceService.pay(paid.getId());
        issue("300.00", LocalDate.of(2026, 1, 20));
        issue("250.00", LocalDate.of(2026, 3, 5));
        invoiceService.cancel(issue("5000.00", LocalDate.of(2026, 3, 9)).getId());
        draft("777.00", LocalDate.of(2026, 3, 1));
        issue("80.00", LocalDate.of(2025, 6, 1));
    }

    private Invoice draft(String price, LocalDate date) {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client CA");
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(date);
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    private Invoice issue(String price, LocalDate date) {
        return invoiceService.issue(draft(price, date).getId());
    }

    @Test
    void monthsCountIssuedAndPaidInvoicesButNotCancelledOrDrafts() {
        TurnoverReport report = turnoverService.report(2026);

        assertThat(report.getMonths()).hasSize(12);
        TurnoverReport.Month january = report.getMonths().get(0);
        assertThat(january.name()).isEqualTo("Janvier");
        assertThat(january.invoiceCount()).isEqualTo(2);
        assertThat(january.billed()).isEqualByComparingTo("900.00");
        assertThat(january.paid()).isEqualByComparingTo("600.00");
        assertThat(january.pending()).isEqualByComparingTo("300.00");
        assertThat(january.overCeiling()).isFalse();

        TurnoverReport.Month march = report.getMonths().get(2);
        assertThat(march.billed()).isEqualByComparingTo("250.00");
        assertThat(march.cancelledCount()).isEqualTo(1);
        assertThat(march.cancelledAmount()).isEqualByComparingTo("5000.00");
        assertThat(march.cumulative()).isEqualByComparingTo("1150.00");
        assertThat(march.overCeiling()).isTrue();

        assertThat(report.getBilled()).isEqualByComparingTo("1150.00");
        assertThat(report.getInvoiceCount()).isEqualTo(3);
        assertThat(report.getUsage().isAlert100()).isTrue();
        assertThat(report.getUsage().getRemaining()).isEqualByComparingTo("0");
    }

    @Test
    void yearsWithInvoicesAndTheCurrentYearAreOffered() {
        assertThat(turnoverService.years(2027)).containsExactly(2027, 2026, 2025);
        assertThat(turnoverService.years(2026)).containsExactly(2026, 2025);
    }

    @Test
    void pageShowsTheYearAndTheCeilingChosenInTheSettings() throws Exception {
        mockMvc.perform(get("/turnover").param("year", "2026"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Exercice 2026")))
            .andExpect(content().string(containsString("1 150,00 DA")))
            .andExpect(content().string(containsString("sur 1 000,00 DA")))
            .andExpect(content().string(containsString("Le plafond est atteint")))
            .andExpect(content().string(containsString("/invoices/export/xlsx?year=2026")));
    }

    @Test
    void monthNamesFollowTheLanguage() {
        assertThat(turnoverService.report(2026, Language.EN).getMonths().get(0))
            .extracting(TurnoverReport.Month::name, TurnoverReport.Month::shortName).containsExactly("January", "Jan");
        assertThat(turnoverService.report(2026, Language.FR).getMonths().get(6).shortName()).isEqualTo("Juil.");
        assertThat(turnoverService.report(2026, Language.AR).getMonths().get(0).name()).isEqualTo("جانفي");
    }

    @Test
    void pageIsInTheBrowserLanguage() throws Exception {
        mockMvc.perform(get("/turnover").param("year", "2026").header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Financial year 2026")))
            .andExpect(content().string(containsString("of 1 000,00 DA")))
            .andExpect(content().string(containsString("The ceiling has been reached")))
            .andExpect(content().string(containsString(">January<")))
            .andExpect(content().string(containsString("3 invoices issued")));
        mockMvc.perform(get("/turnover").param("year", "2026").header("Accept-Language", "ar"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("dir=\"rtl\"")))
            .andExpect(content().string(containsString("السنة المالية 2026")))
            .andExpect(content().string(containsString(">جانفي<")));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_DASHBOARD", "CREATE_INVOICE"})
    void dashboardIsInTheBrowserLanguage() throws Exception {
        mockMvc.perform(get("/dashboard").param("year", "2026"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Exercice 2026")))
            .andExpect(content().string(containsString("Dernières factures")));
        mockMvc.perform(get("/dashboard").param("year", "2026").header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Financial year 2026")))
            .andExpect(content().string(containsString("Latest invoices")))
            .andExpect(content().string(containsString("Total billed in 2026: <strong")));
    }

    @Test
    @WithMockUser(authorities = "VIEW_INVOICES")
    void pageNeedsTheDashboardRight() throws Exception {
        mockMvc.perform(get("/turnover")).andExpect(status().isForbidden());
    }
}
