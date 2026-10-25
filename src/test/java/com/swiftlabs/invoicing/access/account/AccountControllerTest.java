package com.swiftlabs.invoicing.access.account;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Mon compte", through a real login (the header reads the session's principal). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private MockHttpSession session;

    @BeforeEach
    void loggedIn() throws Exception {
        user = new User();
        user.setUsername("kbenali");
        user.setEmail("karim@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setProfile(profileRepository.findByName("Administrateur"));
        user = userRepository.save(user);
        session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "kbenali").param("password", "Secret123"))
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void headerShowsTheUsernameUntilAFirstNameIsSetAndLinksToTheAccount() throws Exception {
        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(content().string(containsString("href=\"/account\"")))
            .andExpect(content().string(containsString(">kbenali</span>")))
            .andExpect(content().string(not(containsString("title=\"Double authentification\""))));
    }

    @Test
    void personalInformationIsSavedAndTheHeaderShowsTheFirstName() throws Exception {
        mockMvc.perform(post("/account/save").session(session).with(csrf())
                .param("firstName", "  Karim ").param("lastName", "Benali").param("email", "k.benali@example.test"))
            .andExpect(redirectedUrl("/account"))
            .andExpect(flash().attribute("success", "Informations personnelles enregistrées."));

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getFirstName()).isEqualTo("Karim");
        assertThat(saved.getLastName()).isEqualTo("Benali");
        assertThat(saved.getEmail()).isEqualTo("k.benali@example.test");
        mockMvc.perform(get("/account").session(session))
            .andExpect(content().string(containsString(">Karim</span>")))
            .andExpect(content().string(containsString("Karim Benali")))
            .andExpect(content().string(containsString("value=\"kbenali\"")));
    }

    @Test
    void anEmailUsedByAnotherAccountIsRefusedOnTheForm() throws Exception {
        User other = new User();
        other.setUsername("other");
        other.setEmail("taken@example.test");
        other.setPassword(passwordEncoder.encode("Secret123"));
        other.setProfile(profileRepository.findByName("Administrateur"));
        userRepository.save(other);

        mockMvc.perform(post("/account/save").session(session).with(csrf())
                .param("firstName", "Karim").param("email", "taken@example.test"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("account", "email"))
            .andExpect(content().string(containsString("Cet email est déjà utilisé.")));
        mockMvc.perform(post("/account/save").session(session).with(csrf()).param("email", "pas-un-email"))
            .andExpect(model().attributeHasFieldErrorCode("account", "email", "Email"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo("karim@example.test");
    }

    @Test
    void thePasswordIsChangedFromTheAccountPage() throws Exception {
        mockMvc.perform(get("/account").session(session))
            .andExpect(content().string(containsString("Changer le mot de passe")))
            .andExpect(content().string(containsString("action=\"/account/password\"")));
        mockMvc.perform(post("/account/password").session(session).with(csrf())
                .param("currentPassword", "Wrong123").param("newPassword", "Better456").param("confirmPassword", "Better456"))
            .andExpect(redirectedUrl("/account?password=true"))
            .andExpect(flash().attribute("error", "Le mot de passe actuel est incorrect."));
        // The form opens again after an error
        mockMvc.perform(get("/account?password=true").session(session))
            .andExpect(content().string(containsString("<details class=\"group pb-4\" open=\"open\">")));

        mockMvc.perform(post("/account/password").session(session).with(csrf())
                .param("currentPassword", "Secret123").param("newPassword", "Better456").param("confirmPassword", "Better456"))
            .andExpect(redirectedUrl("/account"));
        assertThat(passwordEncoder.matches("Better456", userRepository.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void theOldPasswordUrlLeadsToTheAccountPage() throws Exception {
        mockMvc.perform(get("/account/password").session(session)).andExpect(redirectedUrl("/account?password=true"));
    }

    @Test
    void twoFactorStatusLinksToItsSettings() throws Exception {
        mockMvc.perform(get("/account").session(session))
            .andExpect(content().string(containsString("Double authentification")))
            .andExpect(content().string(containsString("href=\"/account/two-factor\"")))
            .andExpect(content().string(containsString("Désactivée")));
    }
}
