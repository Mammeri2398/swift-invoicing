package com.swiftlabs.invoicing.client;

import com.swiftlabs.invoicing.common.BusinessException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The client pages in the user's language; French stays the default. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_CLIENTS", "CREATE_CLIENT", "DELETE_CLIENT"})
class ClientPagesLanguageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientService clientService;

    private Client client(String name, ClientType type) {
        Client client = new Client();
        client.setType(type);
        client.setName(name);
        return clientRepository.save(client);
    }

    @Test
    void listIsInFrenchByDefaultAndInEnglishOnRequest() throws Exception {
        client("SARL Atlas", ClientType.PROFESSIONNEL);
        mockMvc.perform(get("/clients"))
            .andExpect(content().string(containsString("Registre")))
            .andExpect(content().string(containsString("1 client(s)")))
            .andExpect(content().string(containsString("NIF manquant")));
        mockMvc.perform(get("/clients").header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Directory")))
            .andExpect(content().string(containsString("1 client<")))
            .andExpect(content().string(containsString("Business")))
            .andExpect(content().string(containsString("NIF missing")));
    }

    @Test
    void formLabelsComeFromTheBundles() throws Exception {
        mockMvc.perform(get("/clients/new").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("15 or 20 digits")))
            .andExpect(content().string(containsString("data-label-professional=\"Company name\"")))
            .andExpect(content().string(containsString("data-label-private=\"Full name\"")));
        mockMvc.perform(get("/clients/new").header("Accept-Language", "ar"))
            .andExpect(content().string(containsString("15 أو 20 رقمًا")))
            .andExpect(content().string(containsString("data-label-private=\"الاسم الكامل\"")));
    }

    @Test
    void savedMessageAndErrorsAreTranslated() throws Exception {
        mockMvc.perform(post("/clients/save").with(csrf()).header("Accept-Language", "en")
                .param("type", "PARTICULIER").param("name", "Karim"))
            .andExpect(flash().attribute("success", "Client saved."));
        Client client = client("Karim 2", ClientType.PARTICULIER);
        mockMvc.perform(post("/clients/" + client.getId() + "/delete").with(csrf()).header("Accept-Language", "ar"))
            .andExpect(flash().attribute("success", "تم حذف الزبون."));
        assertThatThrownBy(() -> clientService.findById(-1L))
            .isInstanceOf(BusinessException.class).hasMessage("Client introuvable (id=-1).");
    }
}
