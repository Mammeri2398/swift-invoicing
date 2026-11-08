package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

/** The language of the pages: each user's own once logged in, the browser's (or the one picked) before. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LanguageTest {

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

    private MockHttpSession login(String username, Language language) throws Exception {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setLanguage(language);
        accessService.grant(userRepository.save(user), CompanySchemas.FIRST_ID, profileRepository.findByName("Administrateur"));
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", username).param("password", "Secret123"));
        return session;
    }

    @Test
    void switchingLanguageKeepsThePagesParameters() throws Exception {
        // A reset link's token or a looked up number must survive the change of language
        mockMvc.perform(get("/invoices/lookup?number=FA-2026-0001&lang=fr"))
            .andExpect(content().string(containsString("href=\"/invoices/lookup?number=FA-2026-0001&amp;lang=en\"")))
            .andExpect(content().string(containsString("href=\"/invoices/lookup?number=FA-2026-0001&amp;lang=ar\"")));
    }

    @Test
    void beforeLoginThePagesFollowTheBrowserAndFrenchByDefault() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(content().string(containsString("lang=\"fr\" dir=\"ltr\"")))
            .andExpect(content().string(containsString("Swift Invoicing par Swift Labs")));
        mockMvc.perform(get("/login").header("Accept-Language", "en-GB,en;q=0.9"))
            .andExpect(content().string(containsString("lang=\"en\"")))
            .andExpect(content().string(containsString("Swift Invoicing by Swift Labs")));
        mockMvc.perform(get("/login").header("Accept-Language", "de-DE,de;q=0.9"))
            .andExpect(content().string(containsString("Swift Invoicing par Swift Labs")));
    }

    @Test
    void theLanguagePickedOnTheLoginPageIsRemembered() throws Exception {
        MvcResult picked = mockMvc.perform(get("/login").param("lang", "ar"))
            .andExpect(cookie().value("lang", "ar"))
            .andExpect(content().string(containsString("lang=\"ar\" dir=\"rtl\"")))
            .andExpect(content().string(containsString("Swift Invoicing من Swift Labs")))
            .andReturn();
        Cookie remembered = picked.getResponse().getCookie("lang");
        mockMvc.perform(get("/login").cookie(remembered).header("Accept-Language", "fr"))
            .andExpect(content().string(containsString("dir=\"rtl\"")));
    }

    @Test
    void eachUserWorksInTheirOwnLanguage() throws Exception {
        MockHttpSession english = login("lang-en", Language.EN);
        MockHttpSession french = login("lang-fr", Language.FR);

        // The user's language wins over the browser's
        mockMvc.perform(get("/account").session(english).header("Accept-Language", "fr"))
            .andExpect(content().string(containsString("lang=\"en\"")))
            .andExpect(content().string(containsString("Personal information")));
        mockMvc.perform(get("/account").session(french))
            .andExpect(content().string(containsString("Informations personnelles")));

        mockMvc.perform(post("/account/language").session(english).with(csrf()).param("language", "ar"));
        mockMvc.perform(get("/account").session(english))
            .andExpect(content().string(containsString("dir=\"rtl\"")))
            .andExpect(content().string(containsString("المعلومات الشخصية")));
        assertThat(userRepository.findByUsername("lang-en").orElseThrow().getLanguage()).isEqualTo(Language.AR);

        // The other user is not affected
        mockMvc.perform(get("/account").session(french))
            .andExpect(content().string(containsString("Informations personnelles")));
    }
}
