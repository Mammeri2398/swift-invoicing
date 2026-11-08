package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real login form, then the code page, for a user who has two-factor login turned on. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TwoFactorLoginTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TwoFactorService twoFactorService;

    private User user;
    private String secret;
    private List<String> recoveryCodes;

    @BeforeEach
    void userWithTwoFactor() {
        user = new User();
        user.setUsername("karim");
        user.setEmail("karim@example.test");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user = userRepository.save(user);
        accessService.grant(user, CompanySchemas.FIRST_ID, profileRepository.findByName("Administrateur"));
        secret = Totp.newSecret();
        recoveryCodes = twoFactorService.enable(user.getId(), secret, codeAt(Instant.now().minusSeconds(Totp.STEP_SECONDS)));
    }

    private String codeAt(Instant instant) {
        return Totp.code(Totp.base32Decode(secret), Totp.step(instant));
    }

    private MockHttpSession loginWithPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf())
                .param("username", "karim").param("password", PASSWORD))
            .andExpect(redirectedUrl("/login/verify"));
        return session;
    }

    private void verify(MockHttpSession session, String code, String expectedRedirect) throws Exception {
        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", code))
            .andExpect(redirectedUrl(expectedRedirect));
    }

    @Test
    void thePasswordAloneGivesAccessToNothing() throws Exception {
        MockHttpSession session = loginWithPassword();
        mockMvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/login/verify"));
        mockMvc.perform(get("/invoices").session(session)).andExpect(redirectedUrl("/login/verify"));
        mockMvc.perform(get("/account/password").session(session)).andExpect(redirectedUrl("/login/verify"));
        mockMvc.perform(get("/login/verify").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Vérification en deux étapes")));
    }

    @Test
    void aProfileChangedDuringTheCodeStepGivesNoAccessWithoutTheCode() throws Exception {
        MockHttpSession session = loginWithPassword();
        // An administrator changes the user's profile while the code page is open
        Profile reader = new Profile();
        reader.setName("Lecteur 2FA");
        accessService.grant(user, CompanySchemas.FIRST_ID, profileRepository.save(reader));
        mockMvc.perform(get("/login/verify").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/login/verify"));
        mockMvc.perform(get("/invoices").session(session)).andExpect(redirectedUrl("/login/verify"));
    }

    @Test
    void theRightCodeCompletesTheLogin() throws Exception {
        MockHttpSession session = loginWithPassword();
        String sessionIdBefore = session.getId();
        verify(session, codeAt(Instant.now()), "/");
        mockMvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());
        // New session id once the second factor is passed
        assertThat(session.getId()).isNotEqualTo(sessionIdBefore);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void theCodePageIsOnlyForAPendingLogin() throws Exception {
        mockMvc.perform(get("/login/verify")).andExpect(status().is3xxRedirection());
        MockHttpSession session = loginWithPassword();
        verify(session, codeAt(Instant.now()), "/");
        mockMvc.perform(get("/login/verify").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void wrongCodesLockTheAccountLikeWrongPasswords() throws Exception {
        MockHttpSession session = loginWithPassword();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", "000000"))
                .andExpect(redirectedUrl("/login/verify"))
                .andExpect(flash().attribute("error", "Code incorrect."));
            // Typing the password again does not give new tries
            session = loginWithPassword();
        }
        verify(session, "000000", "/login?error");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLockedUntil()).isNotNull();
        mockMvc.perform(post("/perform-login").with(csrf()).param("username", "karim").param("password", PASSWORD))
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void aRecoveryCodeWorksOnceAndWarns() throws Exception {
        MockHttpSession session = loginWithPassword();
        verify(session, recoveryCodes.get(0).toUpperCase(), "/");
        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(content().string(containsString("Il vous en reste 9")));
        assertThat(twoFactorService.remainingRecoveryCodes(user.getId())).isEqualTo(9);

        MockHttpSession again = loginWithPassword();
        verify(again, recoveryCodes.get(0), "/login/verify");
    }
}
