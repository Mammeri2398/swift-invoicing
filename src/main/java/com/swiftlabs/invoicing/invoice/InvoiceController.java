package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.BusinessException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final InvoicePdfService pdfService;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService,
                             InvoicePdfService pdfService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.pdfService = pdfService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String list(@RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "year", required = false) Integer year,
                       @RequestParam(name = "client", required = false) Long clientId,
                       @RequestParam(name = "search", required = false) String search,
                       Model model) {
        List<Invoice> invoices = invoiceService.list(status, year, clientId, search);
        BigDecimal total = invoices.stream()
            .map(Invoice::getTotalAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("invoices", invoices);
        model.addAttribute("total", total);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", InvoiceStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedClient", clientId);
        model.addAttribute("search", search);
        return "invoice/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String newForm(Model model) {
        // After a failed save the submitted form comes back as a flash attribute
        if (!model.containsAttribute("invoice")) {
            InvoiceForm form = new InvoiceForm();
            form.addEmptyLine();
            model.addAttribute("invoice", form);
        }
        addFormChoices(model);
        return "invoice/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findWithLines(id);
            if (!invoice.isDraft()) {
                redirectAttributes.addFlashAttribute("error", "Seules les factures brouillon peuvent être modifiées.");
                return "redirect:/invoices/" + id;
            }
            if (!model.containsAttribute("invoice")) {
                model.addAttribute("invoice", InvoiceForm.from(invoice));
            }
            addFormChoices(model);
            return "invoice/form";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/invoices";
        }
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String save(@ModelAttribute InvoiceForm form, RedirectAttributes redirectAttributes) {
        try {
            Invoice saved = invoiceService.saveDraft(form);
            redirectAttributes.addFlashAttribute("success", "Facture brouillon enregistrée.");
            return "redirect:/invoices/" + saved.getId();
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("invoice", form);
            return form.getId() == null ? "redirect:/invoices/new" : "redirect:/invoices/" + form.getId() + "/edit";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String view(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("invoice", invoiceService.findWithLines(id));
            return "invoice/view";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/invoices";
        }
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

    private void addFormChoices(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("paymentMethods", PaymentMethod.values());
    }
}
