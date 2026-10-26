package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.PageLinks;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.IssuerService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    /** Newest first. Fixed: the "sort" request parameter is ignored (no sorting on arbitrary properties). */
    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("id"));

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final InvoicePdfService pdfService;
    private final InvoiceAudit audit;
    private final IssuerService issuerService;
    private final PaymentService paymentService;
    private final InvoiceProperties properties;
    private final Messages messages;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService,
                             InvoicePdfService pdfService, InvoiceAudit audit, IssuerService issuerService,
                             PaymentService paymentService, InvoiceProperties properties, Messages messages) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.pdfService = pdfService;
        this.audit = audit;
        this.issuerService = issuerService;
        this.paymentService = paymentService;
        this.properties = properties;
        this.messages = messages;
    }

    /**
     * Filtered, paginated list. An invalid filter value in the URL (e.g. an unknown status) is
     * ignored rather than failing the page: the binding errors are collected and not shown.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String list(@ModelAttribute("filter") InvoiceFilter filter, BindingResult ignoredFilterErrors,
                       @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request, Model model) {
        // The register: invoices and credit notes (quotes and pro formas are on their own page)
        filter.setKinds(DocumentKind.ACCOUNTING);
        Page<Invoice> page = invoiceService.search(filter,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
        model.addAttribute("page", page);
        model.addAttribute("total", invoiceService.totalOf(filter));
        model.addAttribute("pageLinks", new PageLinks(request));
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", List.of(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED, InvoiceStatus.PAID, InvoiceStatus.CANCELLED));
        return "invoice/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String newForm(@RequestParam(name = "kind", required = false) DocumentKind kind, Model model) {
        InvoiceForm form = new InvoiceForm();
        form.setKind(kind != null && !kind.isAccounting() ? kind : DocumentKind.INVOICE);
        form.addEmptyLine();
        model.addAttribute("invoice", form);
        return formView(form, model);
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Invoice invoice = invoiceService.findWithLines(id);
        if (!invoice.isDraft()) {
            redirectAttributes.addFlashAttribute("error", messages.get("invoice.error.notDraft"));
            return "redirect:/invoices/" + id;
        }
        model.addAttribute("invoice", InvoiceForm.from(invoice));
        return formView(InvoiceForm.from(invoice), model);
    }

    /** Invalid input is shown again with the errors next to each field (no redirect, nothing lost). */
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String save(@Valid @ModelAttribute("invoice") InvoiceForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                Invoice saved = invoiceService.saveDraft(form);
                redirectAttributes.addFlashAttribute("success", messages.get("invoice.flash.draftSaved." + saved.getKind().name()));
                return "redirect:/invoices/" + saved.getId();
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        if (form.getLines().isEmpty()) {
            form.addEmptyLine();
        }
        return formView(form, model);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_INVOICES')")
    public String view(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.findWithLines(id);
        model.addAttribute("invoice", invoice);
        // DocumentKind.getLabel() is the French label of the PDF: the page uses the reader's language
        model.addAttribute("docTitle", invoice.getInvoiceNumber() != null
            ? messages.get("invoice.view.title", messages.label(invoice.getKind()), invoice.getInvoiceNumber())
            : messages.get("invoice.view.draftTitle." + invoice.getKind().name()));
        model.addAttribute("issuer", IssuerDetails.of(invoice, issuerService.getIssuer()));
        if (invoice.isOffer()) {
            model.addAttribute("invoiceOf", invoiceService.invoiceOf(id));
        } else if (!invoice.isCreditNote()) {
            model.addAttribute("creditNotes", invoiceService.creditNotesOf(id));
            if (invoice.isIssuedOrPaid()) {
                BigDecimal remaining = invoiceService.remainingToCredit(invoice);
                model.addAttribute("remainingToCredit", remaining);
                model.addAttribute("fullyCredited", remaining.signum() <= 0);
                PaymentService.Summary payment = paymentService.summary(invoice);
                model.addAttribute("payment", payment);
                model.addAttribute("payments", paymentService.paymentsOf(id));
                model.addAttribute("overdue", invoice.getStatus() == InvoiceStatus.ISSUED && invoice.getDueDate() != null
                    && invoice.getDueDate().isBefore(LocalDate.now()) && payment.balance().signum() > 0);
                // The form proposes the whole balance, today, with the invoice's payment method
                model.addAttribute("paymentForm", new PaymentForm(payment.balance().max(BigDecimal.ZERO), LocalDate.now(),
                    invoice.getPaymentMethod(), null));
                model.addAttribute("paymentMethods", PaymentMethod.values());
            }
        }
        model.addAttribute("history", audit.history(id));
        model.addAttribute("navSection", invoice.isOffer() ? "quotes" : "invoices");
        return "invoice/view";
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String issue(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice issued = invoiceService.issue(id);
            redirectAttributes.addFlashAttribute("success",
                messages.get("invoice.flash.issued." + issued.getKind().name(), issued.getInvoiceNumber()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/invoices/" + id;
    }

    /** A payment typed on the invoice page (all or part of the balance). */
    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String recordPayment(@PathVariable Long id, @Valid @ModelAttribute("paymentForm") PaymentForm form, BindingResult result,
                                RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", messages.get("invoice.flash.paymentInvalid"));
            return "redirect:/invoices/" + id;
        }
        try {
            InvoicePayment payment = paymentService.record(id, form);
            Invoice invoice = payment.getInvoice();
            redirectAttributes.addFlashAttribute("success", invoice.getStatus() == InvoiceStatus.PAID
                ? messages.get("invoice.flash.paidInFull")
                : messages.get("invoice.flash.paymentRecorded", AmountUtils.format(payment.getAmount())));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/invoices/" + id;
    }

    /** Removes a payment typed by mistake. */
    @PostMapping("/{id}/payments/{paymentId}/delete")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String deletePayment(@PathVariable Long id, @PathVariable Long paymentId, RedirectAttributes redirectAttributes) {
        paymentService.delete(id, paymentId);
        redirectAttributes.addFlashAttribute("success", messages.get("invoice.flash.paymentDeleted"));
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String accept(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return quoteAnswer(id, true, redirectAttributes);
    }

    @PostMapping("/{id}/refuse")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String refuse(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return quoteAnswer(id, false, redirectAttributes);
    }

    private String quoteAnswer(Long id, boolean accepted, RedirectAttributes redirectAttributes) {
        try {
            Invoice quote = accepted ? invoiceService.accept(id) : invoiceService.refuse(id);
            redirectAttributes.addFlashAttribute("success",
                messages.get(accepted ? "invoice.flash.quoteAccepted" : "invoice.flash.quoteRefused", quote.getInvoiceNumber()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/invoices/" + id;
    }

    /** Turns a quote or a pro forma into a draft invoice, opened for a last check before issuing. */
    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String convert(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.convertToInvoice(id);
            if (!invoice.isDraft()) {
                redirectAttributes.addFlashAttribute("message", messages.get("invoice.flash.alreadyInvoiced", invoice.getInvoiceNumber()));
                return "redirect:/invoices/" + invoice.getId();
            }
            redirectAttributes.addFlashAttribute("message", messages.get("invoice.flash.converted"));
            return "redirect:/invoices/" + invoice.getId() + "/edit";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
            return "redirect:/invoices/" + id;
        }
    }

    /** Opens the credit note of an issued invoice: a draft with its lines, to reduce for a partial credit. */
    @PostMapping("/{id}/credit-note")
    @PreAuthorize("hasAuthority('CREATE_INVOICE')")
    public String creditNote(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice creditNote = invoiceService.createCreditNote(id);
            redirectAttributes.addFlashAttribute("message", messages.get("invoice.flash.creditNoteDraft"));
            return "redirect:/invoices/" + creditNote.getId() + "/edit";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
            return "redirect:/invoices/" + id;
        }
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String pay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.pay(id);
            redirectAttributes.addFlashAttribute("success", messages.get("invoice.flash.paid"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ISSUE_INVOICE')")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.cancel(id);
            redirectAttributes.addFlashAttribute("success", messages.get("invoice.flash.cancelled"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_INVOICE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean offer = invoiceService.findById(id).isOffer();
            invoiceService.delete(id);
            redirectAttributes.addFlashAttribute("success", messages.get("invoice.flash.draftDeleted"));
            return offer ? "redirect:/quotes" : "redirect:/invoices";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
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

    /** The droit de timbre rules as JSON, for the live preview of the form (invoice-form.js). */
    private String stampDutyRules() {
        InvoiceProperties.StampDuty duty = properties.stampDuty();
        if (!duty.enabled()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{\"minimum\":").append(duty.minimum().toPlainString())
            .append(",\"maximum\":").append(duty.maximum() != null ? duty.maximum().toPlainString() : "null")
            .append(",\"brackets\":[");
        for (int i = 0; i < duty.brackets().size(); i++) {
            InvoiceProperties.Bracket bracket = duty.brackets().get(i);
            json.append(i > 0 ? "," : "").append("{\"upTo\":")
                .append(bracket.upTo() != null ? bracket.upTo().toPlainString() : "null")
                .append(",\"rate\":").append(bracket.rate().toPlainString()).append("}");
        }
        return json.append("]}").toString();
    }

    private String formView(InvoiceForm form, Model model) {
        // Editing a draft credit note: its invoice is shown and the client cannot change
        Invoice existing = form.getId() != null ? invoiceService.findById(form.getId()) : null;
        Invoice creditNoteOf = existing != null && existing.isCreditNote() ? existing.getOriginalInvoice() : null;
        DocumentKind kind = existing != null ? existing.getKind() : (form.getKind() != null ? form.getKind() : DocumentKind.INVOICE);
        boolean creating = form.getId() == null;
        model.addAttribute("creditNoteOf", creditNoteOf);
        model.addAttribute("formKind", kind);
        model.addAttribute("formTitle", kind == DocumentKind.CREDIT_NOTE
            ? messages.get("invoice.form.title.CREDIT_NOTE", creditNoteOf.getInvoiceNumber())
            : messages.get("invoice.form.title." + (creating ? "new." : "edit.") + kind.name()));
        // Droit de timbre: invoices only
        model.addAttribute("stampDutyRules", kind == DocumentKind.INVOICE ? stampDutyRules() : null);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        // TVA per line for a company only (a draft follows the current issuer type)
        model.addAttribute("company", issuerService.getIssuer().isCompany());
        model.addAttribute("vatRates", properties.vatRates());
        model.addAttribute("navSection", DocumentKind.OFFERS.contains(kind) ? "quotes" : "invoices");
        return "invoice/form";
    }
}
