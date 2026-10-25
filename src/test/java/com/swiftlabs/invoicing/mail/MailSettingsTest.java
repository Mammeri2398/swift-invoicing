package com.swiftlabs.invoicing.mail;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceEmailForm;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceMailService;
import com.swiftlabs.invoicing.invoice.InvoiceService;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Email settings saved in the application (mail.enabled is false in tests: only these settings send). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER", "VIEW_INVOICES", "CREATE_INVOICE", "ISSUE_INVOICE"})
class MailSettingsTest {

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP)
        .withConfiguration(GreenMailConfiguration.aConfig().withUser("factures@atlas.test", "secret"));

    @Autowired
    private MailSettingsService settingsService;

    @Autowired
    private MailGateway gateway;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceMailService invoiceMailService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private MockMvc mockMvc;

    private MailSettingsForm localServer() {
        MailSettingsForm form = new MailSettingsForm();
        form.setEnabled(true);
        form.setHost("localhost");
        form.setPort(3025);
        form.setSecurity(MailSecurity.NONE);
        form.setUsername("factures@atlas.test");
        form.setPassword("secret");
        return form;
    }

    @Test
    void nothingIsSentUntilAServerIsSaved() {
        assertThat(gateway.isEnabled()).isFalse();
        assertThatThrownBy(() -> settingsService.sendTest("moi@atlas.test")).hasMessageContaining("Enregistrez d'abord");
    }

    @Test
    void theTestMessageGoesThroughTheSavedServer() throws Exception {
        settingsService.save(localServer());
        assertThat(gateway.isEnabled()).isTrue();
        assertThat(gateway.fromAddress()).isEqualTo("factures@atlas.test");

        settingsService.sendTest("moi@atlas.test");
        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(smtp.getReceivedMessages()[0].getSubject()).isEqualTo("Swift Invoicing : test d'envoi");
        assertThat(smtp.getReceivedMessages()[0].getFrom()[0].toString()).isEqualTo("factures@atlas.test");
    }

    @Test
    void anEmptyPasswordFieldKeepsTheSavedOne() {
        settingsService.save(localServer());
        MailSettingsForm again = localServer();
        again.setPassword("");
        again.setFromAddress("contact@atlas.test");
        settingsService.save(again);
        assertThat(settingsService.current().getPassword()).isEqualTo("secret");
        assertThat(gateway.fromAddress()).isEqualTo("contact@atlas.test");
    }

    @Test
    void invoicesAreSentWithTheSavedServer() throws Exception {
        settingsService.save(localServer());
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client Email");
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 3, 1));
        form.setLines(List.of(line));
        Invoice invoice = invoiceService.issue(invoiceService.saveDraft(form).getId());

        InvoiceEmailForm email = invoiceMailService.prepare(invoice.getId());
        email.setTo("client@example.test");
        invoiceMailService.send(invoice.getId(), email);
        assertThat(smtp.getReceivedMessages()).hasSize(1);
    }

    @Test
    void aWrongPasswordIsExplained() {
        MailSettingsForm wrong = localServer();
        wrong.setPassword("wrong");
        settingsService.save(wrong);
        assertThatThrownBy(() -> settingsService.sendTest("moi@atlas.test"))
            .hasMessageStartingWith("L'envoi a échoué : 535")
            .hasMessageEndingWith("Vérifiez le serveur, le port, la sécurité et le compte.");
    }

    @Test
    void turnedOffOrIncompleteSettingsAreRefused() throws Exception {
        MailSettingsForm off = localServer();
        off.setEnabled(false);
        settingsService.save(off);
        assertThat(gateway.isEnabled()).isFalse();

        MailSettingsForm noHost = localServer();
        noHost.setHost("");
        assertThatThrownBy(() -> settingsService.save(noHost)).isInstanceOf(BusinessException.class).hasMessageContaining("serveur");

        mockMvc.perform(get("/settings/mail"))
            .andExpect(content().string(containsString("Envoi des emails")))
            .andExpect(content().string(containsString("Enregistré : laisser vide pour le garder")));
    }
}
