package com.swiftlabs.invoicing.access.twofactor;

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

import java.time.Instant;

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

/** As in production: administrators must set up two-factor login; an administrator can reset it for someone else. */
@SpringBootTest(properties = "app.security.two-factor.required-for=MANAGE_USERS,MANAGE_PROFILES,MANAGE_PRIVILEGES")
@AutoConfigureMockMvc
@Transactional
class TwoFactorRequiredTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TwoFactorService twoFactorService;

    private User admin;

    @BeforeEach
    void admin() {
        admin = save("nadia", "Administrateur");
    }

    private User save(String username, String profile) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setProfile(profileRepository.findByName(profile));
        return userRepository.save(user);
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", username).param("password", "Secret123"));
        return session;
    }

    @Test
    void anAdministratorWithoutItIsSentToTheSetupPage() throws Exception {
        MockHttpSession session = login("nadia");
        mockMvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/account/two-factor"));
        mockMvc.perform(get("/users").session(session)).andExpect(redirectedUrl("/account/two-factor"));
        mockMvc.perform(get("/account/two-factor").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("la double authentification est obligatoire")));
    }

    @Test
    void onceSetUpTheAdministratorWorksNormallyAndCannotTurnItOff() throws Exception {
        MockHttpSession session = login("nadia");
        mockMvc.perform(get("/account/two-factor").session(session));
        String secret = (String) session.getAttribute(TwoFactorController.class.getName() + ".SECRET");
        String code = Totp.code(Totp.base32Decode(secret), Totp.step(Instant.now()));
        mockMvc.perform(post("/account/two-factor/confirm").session(session).with(csrf()).param("code", code))
            .andExpect(redirectedUrl("/account/two-factor"));

        mockMvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/account/two-factor").session(session))
            .andExpect(content().string(not(containsString("Désactiver la double authentification ?"))));
        String next = Totp.code(Totp.base32Decode(secret), Totp.step(Instant.now()) + 1);
        mockMvc.perform(post("/account/two-factor/disable").session(session).with(csrf()).param("code", next))
            .andExpect(flash().attribute("error", "La double authentification est obligatoire pour votre profil."));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isTotpEnabled()).isTrue();
    }

    @Test
    void anAdministratorCanResetItForALostPhone() throws Exception {
        String secret = Totp.newSecret();
        twoFactorService.enable(admin.getId(), secret, Totp.code(Totp.base32Decode(secret), Totp.step(Instant.now())));
        User colleague = save("yacine", "Administrateur");
        String other = Totp.newSecret();
        twoFactorService.enable(colleague.getId(), other, Totp.code(Totp.base32Decode(other), Totp.step(Instant.now())));

        MockHttpSession session = login("nadia");
        String next = Totp.code(Totp.base32Decode(secret), Totp.step(Instant.now()) + 1);
        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", next)).andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/users").session(session))
            .andExpect(content().string(containsString("/users/" + colleague.getId() + "/reset-two-factor")));
        mockMvc.perform(post("/users/" + colleague.getId() + "/reset-two-factor").session(session).with(csrf()))
            .andExpect(redirectedUrl("/users"));

        assertThat(userRepository.findById(colleague.getId()).orElseThrow().isTotpEnabled()).isFalse();
        assertThat(twoFactorService.remainingRecoveryCodes(colleague.getId())).isZero();
        // Password only, then the setup is asked again since the profile requires it
        MockHttpSession colleagueSession = login("yacine");
        mockMvc.perform(get("/dashboard").session(colleagueSession)).andExpect(redirectedUrl("/account/two-factor"));
    }
}
