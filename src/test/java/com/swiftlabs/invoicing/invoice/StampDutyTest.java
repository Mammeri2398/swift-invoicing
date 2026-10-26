package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Droit de timbre on invoices paid in cash: on top of the TTC, outside turnover and TVA. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE"})
class StampDutyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService pdfService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void client() {
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Espèces");
        client = clientRepository.save(client);
    }

    private Invoice draft(PaymentMethod paymentMethod, String price, String vatRate) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setVatRate(new BigDecimal(vatRate));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 5, 4));
        form.setPaymentMethod(paymentMethod);
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    private void companyIssuer() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setLegalForm("SARL");
        issuer.setShareCapital(new BigDecimal("100000"));
        issuer.setRcNumber("16/00-1");
        issuer.setTaxArticle("1601");
    }

    @Test
    void onlyCashPaymentsCarryTheDuty() {
        Invoice cash = draft(PaymentMethod.CASH, "1000.00", "0");
        assertThat(cash.getStampDutyAmount()).isEqualByComparingTo("10.00");
        assertThat(cash.getTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(cash.getAmountDue()).isEqualByComparingTo("1010.00");

        assertThat(draft(PaymentMethod.BANK_TRANSFER, "1000.00", "0").getStampDutyAmount()).isEqualByComparingTo("0");
        assertThat(draft(null, "1000.00", "0").getStampDutyAmount()).isEqualByComparingTo("0");
    }

    @Test
    void theDutyIsOnTheTtcAndStaysOutOfTurnoverAndTva() {
        companyIssuer();
        Invoice issued = invoiceService.issue(draft(PaymentMethod.CASH, "1000.00", "19").getId());
        // 1 190,00 TTC x 1 % = 11.90, rounded up to 12
        assertThat(issued.getStampDutyAmount()).isEqualByComparingTo("12.00");
        assertThat(issued.getVatAmount()).isEqualByComparingTo("190.00");
        assertThat(issued.getAmountDue()).isEqualByComparingTo("1202.00");
        assertThat(invoiceService.totalBilled(2026)).isEqualByComparingTo("1000.00");
        // What the client still owes includes the duty
        assertThat(invoiceService.totalUnpaid()).isEqualByComparingTo("1202.00");
    }

    @Test
    void aCreditNoteHasNoDutyAndStillCancelsTheWholeInvoice() {
        Invoice invoice = invoiceService.issue(draft(PaymentMethod.CASH, "1000.00", "0").getId());
        Invoice creditNote = invoiceService.issue(invoiceService.createCreditNote(invoice.getId()).getId());
        assertThat(creditNote.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(creditNote.getStampDutyAmount()).isEqualByComparingTo("0");
        assertThat(invoiceService.remainingToCredit(invoice)).isEqualByComparingTo("0");
    }

    @Test
    void thePdfShowsTheDutyAndTheNetToPay() throws Exception {
        Invoice issued = invoiceService.issue(draft(PaymentMethod.CASH, "1000.00", "0").getId());
        String text;
        try (PDDocument pdf = PDDocument.load(pdfService.render(invoiceService.findWithLines(issued.getId())))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        }
        assertThat(text).contains("Sous-total 1 000,00 DA").contains("Droit de timbre (espèces) 10,00 DA").contains("Net à payer 1 010,00 DA")
            .contains("à la somme de : mille dix").contains("dinars algériens.");
        mockMvc.perform(get("/invoices/" + issued.getId()))
            .andExpect(content().string(containsString("Droit de timbre")))
            .andExpect(content().string(containsString("Net à payer :")))
            .andExpect(content().string(containsString("1 010,00 DA")));
    }

    @Test
    void theFormReceivesTheRulesForItsPreview() throws Exception {
        mockMvc.perform(get("/invoices/new"))
            .andExpect(content().string(containsString("data-stamp-duty=\"{&quot;minimum&quot;:5,&quot;maximum&quot;:null,"
                + "&quot;brackets&quot;:[{&quot;upTo&quot;:30000,&quot;rate&quot;:1},{&quot;upTo&quot;:100000,&quot;rate&quot;:1.5},"
                + "{&quot;upTo&quot;:null,&quot;rate&quot;:2}]}\"")));
    }
}
