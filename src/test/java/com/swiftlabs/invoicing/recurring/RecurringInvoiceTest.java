package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceRepository;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.invoice.InvoiceStatus;
import com.swiftlabs.invoicing.invoice.PaymentMethod;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.Issuer;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recurring invoices: the dates, the drafts they prepare (catch-up, pause, end, failure) and the pages. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecurringInvoiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private RecurringInvoiceService service;

    @Autowired
    private RecurringInvoiceRepository repository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private Client client;

    @BeforeEach
    void company() {
        Issuer issuer = issuerService.getIssuer();
        issuer.setCompanyType(CompanyType.COMPANY);
        issuer.setFullName("Atlas Services");
        issuer.setLegalForm("SARL");
        issuer.setRcNumber("16/00-1234567B21");
        issuer.setTaxArticle("16012345678");
        client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName("Locataire");
        client = clientRepository.save(client);
    }

    private static InvoiceLineForm line(String description, String price, String vatRate) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setUnit("mois");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setVatRate(new BigDecimal(vatRate));
        return line;
    }

    private RecurringInvoice saveModel(String name, Frequency frequency, LocalDate next) {
        RecurringInvoiceForm form = new RecurringInvoiceForm();
        form.setName(name);
        form.setClientId(client.getId());
        form.setFrequency(frequency);
        form.setNextDate(next);
        form.setDueDays(30);
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        form.setNotes("Virement au compte habituel");
        form.setLines(List.of(line("Loyer bureau", "50000.00", "19")));
        return service.save(form);
    }

    /** A real login: the invoice page shows its buttons from the logged-in user's privileges. */
    private MockHttpSession administrator() throws Exception {
        User user = new User();
        user.setUsername("recurring-admin");
        user.setEmail("recurring-admin@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setProfile(profileRepository.findByName("Administrateur"));
        userRepository.save(user);
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "recurring-admin").param("password", "Secret123"));
        return session;
    }

    private List<Invoice> drafts() {
        return invoiceRepository.findAll().stream()
            .filter(invoice -> invoice.getClient() != null && invoice.getClient().getId().equals(client.getId())).toList();
    }

    @Test
    void theDatesAreCountedFromTheFirstOneAndKeepTheEndOfTheMonth() {
        RecurringInvoice model = new RecurringInvoice(client);
        model.setFrequency(Frequency.MONTHLY);
        model.restartOn(LocalDate.of(2026, 1, 31));
        model.advance();
        assertThat(model.getNextDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        model.advance();
        assertThat(model.getNextDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        model.setFrequency(Frequency.QUARTERLY);
        assertThat(model.getNextDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        model.setEndDate(LocalDate.of(2026, 7, 30));
        assertThat(model.isFinished()).isTrue();
        assertThat(model.isDue(LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    void aDueModelPreparesADraftWithItsLinesAndTerms() {
        RecurringInvoice model = saveModel("Loyer bureau", Frequency.MONTHLY, TODAY);

        assertThat(service.generateDue()).isEqualTo(1);
        assertThat(service.generateDue()).isZero();

        assertThat(drafts()).singleElement().satisfies(draft -> {
            assertThat(draft.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(draft.getIssueDate()).isEqualTo(TODAY);
            assertThat(draft.getDueDate()).isEqualTo(TODAY.plusDays(30));
            assertThat(draft.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(draft.getNotes()).isEqualTo("Virement au compte habituel");
            assertThat(draft.getLines()).singleElement().satisfies(line -> {
                assertThat(line.getDescription()).isEqualTo("Loyer bureau");
                assertThat(line.getVatRate()).isEqualByComparingTo("19");
            });
            assertThat(draft.getTotalAmount()).isEqualByComparingTo("59500.00");
        });
        assertThat(model.getNextDate()).isEqualTo(TODAY.plusMonths(1));
        assertThat(model.getLastInvoice()).isNotNull();
        assertThat(model.getLastError()).isNull();
    }

    @Test
    void aPcThatWasOffCatchesUpEachMissedDateButNoMoreThanTwelveAtOnce() {
        saveModel("Maintenance", Frequency.MONTHLY, TODAY.minusMonths(2));
        assertThat(service.generateDue()).isEqualTo(3);
        assertThat(drafts()).extracting(Invoice::getIssueDate)
            .containsExactlyInAnyOrder(TODAY.minusMonths(2), TODAY.minusMonths(2).plusMonths(1), TODAY.minusMonths(2).plusMonths(2));

        RecurringInvoice old = saveModel("Abonnement", Frequency.MONTHLY, TODAY.minusMonths(20));
        assertThat(service.generateDue()).isEqualTo(RecurringInvoiceService.MAX_CATCH_UP);
        assertThat(old.getOccurrences()).isEqualTo(RecurringInvoiceService.MAX_CATCH_UP);
    }

    @Test
    void aPausedOrFinishedModelPreparesNothing() {
        RecurringInvoice paused = saveModel("Loyer", Frequency.MONTHLY, TODAY);
        service.setActive(paused.getId(), false);
        RecurringInvoiceForm ended = RecurringInvoiceForm.from(saveModel("Ancien contrat", Frequency.YEARLY, TODAY.minusYears(1)));
        ended.setEndDate(TODAY.minusYears(1));
        service.save(ended);

        assertThat(service.generateDue()).isEqualTo(1);
        assertThat(drafts()).extracting(Invoice::getIssueDate).containsExactly(TODAY.minusYears(1));
        assertThat(repository.findById(ended.getId()).orElseThrow().isFinished()).isTrue();
    }

    @Test
    void aModelThatFailsKeepsItsErrorAndDoesNotStopTheOthers() {
        RecurringInvoice broken = saveModel("Taux supprimé", Frequency.MONTHLY, TODAY);
        // A rate that is no longer allowed (the configuration changed since the model was saved)
        broken.replaceLines(List.of(new RecurringInvoiceLine(broken, 1, "Ancien taux", null, BigDecimal.ONE, new BigDecimal("100.00"),
            new BigDecimal("7"))));
        repository.saveAndFlush(broken);
        RecurringInvoice fine = saveModel("Loyer", Frequency.MONTHLY, TODAY);

        assertThat(service.generateDue()).isEqualTo(1);
        assertThat(broken.getLastError()).contains("TVA");
        assertThat(broken.getNextDate()).isEqualTo(TODAY);
        assertThat(fine.getNextDate()).isEqualTo(TODAY.plusMonths(1));
    }

    @Test
    void aModelIsPreparedFromAnInvoice() {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 9, 1));
        form.setDueDate(LocalDate.of(2026, 9, 16));
        form.setPaymentMethod(PaymentMethod.CHEQUE);
        form.setLines(List.of(line("Maintenance site web", "12000.00", "9")));
        Invoice invoice = invoiceService.saveDraft(form);

        RecurringInvoiceForm model = service.formFromInvoice(invoice.getId());

        assertThat(model.getClientId()).isEqualTo(client.getId());
        assertThat(model.getNextDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(model.getDueDays()).isEqualTo(15);
        assertThat(model.getPaymentMethod()).isEqualTo(PaymentMethod.CHEQUE);
        assertThat(model.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getId()).isNull();
            assertThat(line.getDescription()).isEqualTo("Maintenance site web");
        });
    }

    @Test
    @WithMockUser(authorities = {"VIEW_INVOICES", "CREATE_INVOICE"})
    void thePagesCreateAModelAndItsFirstDraftAtOnce() throws Exception {
        mockMvc.perform(get("/recurring/new")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Nouvelle facture récurrente")));
        mockMvc.perform(post("/recurring/save").with(csrf())
                .param("name", "Loyer bureau").param("clientId", client.getId().toString()).param("frequency", "MONTHLY")
                .param("nextDate", TODAY.toString()).param("lines[0].description", "Loyer").param("lines[0].quantity", "1")
                .param("lines[0].unitPrice", "40000").param("lines[0].vatRate", "19"))
            .andExpect(redirectedUrl("/recurring"))
            .andExpect(flash().attribute("success", containsString("1 facture(s) brouillon préparée(s)")));
        assertThat(drafts()).hasSize(1);

        mockMvc.perform(get("/recurring")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Loyer bureau")))
            .andExpect(content().string(containsString("Mensuelle")));
        mockMvc.perform(get("/recurring").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("Monthly")));

        // An end before the next date is refused next to the field
        mockMvc.perform(post("/recurring/save").with(csrf())
                .param("name", "Contrat").param("clientId", client.getId().toString()).param("frequency", "MONTHLY")
                .param("nextDate", "2026-12-01").param("endDate", "2026-11-01").param("lines[0].description", "Loyer")
                .param("lines[0].quantity", "1").param("lines[0].unitPrice", "40000").param("lines[0].vatRate", "19"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("recurring", "endDate"));
    }

    @Test
    void anInvoiceCanBeMadeRecurring() throws Exception {
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(2026, 9, 1));
        form.setLines(List.of(line("Maintenance site web", "12000.00", "9")));
        Invoice invoice = invoiceService.issue(invoiceService.saveDraft(form).getId());
        MockHttpSession session = administrator();
        mockMvc.perform(get("/invoices/{id}", invoice.getId()).session(session))
            .andExpect(content().string(containsString("/recurring/new?from=" + invoice.getId())));
        mockMvc.perform(get("/recurring/new").session(session).param("from", invoice.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Maintenance site web")))
            .andExpect(content().string(containsString("2026-10-01")));
    }

    @Test
    @WithMockUser(authorities = "VIEW_INVOICES")
    void managingRecurringInvoicesNeedsTheRightToCreateInvoices() throws Exception {
        mockMvc.perform(get("/recurring")).andExpect(status().isForbidden());
        mockMvc.perform(post("/recurring/run").with(csrf())).andExpect(status().isForbidden());
    }
}
