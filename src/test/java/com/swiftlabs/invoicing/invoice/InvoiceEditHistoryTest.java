package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Each save of a draft lists in its history what changed: details, lines and totals, old value → new value. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvoiceEditHistoryTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceAudit audit;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private MockMvc mockMvc;

    private Client client;

    @BeforeEach
    void client() {
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Client suivi");
        client = clientRepository.save(client);
    }

    @AfterEach
    void french() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static InvoiceLineForm line(String description, String quantity, String price) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setUnit("jour");
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(price));
        return line;
    }

    private Invoice draft() {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 10, 1));
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        form.setLines(List.of(line("Développement", "2", "5000"), line("Hébergement", "1", "12000")));
        return invoiceService.saveDraft(form);
    }

    private List<InvoiceEvent> edits(Invoice invoice) {
        return audit.history(invoice.getId()).stream().filter(e -> e.getAction() == InvoiceEvent.Action.EDITED).toList();
    }

    @Test
    void anEditListsEveryChangeWithItsOldAndNewValue() {
        Invoice draft = draft();
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.getLines().get(0).setUnitPrice(new BigDecimal("6000"));
        List<InvoiceLineForm> lines = new ArrayList<>(List.of(form.getLines().get(0), line("Formation", "3", "1000")));
        form.setLines(lines);
        form.setDueDate(LocalDate.of(2026, 10, 31));
        form.setPaymentMethod(PaymentMethod.CASH);
        invoiceService.saveDraft(form);

        assertThat(edits(draft)).singleElement().satisfies(edit -> assertThat(audit.changes(edit)).containsExactly(
            "Échéance : — → 31/10/2026",
            "Mode de paiement : Virement bancaire → Espèces",
            "Ligne 1 · Prix unitaire : 5 000,00 DA → 6 000,00 DA",
            "Ligne 1 · Total HT de la ligne : 10 000,00 DA → 12 000,00 DA",
            "Ligne 2 · Désignation : Hébergement → Formation",
            "Ligne 2 · Quantité : 1 → 3",
            "Ligne 2 · Prix unitaire : 12 000,00 DA → 1 000,00 DA",
            "Ligne 2 · Total HT de la ligne : 12 000,00 DA → 3 000,00 DA",
            "Total HT : 22 000,00 DA → 15 000,00 DA",
            "Droit de timbre : 0,00 DA → 150,00 DA",
            "Montant à payer : 22 000,00 DA → 15 150,00 DA"));
    }

    @Test
    void addedAndRemovedLinesAreNamed() {
        Invoice draft = draft();
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        List<InvoiceLineForm> threeLines = new ArrayList<>(form.getLines());
        threeLines.add(line("Maintenance", "1", "5909.090909"));
        form.setLines(threeLines);
        invoiceService.saveDraft(form);
        form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.setLines(List.of(form.getLines().get(0)));
        invoiceService.saveDraft(form);

        List<InvoiceEvent> edits = edits(draft);
        assertThat(audit.changes(edits.get(0))).contains("Ligne 3 ajoutée : Maintenance", "Ligne 3 · Unité : — → jour",
            "Ligne 3 · Quantité : — → 1", "Ligne 3 · Prix unitaire : — → 5 909,090909 DA");
        assertThat(audit.changes(edits.get(1))).contains("Ligne 2 supprimée : Hébergement", "Ligne 3 supprimée : Maintenance",
            "Ligne 2 · Total HT de la ligne : 12 000,00 DA → —");
    }

    @Test
    void aSaveThatChangesNothingRecordsNothing() {
        Invoice draft = draft();
        invoiceService.saveDraft(InvoiceForm.from(invoiceService.findWithLines(draft.getId())));
        assertThat(edits(draft)).isEmpty();
    }

    @Test
    void theChangesAreShownInTheReadersLanguage() {
        Invoice draft = draft();
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.getLines().get(0).setQuantity(new BigDecimal("2.5"));
        invoiceService.saveDraft(form);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(audit.changes(edits(draft).get(0))).contains("Line 1 · Quantity: 2 → 2,5");
    }

    @Test
    @WithMockUser(authorities = "VIEW_INVOICES")
    void theInvoicePageShowsTheEdit() throws Exception {
        Invoice draft = draft();
        InvoiceForm form = InvoiceForm.from(invoiceService.findWithLines(draft.getId()));
        form.getLines().get(0).setUnitPrice(new BigDecimal("7000"));
        invoiceService.saveDraft(form);

        mockMvc.perform(get("/invoices/" + draft.getId()))
            .andExpect(content().string(containsString("Brouillon modifié")))
            .andExpect(content().string(containsString("Ligne 1 · Prix unitaire : 5 000,00 DA → 7 000,00 DA")));
    }
}
