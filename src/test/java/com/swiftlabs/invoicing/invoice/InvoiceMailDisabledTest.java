package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without a mail server (mail.enabled=false, as in development and the default test setup). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_INVOICES", "ISSUE_INVOICE"})
class InvoiceMailDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void pageExplainsHowToConfigureMailAndSendingIsRefused() throws Exception {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client");
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.TEN);
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 10, 1));
        form.setLines(List.of(line));
        Invoice issued = invoiceService.issue(invoiceService.saveDraft(form).getId());

        mockMvc.perform(get("/invoices/" + issued.getId() + "/email"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("L'envoi d'emails n'est pas configuré")))
            .andExpect(content().string(containsString("disabled=\"disabled\"")));
        mockMvc.perform(post("/invoices/" + issued.getId() + "/email/send").with(csrf())
                .param("to", "client@example.test").param("subject", "x").param("message", "x"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("n&#39;est pas configuré : réglez le serveur")));
    }
}
