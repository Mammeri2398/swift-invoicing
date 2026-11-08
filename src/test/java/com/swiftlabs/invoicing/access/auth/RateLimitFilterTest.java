package com.swiftlabs.invoicing.access.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Per-IP limits with small values; each test uses its own client address. */
@SpringBootTest(properties = {
    "app.security.rate-limit.login.requests=3",
    "app.security.rate-limit.password-reset.requests=2",
    "app.security.rate-limit.lookup.requests=2",
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void login(String ip) throws Exception {
        mockMvc.perform(post("/perform-login").with(csrf()).with(from(ip)).param("username", "nobody").param("password", "x"));
    }

    @Test
    void loginAttemptsAreLimitedPerAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/perform-login").with(csrf()).with(from("10.0.0.1")).param("username", "nobody").param("password", "x"))
                .andExpect(status().is3xxRedirection());
        }
        mockMvc.perform(post("/perform-login").with(csrf()).with(from("10.0.0.1")).param("username", "nobody").param("password", "x"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));

        // Another address is not affected
        mockMvc.perform(post("/perform-login").with(csrf()).with(from("10.0.0.2")).param("username", "nobody").param("password", "x"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void anEncodedPathCountsLikeThePlainOne() throws Exception {
        // /perform%2Dlogin is /perform-login once decoded: it must not escape the limit
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(URI.create("/perform%2Dlogin")).with(csrf()).with(from("10.0.0.9"))
                .param("username", "nobody").param("password", "x"));
        }
        mockMvc.perform(post("/perform-login").with(csrf()).with(from("10.0.0.9")).param("username", "nobody").param("password", "x"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void theLimitPageExplainsWhatHappened() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.0.3");
        }
        // sendError(429) is rendered by the error page (as Spring Boot does on a real server)
        mockMvc.perform(get("/error").with(from("10.0.0.3")).accept(MediaType.TEXT_HTML)
                .requestAttr("jakarta.servlet.error.status_code", 429))
            .andExpect(content().string(containsString("Trop de tentatives")));
    }

    @Test
    void linkRequestsAndNewPasswordsShareOneLimit() throws Exception {
        mockMvc.perform(post("/forgot-password/send").with(csrf()).with(from("10.0.1.1")).param("email", "a@example.test"))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/reset-password/save").with(csrf()).with(from("10.0.1.1"))
                .param("token", "x").param("password", "Abcdefg1").param("confirmPassword", "Abcdefg1"))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/forgot-password/send").with(csrf()).with(from("10.0.1.1")).param("email", "a@example.test"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void lookupsAreLimitedButNotTheEmptyForm() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/invoices/lookup").with(from("10.0.2.1"))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/invoices/lookup").with(from("10.0.2.1")).param("number", "FA-2026-0001")).andExpect(status().isOk());
        mockMvc.perform(get("/invoices/lookup").with(from("10.0.2.1")).param("number", "FA-2026-0002")).andExpect(status().isOk());
        mockMvc.perform(get("/invoices/lookup").with(from("10.0.2.1")).param("number", "FA-2026-0003"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void otherPagesAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/login").with(from("10.0.3.1"))).andExpect(status().isOk());
        }
    }
}
