package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Bean Validation on the forms, as the user sees it: messages next to the fields, input kept. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_CLIENTS", "CREATE_CLIENT", "VIEW_INVOICES", "CREATE_INVOICE"})
class FormValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void invalidClientIsShownAgainWithMessagesNextToTheFields() throws Exception {
        mockMvc.perform(post("/clients/save").with(csrf())
                .param("type", "PROFESSIONNEL")
                .param("name", "  ")
                .param("nif", "12AB")
                .param("email", "not-an-email")
                .param("city", "x".repeat(256)))
            .andExpect(status().isOk())
            .andExpect(view().name("client/form"))
            .andExpect(model().attributeHasFieldErrorCode("client", "name", "NotBlank"))
            .andExpect(model().attributeHasFieldErrorCode("client", "nif", "Pattern"))
            .andExpect(model().attributeHasFieldErrorCode("client", "email", "Email"))
            .andExpect(model().attributeHasFieldErrorCode("client", "city", "Size"))
            .andExpect(content().string(containsString("Ce champ est obligatoire.")))
            .andExpect(content().string(containsString("Le NIF ne contient que des chiffres.")))
            .andExpect(content().string(containsString("Adresse email invalide.")))
            // {1} of @Size is "max": the arguments are resolved, not printed raw
            .andExpect(content().string(containsString("Ce texte est trop long (255 caractères au maximum).")))
            .andExpect(content().string(containsString("Corrigez les champs signalés.")))
            .andExpect(content().string(containsString("value=\"not-an-email\"")));
        assertThat(clientRepository.count()).isZero();
    }

    @Test
    void professionalClientNeedsANifReportedOnTheNifField() throws Exception {
        mockMvc.perform(post("/clients/save").with(csrf()).param("type", "PROFESSIONNEL").param("name", "SARL Sans NIF"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("client", "nif", "NifRequiredForProfessional"))
            .andExpect(content().string(containsString("Le NIF est obligatoire pour un client professionnel.")));

        mockMvc.perform(post("/clients/save").with(csrf()).param("type", "PARTICULIER").param("name", "Particulier Sans NIF"))
            .andExpect(redirectedUrl("/clients"));
    }

    @Test
    void textIsTrimmedAndBlankOptionalFieldsAreStoredAsNull() throws Exception {
        mockMvc.perform(post("/clients/save").with(csrf())
                .param("type", "PARTICULIER").param("name", "  Karim Benali  ").param("nif", "   ").param("email", ""))
            .andExpect(redirectedUrl("/clients"));

        Client saved = clientRepository.findAll().get(0);
        assertThat(saved.getName()).isEqualTo("Karim Benali");
        assertThat(saved.getNif()).isNull();
        assertThat(saved.getEmail()).isNull();
    }

    private Client client() {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Facture");
        return clientRepository.save(client);
    }

    @Test
    void invalidInvoiceShowsFieldAndLineErrors() throws Exception {
        Client client = client();
        mockMvc.perform(post("/invoices/save").with(csrf())
                .param("clientId", client.getId().toString())
                .param("issueDate", "2026-05-10")
                .param("dueDate", "2026-05-01")
                .param("lines[0].description", "Conseil").param("lines[0].quantity", "0").param("lines[0].unitPrice", "-5")
                .param("lines[1].description", "").param("lines[1].quantity", "1").param("lines[1].unitPrice", "abc"))
            .andExpect(status().isOk())
            .andExpect(view().name("invoice/form"))
            .andExpect(model().attributeHasFieldErrorCode("invoice", "dueDate", "DueDateNotBeforeIssueDate"))
            .andExpect(model().attributeHasFieldErrorCode("invoice", "lines[0].quantity", "DecimalMin"))
            .andExpect(model().attributeHasFieldErrorCode("invoice", "lines[0].unitPrice", "DecimalMin"))
            .andExpect(model().attributeHasFieldErrorCode("invoice", "lines[1].description", "NotBlank"))
            .andExpect(model().attributeHasFieldErrorCode("invoice", "lines[1].unitPrice", "typeMismatch"))
            .andExpect(content().string(containsString("L&#39;échéance ne peut pas précéder la date d&#39;émission.")))
            .andExpect(content().string(containsString("La quantité doit être supérieure à 0.")))
            .andExpect(content().string(containsString("Le prix ne peut pas être négatif.")))
            .andExpect(content().string(containsString("Nombre invalide.")))
            // Both lines are kept, with what was typed
            .andExpect(content().string(containsString("value=\"Conseil\"")))
            .andExpect(content().string(containsString("name=\"lines[1].description\"")));
    }

    @Test
    void invoiceNeedsAClientAndALine() throws Exception {
        mockMvc.perform(post("/invoices/save").with(csrf()).param("issueDate", "2026-05-10"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("invoice", "clientId", "NotNull"))
            .andExpect(model().attributeHasFieldErrorCode("invoice", "lines", "NotEmpty"))
            .andExpect(content().string(containsString("Choisissez un client.")))
            .andExpect(content().string(containsString("Ajoutez au moins une ligne.")))
            // An empty line is offered again so the user can fill it in
            .andExpect(content().string(containsString("name=\"lines[0].description\"")));
    }

    @Test
    void invalidListFilterInTheUrlIsIgnored() throws Exception {
        mockMvc.perform(get("/invoices").param("status", "NOT_A_STATUS").param("year", "abc"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Une erreur est survenue"))));
    }

    @Test
    void pageLinksKeepTheFilters() throws Exception {
        Client client = client();
        for (int i = 1; i <= 21; i++) {
            mockMvc.perform(post("/invoices/save").with(csrf())
                .param("clientId", client.getId().toString()).param("issueDate", "2026-03-01")
                .param("lines[0].description", "Ligne " + i).param("lines[0].quantity", "1").param("lines[0].unitPrice", "10"));
        }
        mockMvc.perform(get("/invoices").param("status", "DRAFT").param("search", "facture"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Page 1 sur 2")))
            .andExpect(content().string(containsString("href=\"/invoices?status=DRAFT&amp;search=facture&amp;page=1\"")));
    }
}
