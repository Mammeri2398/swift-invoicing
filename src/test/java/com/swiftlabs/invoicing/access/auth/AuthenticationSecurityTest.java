package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationSecurityTest {

    private static final String PASSWORD = "Secret123x";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    private User user(String username, Profile profile, boolean mustChangePassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setEnabled(true);
        if (mustChangePassword) {
            user.assignTemporaryPassword(passwordEncoder.encode(PASSWORD), LocalDateTime.now());
        } else {
            user.changePassword(passwordEncoder.encode(PASSWORD), LocalDateTime.now());
        }
        User saved = userRepository.save(user);
        accessService.grant(saved, CompanySchemas.FIRST_ID, profile);
        return saved;
    }

    private User admin(String username) {
        return user(username, profileRepository.findByName("Administrateur"), false);
    }

    /** A profile with only the given privilege. */
    private Profile profileWith(String name, String privilegeCode) {
        Profile profile = new Profile();
        profile.setName(name);
        profile.getPrivileges().add(privilegeRepository.findByCode(privilegeCode));
        return profileRepository.save(profile);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/perform-login").user(username).password(password))
            .andReturn().getRequest().getSession(false);
    }

    @Test
    void successfulLoginGoesToTheFirstAllowedSection() throws Exception {
        admin("boss");
        mockMvc.perform(formLogin("/perform-login").user("boss").password(PASSWORD))
            .andExpect(authenticated().withUsername("boss"))
            .andExpect(redirectedUrl("/"));

        MockHttpSession session = login("boss", PASSWORD);
        mockMvc.perform(get("/").session(session)).andExpect(redirectedUrl("/dashboard"));

        user("reader", profileWith("Lecture factures", "VIEW_INVOICES"), false);
        MockHttpSession readerSession = login("reader", PASSWORD);
        mockMvc.perform(get("/").session(readerSession)).andExpect(redirectedUrl("/invoices"));
        mockMvc.perform(get("/dashboard").session(readerSession)).andExpect(status().isForbidden());
    }

    @Test
    void failedLoginShowsAGenericMessage() throws Exception {
        admin("someone");
        mockMvc.perform(formLogin("/perform-login").user("someone").password("wrong"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(formLogin("/perform-login").user("nobody-here").password("wrong"))
            .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(get("/login").param("error", ""))
            .andExpect(content().string(containsString("Identifiants invalides. Après 5 échecs consécutifs")));
        // Before login, the page follows the browser's language
        mockMvc.perform(get("/login").param("error", "").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("Invalid username or password. After 5 failed attempts in a row")))
            .andExpect(content().string(containsString("Forgot your password?")))
            .andExpect(content().string(containsString("data-label-hide=\"Hide password\"")));
    }

    @Test
    void accountIsLockedAfterFiveFailuresAndUnlockedByAnAdminReset() throws Exception {
        User target = admin("victim");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(formLogin("/perform-login").user("victim").password("guess" + i)).andExpect(unauthenticated());
        }
        assertThat(userRepository.findById(target.getId()).orElseThrow().isLocked(LocalDateTime.now())).isTrue();

        // Even the right password is refused while locked
        mockMvc.perform(formLogin("/perform-login").user("victim").password(PASSWORD))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/login?error"));

        String temporary = userService.resetToTemporaryPassword(target.getId(), null);
        mockMvc.perform(formLogin("/perform-login").user("victim").password(temporary)).andExpect(authenticated());
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        User target = admin("forgetful");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(formLogin("/perform-login").user("forgetful").password("guess" + i));
        }
        mockMvc.perform(formLogin("/perform-login").user("forgetful").password(PASSWORD)).andExpect(authenticated());

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeAnythingElse() throws Exception {
        user("rookie", profileRepository.findByName("Administrateur"), true);
        MockHttpSession session = login("rookie", PASSWORD);

        mockMvc.perform(get("/clients").session(session)).andExpect(redirectedUrl("/account/password"));
        mockMvc.perform(get("/account/password").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("mot de passe temporaire")));

        mockMvc.perform(post("/account/password").session(session).with(csrf())
                .param("currentPassword", PASSWORD)
                .param("newPassword", "MyOwnPass789")
                .param("confirmPassword", "MyOwnPass789"))
            .andExpect(redirectedUrl("/"));

        // Same session, no need to log in again
        mockMvc.perform(get("/clients").session(session)).andExpect(status().isOk());
        User reloaded = userRepository.findByUsername("rookie").orElseThrow();
        assertThat(reloaded.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches("MyOwnPass789", reloaded.getPassword())).isTrue();
    }

    @Test
    void wrongCurrentPasswordIsRefused() throws Exception {
        admin("careful");
        MockHttpSession session = login("careful", PASSWORD);
        mockMvc.perform(post("/account/password").session(session).with(csrf())
                .param("currentPassword", "not-it")
                .param("newPassword", "MyOwnPass789")
                .param("confirmPassword", "MyOwnPass789"))
            // Not a forced change: back to the password form of "Mon compte"
            .andExpect(redirectedUrl("/account?password=true"));
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findByUsername("careful").orElseThrow().getPassword())).isTrue();
    }

    @Test
    void legacyHashWithoutPrefixIsUpgradedAtLogin() throws Exception {
        User legacy = admin("legacy");
        legacy.setPassword(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(PASSWORD));
        userRepository.flush();
        assertThat(legacy.getPassword()).doesNotStartWith("{");

        mockMvc.perform(formLogin("/perform-login").user("legacy").password(PASSWORD)).andExpect(authenticated());

        assertThat(userRepository.findByUsername("legacy").orElseThrow().getPassword()).startsWith("{argon2}");
    }

    @Test
    void loginPageRedirectsWhenAlreadyLoggedIn() throws Exception {
        admin("already");
        MockHttpSession session = login("already", PASSWORD);
        mockMvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/"));
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }
}
