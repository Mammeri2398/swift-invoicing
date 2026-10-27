package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fresh on-premise install: its own empty database (no account), the setup wizard on, no fixed
 * public URL. One test, in order: the wizard can only run once per database.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:onpremise;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "app.setup.wizard=true",
    "app.base-url=",
    "mail.enabled=true",
})
@AutoConfigureMockMvc
class OnPremiseSetupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IssuerService issuerService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void theFirstStartLeadsThroughTheSetupOnce() throws Exception {
        assertThat(userRepository.count()).isZero();
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/setup"));
        mockMvc.perform(get("/login")).andExpect(redirectedUrl("/setup"));
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mockMvc.perform(get("/setup"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Bienvenue dans Swift Invoicing")));
        mockMvc.perform(get("/setup").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("Welcome to Swift Invoicing")))
            .andExpect(content().string(containsString("The password must be between")));

        // Mistakes are shown on the form, nothing is created
        mockMvc.perform(post("/setup").with(csrf())
                .param("adminUsername", "gerant").param("adminEmail", "gerant@atlas.test")
                .param("password", "Secret123").param("confirmPassword", "Secret124")
                .param("companyType", "COMPANY").param("fullName", "Atlas Solutions").param("nif", "000016001234567"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("setup", "confirmPassword"));
        mockMvc.perform(post("/setup").with(csrf())
                .param("adminUsername", "gerant").param("adminEmail", "gerant@atlas.test")
                .param("password", "Secret123").param("confirmPassword", "Secret123")
                .param("companyType", "COMPANY").param("fullName", "Atlas Solutions").param("nif", "000016001234567"))
            .andExpect(model().attributeHasFieldErrors("setup", "legalForm"));
        assertThat(userRepository.count()).isZero();

        // Set up in Arabic: the administrator works in Arabic
        mockMvc.perform(post("/setup").with(csrf()).header("Accept-Language", "ar")
                .param("adminUsername", "gerant").param("adminFirstName", "Nadia").param("adminEmail", "gerant@atlas.test")
                .param("password", "Secret123").param("confirmPassword", "Secret123")
                .param("companyType", "COMPANY").param("fullName", "Atlas Solutions").param("nif", "000016001234567")
                .param("legalForm", "SARL").param("shareCapital", "100000").param("rcNumber", "16/00-1234567B21")
                .param("taxArticle", "16012345678").param("city", "Alger"))
            .andExpect(redirectedUrl("/login?setup"));

        User admin = userRepository.findByUsername("gerant").orElseThrow();
        assertThat(admin.isMustChangePassword()).isFalse();
        // Administrator of the first company, whose details the setup filled in
        assertThat(accessService.profileIn(admin.getId(), CompanySchemas.FIRST_ID).orElseThrow().getName()).isEqualTo("Administrateur");
        assertThat(admin.getLanguage()).isEqualTo(Language.AR);
        // The rest of this test reads the French pages
        admin.setLanguage(Language.FR);
        admin = userRepository.save(admin);
        assertThat(passwordEncoder.matches("Secret123", admin.getPassword())).isTrue();
        assertThat(issuerService.getIssuer().getCompanyType()).isEqualTo(CompanyType.COMPANY);
        assertThat(issuerService.getIssuer().getFullName()).isEqualTo("Atlas Solutions");

        // Done: the setup page is closed and the application works normally
        mockMvc.perform(get("/setup")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/login").param("setup", ""))
            .andExpect(content().string(containsString("Installation terminée")))
            .andExpect(content().string(containsString("Depuis les autres ordinateurs du bureau")));
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "gerant").param("password", "Secret123"))
            .andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/settings/issuer").session(session))
            .andExpect(content().string(containsString("Accès depuis les autres postes")));

        // No fixed public URL: the reset link uses the address the user typed
        mockMvc.perform(post("/forgot-password/send").with(csrf()).param("email", "gerant@atlas.test"));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getText()).contains("http://localhost/reset-password?token=");
    }
}
