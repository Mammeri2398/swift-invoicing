package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

/** The quote pages, through a real login (no @WithMockUser, which would replace the session's user). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuotePagesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Invoice issuedQuote() {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Devis");
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Site vitrine");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("5000.00"));
        line.setVatRate(BigDecimal.ZERO);
        InvoiceForm form = new InvoiceForm();
        form.setKind(DocumentKind.QUOTE);
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 6, 1));
        form.setLines(List.of(line));
        return invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    /** A real login: the action buttons depend on the session's user (currentUser). */
    private MockHttpSession login() throws Exception {
        User user = new User();
        user.setUsername("devis-user");
        user.setEmail("devis-user@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setProfile(profileRepository.findByName("Administrateur"));
        userRepository.save(user);
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "devis-user").param("password", "Secret123"));
        return session;
    }

    /** The menu entry of the page: quotes are shown at /invoices/{id} too, but under "Devis". */
    private static final String QUOTES_ACTIVE = "href=\"/quotes\" class=\"nav-link active\"";
    private static final String INVOICES_ACTIVE = "href=\"/invoices\" class=\"nav-link active\"";

    @Test
    void thePagesLeadFromTheQuoteToItsInvoice() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(get("/invoices/new").session(session).param("kind", "QUOTE"))
            .andExpect(content().string(containsString("Nouveau devis")))
            .andExpect(content().string(containsString("name=\"kind\" value=\"QUOTE\"")))
            .andExpect(content().string(containsString("Valable jusqu&#39;au")))
            .andExpect(content().string(containsString(QUOTES_ACTIVE)))
            .andExpect(content().string(not(containsString(INVOICES_ACTIVE))));

        Invoice quote = issuedQuote();
        mockMvc.perform(get("/invoices/" + quote.getId()).session(session))
            .andExpect(content().string(containsString("Devis DV-2026-0001")))
            .andExpect(content().string(containsString("/accept")))
            .andExpect(content().string(containsString("Facturer")))
            .andExpect(content().string(not(containsString("Marquer payée"))))
            .andExpect(content().string(containsString(QUOTES_ACTIVE)))
            .andExpect(content().string(not(containsString(INVOICES_ACTIVE))));
        mockMvc.perform(get("/invoices/" + quote.getId() + "/email").session(session))
            .andExpect(content().string(containsString("value=\"Devis DV-2026-0001 - ")));

        MvcResult converted = mockMvc.perform(post("/invoices/" + quote.getId() + "/convert").session(session).with(csrf())).andReturn();
        assertThat(converted.getResponse().getRedirectedUrl()).endsWith("/edit");
        mockMvc.perform(get("/invoices/" + quote.getId()).session(session))
            .andExpect(content().string(containsString("Facturé : facture brouillon")))
            .andExpect(content().string(not(containsString(">Facturer<"))));
        Invoice invoice = invoiceService.invoiceOf(quote.getId());
        mockMvc.perform(get("/invoices/" + invoice.getId()).session(session))
            .andExpect(content().string(containsString("Établie à partir du")))
            .andExpect(content().string(containsString("DV-2026-0001")))
            .andExpect(content().string(containsString(INVOICES_ACTIVE)))
            .andExpect(content().string(not(containsString(QUOTES_ACTIVE))));
    }
}
