package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public lookup page confirms an invoice exists without revealing its client or amount. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvoiceLookupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    private Invoice issued;

    @BeforeEach
    @WithMockUser
    void data() {
        // The seeded admin signs in for one test: its temporary password counts as chosen
        userRepository.findByUsername("admin").ifPresent(admin -> admin.changePassword(admin.getPassword(), LocalDateTime.now()));
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Confidentiel");
        client = clientRepository.save(client);

        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation secrète");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("123456.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 2, 3));
        form.setLines(List.of(line));
        issued = invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    @Test
    void anonymousVisitorSeesExistenceStatusAndDateOnly() throws Exception {
        mockMvc.perform(get("/invoices/lookup").param("number", issued.getInvoiceNumber()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Facture authentique")))
            .andExpect(content().string(containsString("03/02/2026")))
            .andExpect(content().string(containsString("Émise")))
            .andExpect(content().string(not(containsString("Client Confidentiel"))))
            .andExpect(content().string(not(containsString("123 456,00"))))
            .andExpect(content().string(not(containsString("Prestation secrète"))))
            .andExpect(content().string(not(containsString("Ouvrir la facture"))));
    }

    @Test
    void anonymousVisitorReadsThePageInTheBrowserLanguage() throws Exception {
        mockMvc.perform(get("/invoices/lookup").param("number", issued.getInvoiceNumber()).header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Verify an invoice")))
            .andExpect(content().string(containsString("Invoice verified as authentic")))
            .andExpect(content().string(containsString("Issue date")))
            .andExpect(content().string(not(containsString("Facture authentique"))));
        mockMvc.perform(get("/invoices/lookup").param("number", "FA-1999-9999").param("lang", "ar"))
            .andExpect(content().string(containsString("dir=\"rtl\"")))
            .andExpect(content().string(containsString("لا توجد أي فاتورة تحمل الرقم")))
            // Visitors without an account can switch language on the page
            .andExpect(content().string(containsString("?lang=en")));
    }

    @Test
    void unknownNumberSaysSo() throws Exception {
        mockMvc.perform(get("/invoices/lookup").param("number", "FA-1999-9999"))
            .andExpect(content().string(containsString("Aucune facture ne porte le numéro")));
    }

    /** A real signed-in user: the link needs their company to be the invoice's. */
    @Test
    @WithUserDetails(value = "admin", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void userAllowedToViewInvoicesGetsTheLink() throws Exception {
        mockMvc.perform(get("/invoices/lookup").param("number", issued.getInvoiceNumber()))
            .andExpect(content().string(containsString("href=\"/invoices/" + issued.getId() + "\"")))
            .andExpect(content().string(not(containsString("Client Confidentiel"))));
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    void otherLoggedInUsersDoNotGetTheLink() throws Exception {
        mockMvc.perform(get("/invoices/lookup").param("number", issued.getInvoiceNumber()))
            .andExpect(content().string(not(containsString("Ouvrir la facture"))));
    }
}
