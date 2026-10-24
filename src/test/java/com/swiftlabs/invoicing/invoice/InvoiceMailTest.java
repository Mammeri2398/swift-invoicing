package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.issuer.IssuerService;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sending invoices through an in-memory SMTP server (GreenMail). */
@SpringBootTest(properties = {
    "mail.enabled=true",
    "spring.mail.host=localhost",
    "spring.mail.port=3025",
    "mail.from=factures@swift.test",
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "samia", authorities = {"VIEW_INVOICES", "ISSUE_INVOICE"})
class InvoiceMailTest {

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceAudit audit;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    private Invoice draft;
    private Invoice issued;

    @BeforeEach
    void data() {
        issuerService.getIssuer().setFullName("Karim Benali");
        issuerService.getIssuer().setTradeName("Benali Web");
        issuerService.getIssuer().setEmail("karim@benali.test");
        issuerService.getIssuer().setBankOrCcpAccount("CCP 0021458992 clé 45");

        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Mohamed Salah");
        client.setEmail("m.salah@client.test");
        client = clientRepository.save(client);

        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Site vitrine");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("62000.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 10, 20));
        form.setDueDate(LocalDate.of(2026, 11, 20));
        form.setLines(List.of(line));
        issued = invoiceService.issue(invoiceService.saveDraft(form).getId());
        draft = invoiceService.saveDraft(form);
    }

    @Test
    void formIsPrefilledForTheClient() throws Exception {
        mockMvc.perform(get("/invoices/" + issued.getId() + "/email"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"m.salah@client.test\"")))
            .andExpect(content().string(containsString("value=\"Facture " + issued.getInvoiceNumber() + " - Benali Web\"")))
            .andExpect(content().string(containsString("d&#39;un montant de 62 000,00 DA, payable avant le 20/11/2026.")))
            .andExpect(content().string(containsString("R&egrave;glement : CCP 0021458992 cl&eacute; 45")))
            .andExpect(content().string(containsString("factures@swift.test")));
    }

    @Test
    void sendingDeliversThePdfAndRecordsItInTheHistory() throws Exception {
        mockMvc.perform(post("/invoices/" + issued.getId() + "/email/send").with(csrf())
                .param("to", "m.salah@client.test")
                .param("cc", "compta@client.test")
                .param("subject", "Votre facture")
                .param("message", "Bonjour,\nci-joint la facture.\nKarim"))
            .andExpect(redirectedUrl("/invoices/" + issued.getId()))
            .andExpect(flash().attribute("success", "Facture envoyée à m.salah@client.test."));

        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(2); // the recipient and the copy
        MimeMessage mail = received[0];
        assertThat(mail.getSubject()).isEqualTo("Votre facture");
        InternetAddress from = (InternetAddress) mail.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("factures@swift.test");
        assertThat(from.getPersonal()).isEqualTo("Benali Web");
        assertThat(mail.getReplyTo()[0].toString()).isEqualTo("karim@benali.test");
        assertThat(mail.getAllRecipients()).extracting(Object::toString)
            .containsExactly("m.salah@client.test", "compta@client.test");

        Multipart parts = (Multipart) mail.getContent();
        Part attachment = attachment(parts);
        assertThat(attachment.getFileName()).isEqualTo(issued.getInvoiceNumber() + ".pdf");
        assertThat(attachment.getContentType()).startsWith("application/pdf");
        assertThat(attachment.getInputStream().readNBytes(5)).isEqualTo("%PDF-".getBytes());

        assertThat(audit.history(issued.getId()))
            .extracting(InvoiceEvent::getAction, InvoiceEvent::getUsername, InvoiceEvent::getDetail)
            .endsWith(tuple(InvoiceEvent.Action.SENT, "samia", "m.salah@client.test, cc compta@client.test"));
        mockMvc.perform(get("/invoices/" + issued.getId()))
            .andExpect(content().string(containsString("Envoyée par email")))
            .andExpect(content().string(containsString("à m.salah@client.test, cc compta@client.test")));
    }

    @Test
    void invalidAddressIsShownOnTheForm() throws Exception {
        mockMvc.perform(post("/invoices/" + issued.getId() + "/email/send").with(csrf())
                .param("to", "pas-une-adresse")
                .param("subject", "Votre facture")
                .param("message", "Bonjour"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("mail", "to", "Email"))
            .andExpect(content().string(containsString("value=\"pas-une-adresse\"")));
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    void draftsAreNotSent() throws Exception {
        mockMvc.perform(get("/invoices/" + draft.getId() + "/email"))
            .andExpect(redirectedUrl("/invoices/" + draft.getId()))
            .andExpect(flash().attribute("error", "Seule une facture émise ou payée peut être envoyée par email."));
        mockMvc.perform(post("/invoices/" + draft.getId() + "/email/send").with(csrf())
                .param("to", "m.salah@client.test").param("subject", "x").param("message", "x"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Seule une facture émise ou payée")));
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    void anUnreachableServerIsReportedAndNothingIsRecorded() throws Exception {
        smtp.stop();
        mockMvc.perform(post("/invoices/" + issued.getId() + "/email/send").with(csrf())
                .param("to", "m.salah@client.test").param("subject", "Votre facture").param("message", "Bonjour"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("L&#39;email n&#39;a pas pu être envoyé")));
        assertThat(audit.history(issued.getId())).extracting(InvoiceEvent::getAction)
            .doesNotContain(InvoiceEvent.Action.SENT);
    }

    @Test
    @WithMockUser(authorities = "VIEW_INVOICES")
    void sendingNeedsTheRightToIssueInvoices() throws Exception {
        mockMvc.perform(get("/invoices/" + issued.getId() + "/email")).andExpect(status().isForbidden());
        mockMvc.perform(post("/invoices/" + issued.getId() + "/email/send").with(csrf())
                .param("to", "m.salah@client.test").param("subject", "x").param("message", "x"))
            .andExpect(status().isForbidden());
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    private static Part attachment(Multipart parts) throws Exception {
        for (int i = 0; i < parts.getCount(); i++) {
            if (Part.ATTACHMENT.equalsIgnoreCase(parts.getBodyPart(i).getDisposition())) {
                return parts.getBodyPart(i);
            }
        }
        throw new AssertionError("No attachment");
    }
}
