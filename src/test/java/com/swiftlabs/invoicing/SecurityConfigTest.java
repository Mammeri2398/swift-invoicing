package com.swiftlabs.invoicing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = "DELETE_INVOICE")
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/invoices/999/delete"))
            .andExpect(status().isForbidden());
    }

    @Test
    void loginPageStoresCsrfTokenBeforeRendering() throws Exception {
        // Smoke test only: MockMvc never commits the response early, so it cannot reproduce
        // the cut-off login form that deferred CSRF tokens caused on a real server.
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(request().sessionAttribute(
                "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN", notNullValue()))
            .andExpect(content().string(containsString("name=\"username\"")));
    }

    @Test
    @WithMockUser(authorities = "DELETE_INVOICE")
    void postWithCsrfTokenIsAccepted() throws Exception {
        mockMvc.perform(post("/invoices/999/delete").with(csrf()))
            .andExpect(status().is3xxRedirection());
    }
}
