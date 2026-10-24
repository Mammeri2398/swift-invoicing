package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.security.domain.PasswordResetToken;
import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.repository.PasswordResetTokenRepository;
import com.swiftlabs.invoicing.security.repository.ProfileRepository;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Anonymous password reset: no authenticated user on any of these requests. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowTest {

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

    private String requestToken() throws Exception {
        mockMvc.perform(post("/forgot-password/send").with(csrf()).param("email", user.getEmail()))
            .andExpect(redirectedUrl("/login"));
        return tokensOfUser().get(0).getToken();
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
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post("/forgot-password/send").param("email", user.getEmail()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/reset-password/save")
                .param("token", "x").param("password", "NewPass456").param("confirmPassword", "NewPass456"))
            .andExpect(status().isForbidden());
    }
}
