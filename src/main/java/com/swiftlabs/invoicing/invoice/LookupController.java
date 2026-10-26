package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Privileges;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Public page to check that an invoice number exists (anyone holding an invoice can verify it).
 * Shows only an {@link InvoiceVerification}; requests are rate limited per IP (see RateLimitFilter).
 */
@Controller
public class LookupController {

    private final InvoiceService invoiceService;

    public LookupController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping(Endpoints.INVOICE_LOOKUP)
    public String lookup(@RequestParam(name = "number", required = false) String number,
                         Authentication authentication, Model model) {
        if (number != null && !number.isBlank()) {
            Invoice invoice = invoiceService.findByNumber(number.trim());
            model.addAttribute(CommonCodes.Attributes.NUMBER, number.trim());
            model.addAttribute(CommonCodes.Attributes.VERIFICATION, invoice != null ? InvoiceVerification.of(invoice) : null);
            // Link to the full invoice only for users allowed to open it
            if (invoice != null && canViewInvoices(authentication)) {
                model.addAttribute(CommonCodes.Attributes.INVOICE_ID, invoice.getId());
            }
        }
        return CommonCodes.Views.INVOICE_LOOKUP;
    }

    private static boolean canViewInvoices(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> Privileges.VIEW_INVOICES.equals(authority.getAuthority()));
    }
}
