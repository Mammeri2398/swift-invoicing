package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.PageLinks;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    /** Newest first. Fixed: the "sort" request parameter is ignored (no sorting on arbitrary properties). */
    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("id"));

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final InvoicePdfService pdfService;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService,
                             InvoicePdfService pdfService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.pdfService = pdfService;
    }

    /**
     * Filtered, paginated list. An invalid filter value in the URL (e.g. an unknown status) is
     * ignored rather than failing the page: the binding errors are collected and not shown.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String list(@ModelAttribute("filter") InvoiceFilter filter, BindingResult ignoredFilterErrors,
                       @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request, Model model) {
        Page<Invoice> page = invoiceService.search(filter,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
        model.addAttribute("page", page);
        model.addAttribute("total", invoiceService.totalOf(filter));
        model.addAttribute("pageLinks", new PageLinks(request));
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", InvoiceStatus.values());
        return "invoice/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String newForm(Model model) {
        InvoiceForm form = new InvoiceForm();
        form.addEmptyLine();
        model.addAttribute("invoice", form);
        return formView(model);
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Invoice invoice = invoiceService.findWithLines(id);
        if (!invoice.isDraft()) {
            redirectAttributes.addFlashAttribute("error", "Seules les factures brouillon peuvent être modifiées.");
            return "redirect:/invoices/" + id;
        }
        model.addAttribute("invoice", InvoiceForm.from(invoice));
        return formView(model);
    }

    /** Invalid input is shown again with the errors next to each field (no redirect, nothing lost). */
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String save(@Valid @ModelAttribute("invoice") InvoiceForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                Invoice saved = invoiceService.saveDraft(form);
                redirectAttributes.addFlashAttribute("success", "Facture brouillon enregistrée.");
                return "redirect:/invoices/" + saved.getId();
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        if (form.getLines().isEmpty()) {
            form.addEmptyLine();
        }
        return formView(model);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", invoiceService.findWithLines(id));
        return "invoice/view";
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String issue(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice issued = invoiceService.issue(id);
            redirectAttributes.addFlashAttribute("success", "Facture émise : " + issued.getInvoiceNumber());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String pay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.pay(id);
            redirectAttributes.addFlashAttribute("success", "Facture marquée payée.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.cancel(id);
            redirectAttributes.addFlashAttribute("success", "Facture annulée.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_INVOICE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Facture brouillon supprimée.");
            return "redirect:/invoices";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/invoices/" + id;
        }
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public void pdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Invoice invoice = invoiceService.findWithLines(id);
        byte[] bytes = pdfService.render(invoice);
        String fileName = (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "facture") + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private String formView(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "invoice/form";
    }
}
