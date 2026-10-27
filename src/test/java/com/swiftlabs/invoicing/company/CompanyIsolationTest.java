package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;
import com.swiftlabs.invoicing.invoice.Invoice;
import com.swiftlabs.invoicing.invoice.InvoiceForm;
import com.swiftlabs.invoicing.invoice.InvoiceLineForm;
import com.swiftlabs.invoicing.invoice.InvoiceService;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each company has its own schema: its own details, clients, invoices and numbering, never seen from another company.
 * Not transactional: each step opens its own session in the company it runs in, as a request does.
 */
@SpringBootTest
class CompanyIsolationTest {

    private static final int YEAR = 2033;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdCompanies = new ArrayList<>();
    private final List<Long> firstCompanyClients = new ArrayList<>();

    private <T> T inCompany(String schema, Supplier<T> work) {
        return CompanyContext.runAs(schema, () -> new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }

    private Client client(String name) {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName(name);
        return clientRepository.save(client);
    }

    private Invoice issuedInvoice(Client client) {
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(LocalDate.of(YEAR, 3, 1));
        form.setLines(List.of(line));
        return invoiceService.issue(invoiceService.saveDraft(form).getId());
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdCompanies) {
            jdbc.execute("DROP SCHEMA " + CompanySchemas.of(id) + " CASCADE");
            jdbc.update("DELETE FROM public.company WHERE id = ?", id);
        }
        for (Long id : firstCompanyClients) {
            String first = CompanySchemas.FIRST;
            jdbc.update("DELETE FROM " + first + ".invoice_event WHERE invoice_id IN (SELECT id FROM " + first + ".invoice WHERE client_id = ?)", id);
            jdbc.update("DELETE FROM " + first + ".invoice_line WHERE invoice_id IN (SELECT id FROM " + first + ".invoice WHERE client_id = ?)", id);
            jdbc.update("DELETE FROM " + first + ".invoice WHERE client_id = ?", id);
            jdbc.update("DELETE FROM " + first + ".client WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM " + CompanySchemas.FIRST + ".invoice_sequence WHERE invoice_year = ?", YEAR);
    }

    @Test
    void aNewCompanyHasItsOwnEmptySchema() {
        Company second = companyService.create("Deuxième société");
        createdCompanies.add(second.getId());

        assertThat(second.getSchemaName()).isEqualTo("company_" + second.getId());
        assertThat(inCompany(second.getSchemaName(), () -> clientRepository.count())).isZero();
        // Its details start as the placeholder its setup replaces
        assertThat(inCompany(second.getSchemaName(), () -> issuerService.getIssuer().getFullName())).isEqualTo("Nom Prénom");
        assertThat(companyService.findActive()).extracting(Company::getSchemaName).contains(CompanySchemas.FIRST, second.getSchemaName());
    }

    @Test
    void clientsAndInvoicesNeverCrossCompanies() {
        Company second = companyService.create("Deuxième société");
        createdCompanies.add(second.getId());
        String other = second.getSchemaName();

        Client mine = inCompany(CompanySchemas.FIRST, () -> client("Client de la première"));
        firstCompanyClients.add(mine.getId());
        Client theirs = inCompany(other, () -> client("Client de la deuxième"));
        long firstCount = inCompany(CompanySchemas.FIRST, () -> clientRepository.count());

        assertThat(inCompany(other, () -> clientRepository.findAll())).extracting(Client::getName).containsExactly("Client de la deuxième");
        assertThat(inCompany(CompanySchemas.FIRST, () -> clientRepository.findAll())).extracting(Client::getName)
            .contains("Client de la première").doesNotContain("Client de la deuxième");
        assertThat(firstCount).isGreaterThanOrEqualTo(1);

        // Each company numbers its own invoices from 1
        Invoice first = inCompany(CompanySchemas.FIRST, () -> issuedInvoice(mine));
        Invoice second2 = inCompany(other, () -> issuedInvoice(theirs));
        assertThat(first.getInvoiceNumber()).isEqualTo("FA-" + YEAR + "-0001");
        assertThat(second2.getInvoiceNumber()).isEqualTo("FA-" + YEAR + "-0001");
        assertThat(inCompany(other, () -> invoiceService.findByNumber("FA-" + YEAR + "-0001").getClientName()))
            .isEqualTo("Client de la deuxième");
    }

    @Test
    void anUnknownSchemaNameIsRefused() {
        assertThatThrownBy(() -> CompanyContext.runAs("public; DROP TABLE app_user", () -> 1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
