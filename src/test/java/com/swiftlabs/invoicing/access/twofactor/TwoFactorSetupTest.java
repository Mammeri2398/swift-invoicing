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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Setting up two-factor login from the account page, like a user with their phone. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TwoFactorSetupTest {

    private static final Pattern SECRET_ON_PAGE = Pattern.compile("((?:[A-Z2-7]{4} ){7}[A-Z2-7]{4})");

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
        user.setUsername("samia");
        user.setEmail("samia@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setProfile(profileRepository.findByName("Administrateur"));
        user = userRepository.save(user);
        session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "samia").param("password", "Secret123"))
            .andExpect(redirectedUrl("/"));
    }

    /** Reads the key from the page, as someone typing it into their app would. */
    private String secretShownOnPage(String url) throws Exception {
        MvcResult page = mockMvc.perform(get(url).session(session))
            .andExpect(content().string(containsString("data:image/svg+xml;base64,")))
            .andReturn();
        Matcher matcher = SECRET_ON_PAGE.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1).replace(" ", "");
    }

    private static String code(String secret) {
        return Totp.code(Totp.base32Decode(secret), Totp.step(Instant.now()));
    }

    @SuppressWarnings("unchecked")
    private List<String> enable(String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/account/two-factor/confirm").session(session).with(csrf()).param("code", code(secret)))
            .andExpect(redirectedUrl("/account/two-factor"))
            .andReturn();
        return (List<String>) result.getFlashMap().get("recoveryCodes");
    }

    @Test
    void scanningAndTypingACodeTurnsItOnAndShowsRecoveryCodes() throws Exception {
        String secret = secretShownOnPage("/account/two-factor");
        List<String> codes = enable(secret);

        assertThat(codes).hasSize(10).allMatch(c -> c.matches("[a-z2-9]{4}-[a-z2-9]{4}"));
        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.isTotpEnabled()).isTrue();
        assertThat(saved.getTotpSecret()).isEqualTo(secret);
        mockMvc.perform(get("/account/two-factor").session(session))
            .andExpect(content().string(containsString("Activée")))
            .andExpect(content().string(containsString("10</strong> code(s) de secours")))
            .andExpect(content().string(not(containsString("data:image/svg+xml"))));
    }

    @Test
    void aWrongCodeLeavesItOff() throws Exception {
        secretShownOnPage("/account/two-factor");
        mockMvc.perform(post("/account/two-factor/confirm").session(session).with(csrf()).param("code", "000000"))
            .andExpect(redirectedUrl("/account/two-factor"))
            .andExpect(flash().attribute("error", containsString("Code incorrect")));
        assertThat(userRepository.findById(user.getId()).orElseThrow().isTotpEnabled()).isFalse();
    }

    @Test
    void movingToANewPhoneReplacesTheSecret() throws Exception {
        String oldSecret = secretShownOnPage("/account/two-factor");
        enable(oldSecret);
        String newSecret = secretShownOnPage("/account/two-factor?change=true");
        assertThat(newSecret).isNotEqualTo(oldSecret);
        enable(newSecret);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTotpSecret()).isEqualTo(newSecret);
    }

    @Test
    void turningItOffNeedsACurrentCode() throws Exception {
        String secret = secretShownOnPage("/account/two-factor");
        enable(secret);
        mockMvc.perform(post("/account/two-factor/disable").session(session).with(csrf()).param("code", "000000"))
            .andExpect(flash().attribute("error", "Code incorrect."));
        assertThat(userRepository.findById(user.getId()).orElseThrow().isTotpEnabled()).isTrue();

        // The code used to enable is spent: wait for the next one, or use the next step directly
        String next = Totp.code(Totp.base32Decode(secret), Totp.step(Instant.now()) + 1);
        mockMvc.perform(post("/account/two-factor/disable").session(session).with(csrf()).param("code", next))
            .andExpect(flash().attribute("success", "Double authentification désactivée."));
        assertThat(userRepository.findById(user.getId()).orElseThrow().isTotpEnabled()).isFalse();
    }
}
