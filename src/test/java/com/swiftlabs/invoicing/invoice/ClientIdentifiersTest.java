package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** RC, NIS and AI of professional clients: on the client form, required at issue for a company, frozen on the invoice. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_CLIENTS", "CREATE_CLIENT", "VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE"})
class ClientIdentifiersTest {

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

    private void companyIssuer() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setLegalForm("SARL");
        issuer.setShareCapital(new BigDecimal("100000"));
        issuer.setRcNumber("16/00-1234567B21");
        issuer.setTaxArticle("16012345678");
    }

    private Client professional(String rc, String ai) {
        Client client = new Client();
        client.setType(ClientType.PROFESSIONNEL);
        client.setName("SARL Bâtiment");
        client.setNif("987654321012345");
        client.setRcNumber(rc);
        client.setTaxArticle(ai);
        return clientRepository.save(client);
    }

    private Invoice draftFor(Client client) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Travaux");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("1000.00"));
        line.setVatRate(new BigDecimal("19"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 6, 15));
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    private String pdfText(Invoice invoice) throws Exception {
        try (PDDocument pdf = PDDocument.load(pdfService.render(invoiceService.findWithLines(invoice.getId())))) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    @Test
    void theClientFormKeepsTheIdentifiersOfAProfessionalOnly() throws Exception {
        mockMvc.perform(post("/clients/save").with(csrf())
                .param("type", "PROFESSIONNEL").param("name", "SARL Bâtiment").param("nif", "987654321012345")
                .param("rcNumber", "16/00-7654321B19").param("nis", "123456789012345").param("taxArticle", "16098765432"))
            .andExpect(redirectedUrl("/clients"));
        Client saved = clientRepository.findAll().stream().filter(c -> "SARL Bâtiment".equals(c.getName())).findFirst().orElseThrow();
        assertThat(saved.getRcNumber()).isEqualTo("16/00-7654321B19");
        assertThat(saved.getNis()).isEqualTo("123456789012345");
        assertThat(saved.getTaxArticle()).isEqualTo("16098765432");

        mockMvc.perform(post("/clients/save").with(csrf())
                .param("id", saved.getId().toString()).param("type", "PARTICULIER").param("name", "Karim Benali")
                .param("rcNumber", "16/00-7654321B19").param("taxArticle", "16098765432"))
            .andExpect(redirectedUrl("/clients"));
        assertThat(clientRepository.findById(saved.getId()).orElseThrow().getRcNumber()).isNull();

        mockMvc.perform(post("/clients/save").with(csrf())
                .param("type", "PROFESSIONNEL").param("name", "Autre").param("nif", "1").param("nis", "ABC"))
            .andExpect(model().attributeHasFieldErrorCode("client", "nis", "Pattern"));
    }

    @Test
    void aCompanyCannotInvoiceABusinessWithoutItsRcAndAi() {
        companyIssuer();
        Client client = professional(null, null);
        Invoice draft = draftFor(client);
        assertThatThrownBy(() -> invoiceService.issue(draft.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Complétez la fiche du client avant d'émettre la facture : "
                + "le registre du commerce (RC) et l'article d'imposition (AI) manquent.");

        client.setRcNumber("16/00-7654321B19");
        assertThatThrownBy(() -> invoiceService.issue(draft.getId()))
            .hasMessageEndingWith("l'article d'imposition (AI) manque.");
    }

    @Test
    void anAutoEntrepreneurIsNotBlockedByThem() {
        Invoice issued = invoiceService.issue(draftFor(professional(null, null)).getId());
        assertThat(issued.getInvoiceNumber()).isNotNull();
    }

    @Test
    void theIdentifiersAreFrozenOnTheIssuedInvoice() throws Exception {
        companyIssuer();
        Client client = professional("16/00-7654321B19", "16098765432");
        client.setNis("123456789012345");
        Invoice issued = invoiceService.issue(draftFor(client).getId());
        assertThat(issued.getClientRcNumber()).isEqualTo("16/00-7654321B19");

        // Later changes to the client do not reach the issued invoice
        client.setRcNumber("16/00-0000000X00");
        assertThat(pdfText(issued)).contains("RC : 16/00-7654321B19").contains("NIS : 123456789012345")
            .contains("AI : 16098765432").doesNotContain("16/00-0000000X00");
        mockMvc.perform(get("/invoices/" + issued.getId()))
            .andExpect(content().string(containsString("RC 16/00-7654321B19")))
            .andExpect(content().string(containsString("AI 16098765432")));
    }
}
