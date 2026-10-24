package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.domain.Client;
import com.swiftlabs.invoicing.domain.Invoice;
import com.swiftlabs.invoicing.domain.InvoiceLine;
import com.swiftlabs.invoicing.domain.InvoiceStatus;
import com.swiftlabs.invoicing.domain.PaymentMethod;
import com.swiftlabs.invoicing.dto.InvoiceForm;
import com.swiftlabs.invoicing.dto.InvoiceLineForm;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.repository.InvoiceLineRepository;
import com.swiftlabs.invoicing.service.ClientService;
import com.swiftlabs.invoicing.service.InvoiceService;
import com.swiftlabs.invoicing.service.IssuerService;
import com.swiftlabs.invoicing.service.PdfService;
import com.swiftlabs.invoicing.util.AmountUtils;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final IssuerService issuerService;
    private final PdfService pdfService;
    private final InvoiceLineRepository invoiceLineRepository;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService, IssuerService issuerService, PdfService pdfService, InvoiceLineRepository invoiceLineRepository) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.issuerService = issuerService;
        this.pdfService = pdfService;
        this.invoiceLineRepository = invoiceLineRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String list(@RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "year", required = false) Integer year,
                       @RequestParam(name = "client", required = false) Long clientId,
                       @RequestParam(name = "search", required = false) String search,
                       Model model) {
        List<Invoice> invoices = invoiceService.list(status, year, clientId, search);
        model.addAttribute("invoices", invoices);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", InvoiceStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedClient", clientId);
        model.addAttribute("search", search);

        BigDecimal total = BigDecimal.ZERO;
        for (Invoice inv : invoices) {
            if (inv.getTotalAmount() != null) {
                total = total.add(inv.getTotalAmount());
            }
        }
        InvoiceService.DashboardStats stats = new InvoiceService.DashboardStats();
        stats.setTotalAmount(total);
        model.addAttribute("stats", stats);
        return "invoices/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String newForm(Model model) {
        InvoiceForm form = new InvoiceForm();
        form.addEmptyLine();
        model.addAttribute("invoice", form);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "invoices/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findById(id);
            if (!invoice.isDraft()) {
                redirectAttributes.addFlashAttribute("error", "Seules les factures brouillon peuvent être modifiées.");
                return "redirect:/invoices/" + id;
            }
            loadLines(invoice);
            model.addAttribute("invoice", toForm(invoice));
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("paymentMethods", PaymentMethod.values());
            return "invoices/form";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/invoices";
        }
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String save(@ModelAttribute InvoiceForm form, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = toEntity(form);
            List<InvoiceLineForm> lineForms = form.getLines() != null ? form.getLines() : new ArrayList<InvoiceLineForm>();
            Invoice saved = invoiceService.saveDraft(invoice, lineForms);
            redirectAttributes.addFlashAttribute("success", "Facture brouillon enregistrée.");
            return "redirect:/invoices/" + saved.getId();
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("invoice", form);
            return "redirect:/invoices/new";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String view(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findById(id);
            loadLines(invoice);
            model.addAttribute("invoice", invoice);
            model.addAttribute("vatMention", issuerService.getVatMention());
            return "invoices/view";
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
            return "redirect:/invoices/" + id;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/invoices/" + id;
        }
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
    public void pdf(@PathVariable Long id, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        Invoice invoice = invoiceService.findById(id);
        byte[] bytes = pdfService.renderInvoicePdf(invoice);
        String fileName = (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "facture") + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    @ModelAttribute("amountUtils")
    public AmountUtils amountUtils() {
        return new AmountUtils();
    }

    private InvoiceForm toForm(Invoice invoice) {
        InvoiceForm form = new InvoiceForm();
        form.setId(invoice.getId());
        form.setClientId(invoice.getClient() != null ? invoice.getClient().getId() : null);
        form.setIssueDate(invoice.getIssueDate());
        form.setDueDate(invoice.getDueDate());
        form.setPaymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null);
        form.setNotes(invoice.getNotes());
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            form.addEmptyLine();
        } else {
            for (InvoiceLine line : lines) {
                InvoiceLineForm lf = new InvoiceLineForm();
                lf.setId(line.getId());
                lf.setDescription(line.getDescription());
                lf.setUnit(line.getUnit());
                lf.setQuantity(line.getQuantity());
                lf.setUnitPrice(line.getUnitPrice());
                lf.setLineTotal(line.getLineTotal());
                form.getLines().add(lf);
            }
        }
        return form;
    }

    private void loadLines(Invoice invoice) {
        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderByPositionAsc(invoice.getId());
        invoice.setLines(lines);
    }

    private Invoice toEntity(InvoiceForm form) {
        Invoice invoice = new Invoice();
        if (form.getId() != null) {
            invoice.setId(form.getId());
        }
        invoice.setIssueDate(form.getIssueDate());
        invoice.setDueDate(form.getDueDate());
        invoice.setPaymentMethod(form.getPaymentMethod() != null ? PaymentMethod.valueOf(form.getPaymentMethod()) : null);
        invoice.setNotes(form.getNotes());
        Client client = new Client();
        if (form.getClientId() != null) {
            client.setId(form.getClientId());
        }
        invoice.setClient(client);
        return invoice;
    }
}
