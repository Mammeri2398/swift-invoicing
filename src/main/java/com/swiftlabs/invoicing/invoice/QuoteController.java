package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.PageLinks;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * The "Devis" page: quotes and pro formas. They are created, issued and invoiced like invoices
 * (InvoiceController); only the list lives here, apart from the invoice register.
 */
@Controller
@RequestMapping("/quotes")
@PreAuthorize("hasAuthority('VIEW_INVOICES')")
public class QuoteController {

    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("id"));

    private final InvoiceService invoiceService;
    private final ClientService clientService;

    public QuoteController(InvoiceService invoiceService, ClientService clientService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
    }

    @GetMapping
    public String list(@ModelAttribute("filter") InvoiceFilter filter, BindingResult ignoredFilterErrors,
                       @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request, Model model) {
        filter.setKinds(DocumentKind.OFFERS);
        Page<Invoice> page = invoiceService.search(filter,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
        model.addAttribute("page", page);
        model.addAttribute("pageLinks", new PageLinks(request));
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", List.of(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED, InvoiceStatus.ACCEPTED,
            InvoiceStatus.REFUSED, InvoiceStatus.CANCELLED));
        return "invoice/quotes";
    }
}
