package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceRepository;
import com.swiftlabs.invoicing.invoice.InvoiceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renders every page with real data, as the seeded admin (all privileges). The principal is loaded after @BeforeEach. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    private Client client;
    private Invoice draft;
    private Invoice issued;

    @BeforeEach
    void data() {
        // The seeded admin has a temporary password: mark it as chosen, or every page redirects to /account/password
        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.changePassword(admin.getPassword(), java.time.LocalDateTime.now());
        client = new Client();
        client.setType(ClientType.PROFESSIONNEL);
        client.setName("SARL Atlas");
        client.setNif("000016001234567");
        client.setCity("Oran");
        client = clientRepository.save(client);
        draft = invoiceService.saveDraft(form(null, "Audit", "2.000", "15000.00"));
        issued = invoiceService.issue(invoiceService.saveDraft(form(null, "Développement", "1.000", "50000.00")).getId());
    }

    private InvoiceForm form(Long id, String description, String quantity, String unitPrice) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setUnit("jour");
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(unitPrice));
        InvoiceForm form = new InvoiceForm();
        form.setId(id);
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.now());
        form.setLines(List.of(line));
        return form;
    }

    private void assertPage(String url, String expectedText) throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(expectedText)))
            .andExpect(content().string(not(containsString("Une erreur est survenue"))));
    }

    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void businessPagesRender() throws Exception {
        mockMvc.perform(get("/")).andExpect(redirectedUrlPattern("/dashboard*"));
        assertPage("/dashboard", "Tableau de bord");
        assertPage("/clients", "SARL Atlas");
        assertPage("/clients/new", "Nouveau client");
        assertPage("/clients/" + client.getId() + "/edit", "value=\"SARL Atlas\"");
        assertPage("/invoices", issued.getInvoiceNumber());
        assertPage("/invoices?status=DRAFT&year=" + LocalDate.now().getYear(), "Brouillon");
        assertPage("/invoices/new", "name=\"lines[0].description\"");
        assertPage("/invoices/" + draft.getId() + "/edit", "value=\"Audit\"");
        assertPage("/invoices/" + issued.getId(), "Facture " + issued.getInvoiceNumber());
        assertPage("/invoices/lookup?number=" + issued.getInvoiceNumber(), "Facture authentique");
        assertPage("/turnover", "Modifier le plafond");
        assertPage("/settings/issuer", "Plafond annuel (DA)");
    }

    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void accessPagesRender() throws Exception {
        assertPage("/users", "admin");
        assertPage("/users/new", "Nouvel utilisateur");
        assertPage("/users/1/edit", "Modifier l&#39;utilisateur");
        assertPage("/profiles", "Administrateur");
        assertPage("/profiles/new", "name=\"privilegeIds\"");
        assertPage("/profiles/1/edit", "checked=\"checked\"");
        assertPage("/privileges", "MANAGE_PROFILES");
        assertPage("/privileges/new", "Nouveau privilège");
        assertPage("/privileges/1/edit", "readonly");
        assertPage("/access-denied", "Accès refusé");
    }

    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void navigationReflectsTheLoggedInUser() throws Exception {
        mockMvc.perform(get("/clients"))
            .andExpect(content().string(containsString(">admin<")))
            .andExpect(content().string(containsString(">Administrateur<")))
            .andExpect(content().string(containsString("href=\"/users\"")))
            .andExpect(content().string(containsString("nav-link active")));
    }

    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void pdfIsGenerated() throws Exception {
        byte[] pdf = mockMvc.perform(get("/invoices/" + issued.getId() + "/pdf"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void invoiceFormSubmitsItsLines() throws Exception {
        long before = invoiceRepository.count();
        mockMvc.perform(post("/invoices/save").with(csrf())
                .param("clientId", client.getId().toString())
                .param("issueDate", "2026-03-15")
                .param("paymentMethod", "BANK_TRANSFER")
                .param("lines[0].description", "Conseil").param("lines[0].unit", "heure")
                .param("lines[0].quantity", "3").param("lines[0].unitPrice", "2000.00")
                .param("lines[1].description", "Déplacement").param("lines[1].unit", "forfait")
                .param("lines[1].quantity", "1").param("lines[1].unitPrice", "500.00"))
            .andExpect(redirectedUrlPattern("/invoices/*"));

        assertThat(invoiceRepository.count()).isEqualTo(before + 1);
        Invoice saved = invoiceService.findRecent().get(0);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("6500.00");
        assertThat(saved.getIssueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void publicPagesRender() throws Exception {
        assertPage("/login", "Se connecter");
        assertPage("/forgot-password", "Envoyer le lien");
        assertPage("/invoices/lookup", "Vérifier une facture");
        mockMvc.perform(get("/fonts/material-symbols-outlined.woff2")).andExpect(status().isOk());
        mockMvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void unknownPageShowsTheErrorPage() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
            .andExpect(status().isNotFound());
    }
}
