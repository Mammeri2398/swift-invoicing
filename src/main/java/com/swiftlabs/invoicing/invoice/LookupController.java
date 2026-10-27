package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.company.Company;
import com.swiftlabs.invoicing.company.CompanyService;
import com.swiftlabs.invoicing.company.CompanyWork;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Public page to check that an invoice number exists (anyone holding an invoice can verify it).
 * Shows only an {@link InvoiceVerification}; requests are rate limited per IP (see RateLimitFilter).
 */
@Controller
public class LookupController {

    private final InvoiceService invoiceService;
    private final CompanyService companyService;
    private final CompanyWork companyWork;

    public LookupController(InvoiceService invoiceService, CompanyService companyService, CompanyWork companyWork) {
        this.invoiceService = invoiceService;
        this.companyService = companyService;
        this.companyWork = companyWork;
    }

    /** The number is looked for in every active company: each one numbers its documents from 1. */
    @GetMapping(Endpoints.INVOICE_LOOKUP)
    public String lookup(@RequestParam(name = "number", required = false) String number,
                         Authentication authentication, Model model) {
        if (number != null && !number.isBlank()) {
            String wanted = number.trim();
            Long visitorCompany = authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getCompanyId() : null;
            List<InvoiceVerification> found = new ArrayList<>();
            for (Company company : companyService.findActive()) {
                InvoiceVerification verification = companyWork.inCompany(company.getSchemaName(), () -> {
                    Invoice invoice = invoiceService.findByNumber(wanted);
                    // Link to the full invoice only for users allowed to open it, in its company
                    return invoice == null ? null
                        : InvoiceVerification.of(invoice, company.getId().equals(visitorCompany) && canViewInvoices(authentication));
                });
                if (verification != null) {
                    found.add(verification);
                }
            }
            model.addAttribute(CommonCodes.Attributes.NUMBER, wanted);
            model.addAttribute(CommonCodes.Attributes.VERIFICATIONS, found);
        }
        return CommonCodes.Views.INVOICE_LOOKUP;
    }

    private static boolean canViewInvoices(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> Privileges.VIEW_INVOICES.equals(authority.getAuthority()));
    }
}
