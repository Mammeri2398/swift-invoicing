package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceRepository;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.issuer.IssuerService;
import com.swiftlabs.invoicing.recurring.Frequency;
import com.swiftlabs.invoicing.recurring.RecurringInvoiceForm;
import com.swiftlabs.invoicing.recurring.RecurringInvoiceScheduler;
import com.swiftlabs.invoicing.recurring.RecurringInvoiceService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What runs without a signed-in user: anonymous pages, the hourly jobs. In the application nothing falls back to a
 * company (the tests' default company is switched off here), so each of them names the company it works in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyBackgroundTest {

    private static final int YEAR = 2014;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyWork companyWork;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private RecurringInvoiceService recurringService;

    @Autowired
    private JdbcTemplate jdbc;

    private Company second;
    private final List<Long> firstCompanyClients = new ArrayList<>();

    @BeforeEach
    void productionMode() {
        second = companyService.create("Seconde");
        CompanyContext.setFallback("");
    }

    @AfterEach
    void cleanUp() {
        CompanyContext.setFallback(CompanySchemas.FIRST);
        jdbc.execute("DROP SCHEMA " + second.getSchemaName() + " CASCADE");
        jdbc.update("DELETE FROM public.company WHERE id = ?", second.getId());
        String first = CompanySchemas.FIRST;
        for (Long id : firstCompanyClients) {
            jdbc.update("DELETE FROM " + first + ".recurring_invoice WHERE client_id = ?", id);
            jdbc.update("DELETE FROM " + first + ".invoice_event WHERE invoice_id IN (SELECT id FROM " + first + ".invoice WHERE client_id = ?)", id);
            jdbc.update("DELETE FROM " + first + ".invoice_line WHERE invoice_id IN (SELECT id FROM " + first + ".invoice WHERE client_id = ?)", id);
            jdbc.update("DELETE FROM " + first + ".invoice WHERE client_id = ?", id);
            jdbc.update("DELETE FROM " + first + ".client WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM " + first + ".invoice_sequence WHERE invoice_year = ?", YEAR);
    }

    private Client client(String schema, String name) {
        Client client = companyWork.inCompany(schema, () -> {
            Client c = new Client();
            c.setType(ClientType.PARTICULIER);
            c.setName(name);
            return clientRepository.save(c);
        });
        if (CompanySchemas.FIRST.equals(schema)) {
            firstCompanyClients.add(client.getId());
        }
        return client;
    }

    private static InvoiceLineForm line() {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        return line;
    }

    private Invoice issued(String schema, Client client) {
        return companyWork.inCompany(schema, () -> {
            InvoiceForm form = new InvoiceForm();
            form.setClientId(client.getId());
            form.setIssueDate(LocalDate.of(YEAR, 1, 10));
            form.setLines(List.of(line()));
            return invoiceService.issue(invoiceService.saveDraft(form).getId());
        });
    }

    @Test
    void withoutACompanyNothingReachesACompanysTables() throws Exception {
        assertThatThrownBy(() -> clientRepository.count()).isInstanceOf(RuntimeException.class);
        // Anonymous pages still work
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/invoices/lookup").param("number", "FA-1999-0001"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Aucune facture ne porte le numéro")));
    }

    @Test
    void theVerificationPageFindsANumberInEveryCompany() throws Exception {
        companyWork.inCompany(second.getSchemaName(), () -> {
            var issuer = issuerService.getIssuer();
            issuer.setFullName("Seconde SARL");
        });
        Invoice mine = issued(CompanySchemas.FIRST, client(CompanySchemas.FIRST, "Client un"));
        Invoice theirs = issued(second.getSchemaName(), client(second.getSchemaName(), "Client deux"));
        assertThat(mine.getInvoiceNumber()).isEqualTo(theirs.getInvoiceNumber());

        mockMvc.perform(get("/invoices/lookup").param("number", mine.getInvoiceNumber()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Seconde SARL")))
            .andExpect(content().string(containsString(mine.getIssuerFullName())))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Client deux"))));
    }

    @Test
    void theRecurringInvoicesOfEveryCompanyArePrepared() {
        for (String schema : List.of(CompanySchemas.FIRST, second.getSchemaName())) {
            Client client = client(schema, "Locataire " + schema);
            companyWork.inCompany(schema, () -> {
                RecurringInvoiceForm form = new RecurringInvoiceForm();
                form.setName("Loyer");
                form.setClientId(client.getId());
                form.setFrequency(Frequency.MONTHLY);
                form.setNextDate(LocalDate.now());
                form.setLines(List.of(line()));
                return recurringService.save(form);
            });
        }

        new RecurringInvoiceScheduler(recurringService, companyService).prepareDueInvoices();

        for (String schema : List.of(CompanySchemas.FIRST, second.getSchemaName())) {
            assertThat(companyWork.inCompany(schema, () -> invoiceRepository.findAll().stream()
                .filter(invoice -> invoice.getClient().getName().equals("Locataire " + schema)).count()))
                .as(schema).isEqualTo(1L);
        }
    }
}
