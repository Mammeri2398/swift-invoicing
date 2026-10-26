package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.Language;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Anonymous password reset: no authenticated user on any of these requests. The link is read from the sent email. */
@SpringBootTest(properties = "mail.enabled=true")
@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    @MockitoBean
    private JavaMailSender mailSender;

    private static final String GENERIC_MESSAGE = "Si cet email existe, un lien de réinitialisation vous a été envoyé.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void createUser() {
        user = new User();
        user.setUsername("reset-user");
        user.setEmail("reset-user@example.test");
        user.setPassword(passwordEncoder.encode("OldPass123"));
        user.setEnabled(true);
        user.setProfile(profileRepository.findByName("Administrateur"));
        user = userRepository.save(user);
    }

    private List<PasswordResetToken> tokensOfUser() {
        return tokenRepository.findAll().stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .toList();
    }

    /** Requests a link like a user would, and returns the token from the email. */
    private String requestToken() throws Exception {
        clearInvocations(mailSender);
        mockMvc.perform(post("/forgot-password/send").with(csrf()).param("email", user.getEmail()))
            .andExpect(redirectedUrl("/login"));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(user.getEmail());
        Matcher matcher = TOKEN_IN_LINK.matcher(sent.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        String token = requestToken();

        PasswordResetToken stored = tokensOfUser().get(0);
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(token).isEqualTo(PasswordResetService.hash(token));
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void disabledUserGetsNoLink() throws Exception {
        user.setEnabled(false);
        mockMvc.perform(post("/forgot-password/send").with(csrf()).param("email", user.getEmail()))
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("message", GENERIC_MESSAGE));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(tokensOfUser()).isEmpty();
    }

    @Test
    void anonymousUserCanRequestResetLink() throws Exception {
        mockMvc.perform(post("/forgot-password/send").with(csrf()).param("email", user.getEmail()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("message", GENERIC_MESSAGE));

        assertThat(tokensOfUser()).hasSize(1);
    }

    @Test
    void unknownEmailGetsSameAnswerAndNoToken() throws Exception {
        long before = tokenRepository.count();
        mockMvc.perform(post("/forgot-password/send").with(csrf()).param("email", "nobody@example.test"))
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("message", GENERIC_MESSAGE));

        assertThat(tokenRepository.count()).isEqualTo(before);
    }

    @Test
    void anonymousUserCanResetPasswordWithValidToken() throws Exception {
        String token = requestToken();

        mockMvc.perform(post("/reset-password/save").with(csrf())
                .param("token", token)
                .param("password", "NewPass456")
                .param("confirmPassword", "NewPass456"))
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attributeExists("success"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456", reloaded.getPassword())).isTrue();
        assertThat(tokensOfUser()).isEmpty();
    }

    @Test
    void tokenCannotBeReused() throws Exception {
        String token = requestToken();
        mockMvc.perform(post("/reset-password/save").with(csrf())
            .param("token", token).param("password", "NewPass456").param("confirmPassword", "NewPass456"));

        mockMvc.perform(post("/reset-password/save").with(csrf())
                .param("token", token).param("password", "Other789x").param("confirmPassword", "Other789x"))
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("error", "Lien de réinitialisation invalide ou expiré."));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456", reloaded.getPassword())).isTrue();
    }

    @Test
    void mismatchedOrWeakPasswordKeepsUserOnResetPage() throws Exception {
        String token = requestToken();

        mockMvc.perform(post("/reset-password/save").with(csrf())
                .param("token", token).param("password", "NewPass456").param("confirmPassword", "Different1"))
            .andExpect(redirectedUrl("/reset-password?token=" + token))
            .andExpect(flash().attribute("error", "Les mots de passe ne correspondent pas."));

        mockMvc.perform(post("/reset-password/save").with(csrf())
                .param("token", token).param("password", "weak").param("confirmPassword", "weak"))
            .andExpect(redirectedUrl("/reset-password?token=" + token))
            .andExpect(flash().attributeExists("error"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPass123", reloaded.getPassword())).isTrue();
        assertThat(tokensOfUser()).hasSize(1);
    }

    @Test
    void theEmailIsInTheUsersLanguageAndThePagesInTheBrowsers() throws Exception {
        user.setLanguage(Language.EN);
        mockMvc.perform(get("/forgot-password").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("we will send you a link valid for")));

        clearInvocations(mailSender);
        mockMvc.perform(post("/forgot-password/send").with(csrf()).header("Accept-Language", "en").param("email", user.getEmail()))
            .andExpect(flash().attribute("message", "If this email address exists, a password reset link has been sent to it."));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("Reset your password - Swift Invoicing");
        assertThat(sent.getValue().getText()).startsWith("Hello reset-user,").contains("minutes and can only be used once");

        // A French-speaking user gets it in French, whatever the language of the page
        user.setLanguage(Language.FR);
        clearInvocations(mailSender);
        mockMvc.perform(post("/forgot-password/send").with(csrf()).header("Accept-Language", "en").param("email", user.getEmail()));
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("Réinitialisation de votre mot de passe - Swift Invoicing");
    }

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post("/forgot-password/send").param("email", user.getEmail()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/reset-password/save")
                .param("token", "x").param("password", "NewPass456").param("confirmPassword", "NewPass456"))
            .andExpect(status().isForbidden());
    }
}
