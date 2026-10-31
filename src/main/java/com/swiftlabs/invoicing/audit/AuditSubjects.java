package com.swiftlabs.invoicing.audit;

import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.company.CompanySchemas;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

/**
 * What a page or an action is about, for the activity journal: the invoice FA-2026-0001, the client "SARL X"... Read
 * before the action runs, so that a deletion still names what it deleted. From the {id} of the address (or the id
 * sent by a form that saves an existing one), the {name} of a backup file, or the company switched to.
 */
@Component
public class AuditSubjects {

    private static final Logger log = LoggerFactory.getLogger(AuditSubjects.class);

    /** {@code type}: a document kind (INVOICE, QUOTE...), with "_DRAFT" before it has a number; null for a plain name. */
    public record Subject(String type, String label) {
    }

    private final JdbcTemplate jdbcTemplate;

    public AuditSubjects(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Null when the request is about nothing in particular (a list, a new form) or the thing does not exist. */
    public Subject of(HttpServletRequest request, HandlerMethod method) {
        try {
            String controller = method.getBeanType().getSimpleName();
            @SuppressWarnings("unchecked")
            Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (variables != null && variables.get("name") != null) {
                return new Subject(null, variables.get("name"));
            }
            Long id = number(variables != null && variables.get("id") != null ? variables.get("id")
                : "CompanyController".equals(controller) ? request.getParameter("companyId") : request.getParameter("id"));
            if (id == null) {
                return null;
            }
            // The company's tables, named with its schema: plain JDBC is not pointed at the company as Hibernate is
            String company = CompanyContext.currentSchema();
            boolean inCompany = !CompanySchemas.SHARED.equals(company);
            return switch (controller) {
                case "InvoiceController", "InvoiceMailController" -> inCompany ? document(company, id) : null;
                case "ClientController", "StatementController" ->
                    inCompany ? named("SELECT name FROM " + company + ".client WHERE id = ?", id) : null;
                case "CatalogController" -> inCompany ? named("SELECT name FROM " + company + ".catalog_item WHERE id = ?", id) : null;
                case "RecurringInvoiceController" ->
                    inCompany ? named("SELECT name FROM " + company + ".recurring_invoice WHERE id = ?", id) : null;
                case "UserController" -> named("SELECT username FROM public.app_user WHERE id = ?", id);
                case "ProfileController" -> named("SELECT name FROM public.profile WHERE id = ?", id);
                case "PrivilegeController" -> named("SELECT name FROM public.privilege WHERE id = ?", id);
                case "CompanyController", "CompanySettingsController" -> named("SELECT name FROM public.company WHERE id = ?", id);
                default -> null;
            };
        } catch (RuntimeException ex) {
            log.debug("Activity journal: subject of {} not read: {}", request.getRequestURI(), ex.toString());
            return null;
        }
    }

    private Subject document(String company, Long id) {
        List<Subject> found = jdbcTemplate.query("SELECT kind, invoice_number FROM " + company + ".invoice WHERE id = ?",
            (rs, row) -> rs.getString(2) == null ? new Subject(rs.getString(1) + "_DRAFT", id.toString())
                : new Subject(rs.getString(1), rs.getString(2)), id);
        return found.isEmpty() ? null : found.get(0);
    }

    private Subject named(String sql, Long id) {
        List<String> found = jdbcTemplate.queryForList(sql, String.class, id);
        return found.isEmpty() ? null : new Subject(null, found.get(0));
    }

    private static Long number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
