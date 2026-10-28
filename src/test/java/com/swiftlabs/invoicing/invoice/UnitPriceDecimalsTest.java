package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.catalog.CatalogItem;
import com.swiftlabs.invoicing.catalog.CatalogItemForm;
import com.swiftlabs.invoicing.catalog.CatalogService;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.AmountUtils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * A monthly fee billed by the day: 130 000 DA over 22 working days is a unit price of 5 909,090909 DA (up to 6
 * decimals), and 22 days come back to exactly 130 000,00 DA (line totals are rounded to the centime).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnitPriceDecimalsTest {

    private static final BigDecimal DAILY_RATE = new BigDecimal("5909.090909");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private ClientRepository clientRepository;

    private Invoice twentyTwoDays() {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client au forfait");
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation journalière");
        line.setUnit("jour");
        line.setQuantity(new BigDecimal("22"));
        line.setUnitPrice(DAILY_RATE);
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.now());
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    @Test
    void twentyTwoDaysAtTheDailyRateMakeTheMonthlyFee() throws Exception {
        Invoice invoice = invoiceService.issue(twentyTwoDays().getId());
        Invoice saved = invoiceService.findWithLines(invoice.getId());

        assertThat(saved.getLines().get(0).getUnitPrice()).isEqualByComparingTo(DAILY_RATE);
        assertThat(saved.getLines().get(0).getLineTotal()).isEqualByComparingTo("130000.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("130000.00");

        String text;
        try (PDDocument pdf = PDDocument.load(pdfService.render(saved))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        }
        assertThat(text).contains("5 909,090909").contains("130 000,00");
    }

    @Test
    void pricesShowTheirDecimalsFromTwoToSix() {
        assertThat(AmountUtils.formatPrice(DAILY_RATE)).isEqualTo("5 909,090909 DA");
        assertThat(AmountUtils.formatPrice(new BigDecimal("5000.000000"))).isEqualTo("5 000,00 DA");
        assertThat(AmountUtils.formatPrice(new BigDecimal("12.5"))).isEqualTo("12,50 DA");
        // As read back from the 6-decimal column: never "5000.000000" (nor "5E+3") in a form
        assertThat(AmountUtils.price(new BigDecimal("5000.000000")).toPlainString()).isEqualTo("5000.00");
        assertThat(AmountUtils.price(new BigDecimal("5909.090909")).toPlainString()).isEqualTo("5909.090909");
    }

    @Test
    @WithMockUser(authorities = {"MANAGE_CATALOG", "CREATE_INVOICE", "VIEW_INVOICES"})
    void theCatalogueTakesAPriceWithSixDecimalsButNotSeven() throws Exception {
        mockMvc.perform(post("/catalog/save").with(csrf())
                .param("name", "Jour de mission").param("unit", "jour").param("unitPrice", "5909.090909").param("vatRate", "0"))
            .andExpect(redirectedUrl("/catalog"));
        CatalogItem item = catalogService.findAll().stream().filter(i -> i.getName().equals("Jour de mission")).findFirst().orElseThrow();
        assertThat(item.getUnitPrice()).isEqualByComparingTo(DAILY_RATE);
        mockMvc.perform(get("/catalog")).andExpect(content().string(containsString("5 909,090909 DA")));
        // Picked when typing an invoice line: the form receives the full price
        mockMvc.perform(get("/invoices/new")).andExpect(content().string(containsString("data-price=\"5909.090909\"")));

        mockMvc.perform(post("/catalog/save").with(csrf())
                .param("name", "Trop précis").param("unitPrice", "1.1234567").param("vatRate", "0"))
            .andExpect(model().attributeHasFieldErrorCode("item", "unitPrice", "Digits"));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_INVOICES", "CREATE_INVOICE"})
    void theInvoicePageAndFormKeepTheDecimals() throws Exception {
        Invoice draft = twentyTwoDays();
        mockMvc.perform(get("/invoices/" + draft.getId())).andExpect(content().string(containsString("5 909,090909 DA")));
        mockMvc.perform(get("/invoices/" + draft.getId() + "/edit"))
            .andExpect(content().string(containsString("value=\"5909.090909\"")))
            .andExpect(content().string(containsString("step=\"any\"")));
    }
}
