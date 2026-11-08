package com.swiftlabs.invoicing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Every page ends with the product's name, its maker and its version, the login page included, and nothing else: the
 * invoice check is linked from the invoice list only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FooterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pagesBeforeLoginCarryTheSignature() throws Exception {
        mockMvc.perform(get("/login")).andExpect(content().string(containsString("Swift Invoicing par Swift Labs · v2.0")));
        mockMvc.perform(get("/invoices/lookup")).andExpect(content().string(containsString("Swift Invoicing par Swift Labs · v2.0")));
        mockMvc.perform(get("/login").param("lang", "en"))
            .andExpect(content().string(containsString("Swift Invoicing by Swift Labs · v2.0")));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENTS"})
    void pagesAfterLoginCarryItToo() throws Exception {
        mockMvc.perform(get("/clients")).andExpect(content().string(containsString("Swift Invoicing par Swift Labs · v2.0")));
    }

    @Test
    void theFooterHoldsOnlyTheSignature() throws Exception {
        mockMvc.perform(get("/login")).andExpect(content().string(not(containsString("Montants en DZD"))));
    }

    @Test
    void theLoginPageDoesNotLinkToTheInvoiceCheck() throws Exception {
        mockMvc.perform(get("/login")).andExpect(content().string(not(containsString("href=\"/invoices/lookup\""))));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENTS"})
    void otherPagesDoNot() throws Exception {
        mockMvc.perform(get("/clients")).andExpect(content().string(not(containsString("href=\"/invoices/lookup\""))));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_INVOICES"})
    void theInvoiceListLinksToIt() throws Exception {
        mockMvc.perform(get("/invoices")).andExpect(content().string(containsString("href=\"/invoices/lookup\"")));
    }
}
