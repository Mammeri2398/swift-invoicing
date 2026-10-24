package com.swiftlabs.invoicing.access.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    /** The seeded admin has a temporary password: mark it as chosen so pages are not redirected. */
    @BeforeEach
    void adminHasChosenPassword() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.changePassword(admin.getPassword(), LocalDateTime.now());
    }

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
        mockMvc.perform(post("/users/save").with(csrf())
                .param("username", "noprof").param("email", "noprof@example.test").param("profileId", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("access/user/form"))
            .andExpect(model().attributeHasFieldErrorCode("user", "profileId", "NotNull"))
            .andExpect(content().string(containsString("Choisissez un profil.")))
            .andExpect(content().string(containsString("value=\"noprof\"")));
    }

    @Test
    void failedUpdateReturnsToEditForm() throws Exception {
        mockMvc.perform(post("/users/save").with(csrf())
                .param("id", "1").param("username", "admin").param("email", "admin@swiftinvoicing.local")
                .param("enabled", "true").param("profileId", ""))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("user", "profileId"))
            .andExpect(content().string(containsString("Modifier l&#39;utilisateur")));
    }

    @Test
    void createdUserTemporaryPasswordIsShownOnce() throws Exception {
        MvcResult result = mockMvc.perform(post("/users/save").with(csrf())
                .param("username", "fresh").param("email", "fresh@example.test").param("profileId", "1"))
            .andExpect(redirectedUrl("/users"))
            .andExpect(flash().attributeExists("temporaryPassword"))
            .andReturn();
        String temporary = (String) result.getFlashMap().get("temporaryPassword");

        mockMvc.perform(get("/users").flashAttrs(result.getFlashMap()))
            .andExpect(content().string(containsString(temporary)))
            .andExpect(content().string(containsString("Mot de passe à changer")));
        mockMvc.perform(get("/users"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(temporary))));
    }

    @Test
    void cannotDeleteOwnAccount() throws Exception {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        mockMvc.perform(post("/users/" + adminId + "/delete").with(csrf()))
            .andExpect(redirectedUrl("/users"))
            .andExpect(flash().attribute("error", "Vous ne pouvez pas supprimer votre propre compte."));
        org.assertj.core.api.Assertions.assertThat(userRepository.findById(adminId)).isPresent();
    }
}
