package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Each page and action leaves one line in the log (logger "activity"), with who, in which company, and the result. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ActivityLogTest {

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
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void pagesAndActionsAreLoggedWithTheUserAndTheCompany(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/account"));
        mockMvc.perform(post("/account/language").with(csrf()).param("language", "FR"));
        mockMvc.perform(get("/css/app.css"));

        assertThat(output.getOut()).containsPattern("GET  /account -> \\d{3} \\(\\d+ ms\\) \\[admin @ [^\\]]+\\] AccountController#")
            .containsPattern("POST /account/language -> 302 \\(\\d+ ms\\) \\[admin @ [^\\]]+\\] AccountController#")
            // Pages only: no line for static files
            .doesNotContain("/css/app.css ->");
    }

    @Test
    void visitorsAreAnonymousAndSecretsInAddressesAreMasked(CapturedOutput output) throws Exception {
        // In the address, as a browser sends it
        mockMvc.perform(get("/reset-password?token=s3cr3t-reset-link"));

        assertThat(output.getOut()).contains("GET  /reset-password?token=*** -> ").contains("[anonyme]")
            .doesNotContain("s3cr3t-reset-link");
    }
}
