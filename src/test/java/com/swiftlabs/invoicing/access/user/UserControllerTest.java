package com.swiftlabs.invoicing.access.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
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
@WithMockUser(authorities = "MANAGE_USERS")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAndEditFormsRender() throws Exception {
        mockMvc.perform(get("/users/new"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nouvel utilisateur")));
        mockMvc.perform(get("/users/1/edit"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Modifier l&#39;utilisateur")));
    }

    @Test
    void createWithoutProfileShowsErrorAndKeepsInput() throws Exception {
        MvcResult result = mockMvc.perform(post("/users/save").with(csrf())
                .param("username", "noprof").param("email", "noprof@example.test").param("profileId", ""))
            .andExpect(redirectedUrl("/users/new"))
            .andExpect(flash().attribute("error", "Un profil doit être attribué à l'utilisateur."))
            .andReturn();

        mockMvc.perform(get("/users/new").flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Un profil doit être attribué")))
            .andExpect(content().string(containsString("value=\"noprof\"")));
    }

    @Test
    void failedUpdateReturnsToEditForm() throws Exception {
        mockMvc.perform(post("/users/save").with(csrf())
                .param("id", "1").param("username", "admin").param("email", "admin@swiftinvoicing.local")
                .param("enabled", "true").param("profileId", ""))
            .andExpect(redirectedUrl("/users/1/edit"))
            .andExpect(flash().attributeExists("error"));
    }
}
