package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.catalog.CatalogItem;
import com.swiftlabs.invoicing.catalog.CatalogItemForm;
import com.swiftlabs.invoicing.catalog.CatalogService;
import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.invoice.PaymentForm;
import com.swiftlabs.invoicing.invoice.PaymentMethod;
import com.swiftlabs.invoicing.invoice.PaymentService;
import com.swiftlabs.invoicing.recurring.Frequency;
import com.swiftlabs.invoicing.recurring.RecurringInvoice;
import com.swiftlabs.invoicing.recurring.RecurringInvoiceForm;
import com.swiftlabs.invoicing.recurring.RecurringInvoiceService;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The whole application seen from the second company: every page, search, export and PDF, including the addresses
 * of the first company's own documents. Nothing of the first company may show. Not transactional: each request
 * opens its own session in the signed-in user's company, as in the application.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyIsolationSweepTest {

    /** Written in every document of the first company: it must never appear in the second. */
    private static final String FIRST_MARK = "ZZPREMIERE";
    private static final String SECOND_MARK = "ZZSECONDE";
    private static final int YEAR = 2019;
    private static final String WHOLE_PERIOD = "from=2000-01-01&to=2100-12-31";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private CompanyWork companyWork;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private RecurringInvoiceService recurringService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Company second;
    private Long userId;
    private Client firstClient;
    private Invoice firstInvoice;
    private CatalogItem firstItem;
    private RecurringInvoice firstRecurring;
    private Invoice secondInvoice;

    private static InvoiceLineForm line(String description) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription(description);
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("1000.00"));
        line.setVatRate(BigDecimal.ZERO);
        return line;
    }

    /** A client with an issued and paid invoice, in the company of {@code schema}, every text carrying {@code mark}. */
    private Invoice invoiceIn(String schema, Client client, String mark) {
        return companyWork.inCompany(schema, () -> {
            InvoiceForm form = new InvoiceForm();
            form.setClientId(client.getId());
            form.setIssueDate(LocalDate.of(YEAR, 2, 1));
            form.setNotes("Note " + mark);
            form.setLines(List.of(line("Ligne " + mark)));
            Invoice invoice = invoiceService.issue(invoiceService.saveDraft(form).getId());
            paymentService.record(invoice.getId(), new PaymentForm(new BigDecimal("400.00"), LocalDate.of(YEAR, 2, 5),
                PaymentMethod.CHEQUE, "CHQ " + mark));
            return invoice;
        });
    }

    private Client clientIn(String schema, String mark) {
        return companyWork.inCompany(schema, () -> {
            Client client = new Client();
            client.setType(ClientType.PARTICULIER);
            client.setName("Client " + mark);
            return clientRepository.save(client);
        });
    }

    @BeforeEach
    void twoCompaniesWithTheirOwnData() {
        second = companyService.create("Seconde balayée");
        String first = CompanySchemas.FIRST;

        firstClient = clientIn(first, FIRST_MARK);
        firstInvoice = invoiceIn(first, firstClient, FIRST_MARK);
        firstItem = companyWork.inCompany(first, () -> {
            CatalogItemForm item = new CatalogItemForm();
            item.setName("Article " + FIRST_MARK);
            item.setUnitPrice(new BigDecimal("500.00"));
            item.setVatRate(BigDecimal.ZERO);
            return catalogService.save(item);
        });
        firstRecurring = companyWork.inCompany(first, () -> {
            RecurringInvoiceForm form = new RecurringInvoiceForm();
            form.setName("Contrat " + FIRST_MARK);
            form.setClientId(firstClient.getId());
            form.setFrequency(Frequency.MONTHLY);
            form.setNextDate(LocalDate.now().plusYears(5));
            form.setLines(List.of(line("Abonnement " + FIRST_MARK)));
            return recurringService.save(form);
        });

        secondInvoice = invoiceIn(second.getSchemaName(), clientIn(second.getSchemaName(), SECOND_MARK), SECOND_MARK);

        userId = new TransactionTemplate(transactionManager).execute(status -> {
            User user = new User();
            user.setUsername("balayeur");
            user.setEmail("balayeur@example.test");
            user.changePassword(passwordEncoder.encode("Secret123"), LocalDateTime.now());
            user = userRepository.save(user);
            accessService.grant(user, CompanySchemas.FIRST_ID, profileRepository.findByName("Administrateur"));
            accessService.grant(user, second.getId(), profileRepository.findByName("Administrateur"));
            return user.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM public.app_user WHERE id = ?", userId);
        jdbc.execute("DROP SCHEMA " + second.getSchemaName() + " CASCADE");
        jdbc.update("DELETE FROM public.company WHERE id = ?", second.getId());
        String first = CompanySchemas.FIRST;
        jdbc.update("DELETE FROM " + first + ".recurring_invoice WHERE id = ?", firstRecurring.getId());
        jdbc.update("DELETE FROM " + first + ".catalog_item WHERE id = ?", firstItem.getId());
        for (String table : List.of("invoice_event", "invoice_payment", "invoice_line")) {
            jdbc.update("DELETE FROM " + first + "." + table + " WHERE invoice_id = ?", firstInvoice.getId());
        }
        jdbc.update("DELETE FROM " + first + ".invoice WHERE id = ?", firstInvoice.getId());
        jdbc.update("DELETE FROM " + first + ".client WHERE id = ?", firstClient.getId());
        jdbc.update("DELETE FROM " + first + ".invoice_sequence WHERE invoice_year = ?", YEAR);
    }

    /** The response as text: HTML and CSV as they are, PDF and Excel files read back. */
    private static String text(MockHttpServletResponse response) throws Exception {
        byte[] body = response.getContentAsByteArray();
        String type = String.valueOf(response.getContentType());
        if (type.contains("pdf")) {
            try (PDDocument pdf = PDDocument.load(body)) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        if (type.contains("spreadsheetml")) {
            StringBuilder cells = new StringBuilder();
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body))) {
                for (Sheet sheet : workbook) {
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            cells.append(cell).append(' ');
                        }
                    }
                }
            }
            return cells.toString();
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    @Test
    void nothingOfTheFirstCompanyShowsInTheSecond() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "balayeur").param("password", "Secret123"));
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()));

        List<String> pages = List.of(
            "/", "/dashboard", "/turnover", "/turnover?year=" + YEAR, "/vat?year=" + YEAR + "&month=2",
            "/vat/export?year=" + YEAR + "&month=2", "/accounting?" + WHOLE_PERIOD, "/accounting/export/csv?" + WHOLE_PERIOD,
            "/accounting/export/xlsx?" + WHOLE_PERIOD, "/invoices", // Part of the mark: it would find the first company's client and lines, and the page shows back only "PREMIERE"
            "/invoices?search=PREMIERE", "/invoices?search=Client",
            "/invoices/export/csv", "/invoices/export/xlsx", "/quotes", "/clients", "/catalog", "/recurring", "/invoices/new",
            "/recurring/new", "/settings/issuer", "/settings/mail", "/settings/backups", "/help", "/users", "/account",
            // The first company's own documents, by their addresses
            "/invoices/" + firstInvoice.getId(), "/invoices/" + firstInvoice.getId() + "/pdf",
            "/invoices/" + firstInvoice.getId() + "/edit", "/invoices/" + firstInvoice.getId() + "/email",
            "/clients/" + firstClient.getId() + "/edit", "/clients/" + firstClient.getId() + "/statement",
            "/catalog/" + firstItem.getId() + "/edit", "/recurring/" + firstRecurring.getId() + "/edit",
            "/recurring/new?from=" + firstInvoice.getId());

        List<String> leaks = new ArrayList<>();
        for (String page : pages) {
            MockHttpServletResponse response = mockMvc.perform(get(page).session(session)).andReturn().getResponse();
            String text = text(response);
            if (text.contains(FIRST_MARK)) {
                leaks.add(page + " (" + response.getStatus() + ")");
            }
            assertThat(response.getStatus()).as(page).isLessThan(500);
        }
        assertThat(leaks).as("pages showing the first company's data").isEmpty();

        // The second company's own data does show
        assertThat(text(mockMvc.perform(get("/invoices/" + secondInvoice.getId()).session(session)).andReturn().getResponse()))
            .contains("Ligne " + SECOND_MARK);
        assertThat(text(mockMvc.perform(get("/accounting/export/csv?" + WHOLE_PERIOD).session(session)).andReturn().getResponse()))
            .contains(SECOND_MARK);
        // And the first company is still whole
        assertThat(companyWork.inCompany(CompanySchemas.FIRST, () -> invoiceService.findWithLines(firstInvoice.getId()).getNotes()))
            .isEqualTo("Note " + FIRST_MARK);
    }

    @Test
    void theFirstCompanysDocumentsCannotBeChangedFromTheSecond() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "balayeur").param("password", "Secret123"));
        mockMvc.perform(post("/companies/switch").session(session).with(csrf()).param("companyId", second.getId().toString()));
        String company = "_company";
        String secondId = second.getId().toString();

        // Actions on the first company's ids, sent from the second (its own form field, so not refused as another tab)
        mockMvc.perform(post("/invoices/" + firstInvoice.getId() + "/cancel").session(session).with(csrf()).param(company, secondId));
        mockMvc.perform(post("/clients/" + firstClient.getId() + "/delete").session(session).with(csrf()).param(company, secondId));
        mockMvc.perform(post("/catalog/" + firstItem.getId() + "/delete").session(session).with(csrf()).param(company, secondId));
        mockMvc.perform(post("/recurring/" + firstRecurring.getId() + "/delete").session(session).with(csrf()).param(company, secondId));

        companyWork.inCompany(CompanySchemas.FIRST, () -> {
            assertThat(invoiceService.findById(firstInvoice.getId()).getStatus().name()).isNotEqualTo("CANCELLED");
            assertThat(clientRepository.existsById(firstClient.getId())).isTrue();
            assertThat(catalogService.findAll()).extracting(CatalogItem::getName).contains("Article " + FIRST_MARK);
            assertThat(recurringService.findAll()).extracting(RecurringInvoice::getName).contains("Contrat " + FIRST_MARK);
        });
    }
}
