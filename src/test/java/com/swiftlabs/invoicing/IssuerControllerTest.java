package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.domain.Issuer;
import com.swiftlabs.invoicing.repository.IssuerRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssuerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IssuerRepository issuerRepository;

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void pageRendersStoredIssuerInAPostFormWithCsrfToken() throws Exception {
        mockMvc.perform(get("/settings/issuer"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("action=\"/settings/issuer/save\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("name=\"fullName\"")))
            .andExpect(content().string(containsString("value=\"Nom Pr&eacute;nom\"")))
            .andExpect(content().string(containsString("Enregistrer les paramètres")))
            .andExpect(content().string(not(containsString("Amine Mansouri"))));
    }

    @Test
    @WithMockUser(authorities = "VIEW_ISSUER")
    void pageIsReadOnlyWithoutEditPrivilege() throws Exception {
        mockMvc.perform(get("/settings/issuer"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lecture seule")))
            .andExpect(content().string(not(containsString("Enregistrer les paramètres"))));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void saveUpdatesIssuerAndKeepsLogo() throws Exception {
        Issuer before = issuerRepository.findById(1L).orElseThrow();
        before.setLogo("logo.png");
        issuerRepository.saveAndFlush(before);

        mockMvc.perform(post("/settings/issuer/save").with(csrf())
                .param("id", "1")
                .param("fullName", "Karim Benali")
                .param("aeCardNumber", "16-2024-00001")
                .param("nif", "000016001234567")
                .param("city", "Oran"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings/issuer"))
            .andExpect(flash().attributeExists("success"));

        Issuer after = issuerRepository.findById(1L).orElseThrow();
        assertThat(after.getFullName()).isEqualTo("Karim Benali");
        assertThat(after.getCity()).isEqualTo("Oran");
        assertThat(after.getLogo()).isEqualTo("logo.png");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void saveRejectsMissingNif() throws Exception {
        mockMvc.perform(post("/settings/issuer/save").with(csrf())
                .param("id", "1")
                .param("fullName", "Karim Benali")
                .param("aeCardNumber", "16-2024-00001")
                .param("nif", "  "))
            .andExpect(redirectedUrl("/settings/issuer"))
            .andExpect(flash().attribute("error", "Le NIF est obligatoire."))
            .andExpect(flash().attributeExists("issuer"));

        assertThat(issuerRepository.findById(1L).orElseThrow().getNif()).isEqualTo("123456789012345");
    }
}
