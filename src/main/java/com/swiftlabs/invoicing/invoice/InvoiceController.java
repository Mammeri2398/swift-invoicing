package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.catalog.CatalogService;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.AmountUtils;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.PageLinks;
import com.swiftlabs.invoicing.common.Privileges;
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
@RequestMapping(Endpoints.INVOICES)
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
    private final CatalogService catalogService;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService,
                             InvoicePdfService pdfService, InvoiceAudit audit, IssuerService issuerService,
                             PaymentService paymentService, InvoiceProperties properties, Messages messages,
                             CatalogService catalogService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.pdfService = pdfService;
        this.audit = audit;
        this.issuerService = issuerService;
        this.paymentService = paymentService;
        this.properties = properties;
        this.messages = messages;
        this.catalogService = catalogService;
    }

    /**
     * Filtered, paginated list. An invalid filter value in the URL (e.g. an unknown status) is
     * ignored rather than failing the page: the binding errors are collected and not shown.
     */
    @GetMapping
    @PreAuthorize(Privileges.Has.VIEW_INVOICES)
    public String list(@ModelAttribute(CommonCodes.Attributes.FILTER) InvoiceFilter filter, BindingResult ignoredFilterErrors,
                       @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request, Model model) {
        // The register: invoices and credit notes (quotes and pro formas are on their own page)
        filter.setKinds(DocumentKind.ACCOUNTING);
        Page<Invoice> page = invoiceService.search(filter,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
        model.addAttribute(CommonCodes.Attributes.PAGE, page);
        model.addAttribute(CommonCodes.Attributes.TOTAL, invoiceService.totalOf(filter));
        model.addAttribute(CommonCodes.Attributes.PAGE_LINKS, new PageLinks(request));
        model.addAttribute(CommonCodes.Attributes.CLIENTS, clientService.findAll());
        model.addAttribute(CommonCodes.Attributes.STATUSES, List.of(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED, InvoiceStatus.PAID,
            InvoiceStatus.CANCELLED));
        return CommonCodes.Views.INVOICE_LIST;
    }

    @GetMapping(Endpoints.INVOICE_NEW)
    @PreAuthorize(Privileges.Has.CREATE_INVOICE)
    public String newForm(@RequestParam(name = "kind", required = false) DocumentKind kind, Model model) {
        InvoiceForm form = new InvoiceForm();
        form.setKind(kind != null && !kind.isAccounting() ? kind : DocumentKind.INVOICE);
        form.addEmptyLine();
        model.addAttribute(CommonCodes.Attributes.INVOICE, form);
        return formView(form, model);
    }

    @GetMapping(Endpoints.INVOICE_EDIT)
    @PreAuthorize(Privileges.Has.CREATE_INVOICE)
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Invoice invoice = invoiceService.findWithLines(id);
        if (!invoice.isDraft()) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.INVOICE_ERROR_NOT_DRAFT));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
        }
        model.addAttribute(CommonCodes.Attributes.INVOICE, InvoiceForm.from(invoice));
        return formView(InvoiceForm.from(invoice), model);
    }

    /** Invalid input is shown again with the errors next to each field (no redirect, nothing lost). */
    @PostMapping(Endpoints.INVOICE_SAVE)
    @PreAuthorize(Privileges.Has.CREATE_INVOICE)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.INVOICE) InvoiceForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                Invoice saved = invoiceService.saveDraft(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
                    messages.get(MessageProperties.INVOICE_FLASH_DRAFT_SAVED_PREFIX + saved.getKind().name()));
                return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, saved.getId());
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        if (form.getLines().isEmpty()) {
            form.addEmptyLine();
        }
        return formView(form, model);
    }

    @GetMapping(Endpoints.INVOICE_VIEW)
    @PreAuthorize(Privileges.Has.VIEW_INVOICES)
    public String view(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.findWithLines(id);
        model.addAttribute(CommonCodes.Attributes.INVOICE, invoice);
        // DocumentKind.getLabel() is the French label of the PDF: the page uses the reader's language
        model.addAttribute(CommonCodes.Attributes.DOC_TITLE, invoice.getInvoiceNumber() != null
            ? messages.get(MessageProperties.INVOICE_VIEW_TITLE, messages.label(invoice.getKind()), invoice.getInvoiceNumber())
            : messages.get(MessageProperties.INVOICE_VIEW_DRAFT_TITLE_PREFIX + invoice.getKind().name()));
        model.addAttribute(CommonCodes.Attributes.ISSUER, IssuerDetails.of(invoice, issuerService.getIssuer()));
        if (invoice.isOffer()) {
            model.addAttribute(CommonCodes.Attributes.INVOICE_OF, invoiceService.invoiceOf(id));
        } else if (!invoice.isCreditNote()) {
            model.addAttribute(CommonCodes.Attributes.CREDIT_NOTES, invoiceService.creditNotesOf(id));
            if (invoice.isIssuedOrPaid()) {
                BigDecimal remaining = invoiceService.remainingToCredit(invoice);
                model.addAttribute(CommonCodes.Attributes.REMAINING_TO_CREDIT, remaining);
                model.addAttribute(CommonCodes.Attributes.FULLY_CREDITED, remaining.signum() <= 0);
                PaymentService.Summary payment = paymentService.summary(invoice);
                model.addAttribute(CommonCodes.Attributes.PAYMENT, payment);
                model.addAttribute(CommonCodes.Attributes.PAYMENTS, paymentService.paymentsOf(id));
                model.addAttribute(CommonCodes.Attributes.OVERDUE, invoice.getStatus() == InvoiceStatus.ISSUED && invoice.getDueDate() != null
                    && invoice.getDueDate().isBefore(LocalDate.now()) && payment.balance().signum() > 0);
                // The form proposes the whole balance, today, with the invoice's payment method
                model.addAttribute(CommonCodes.Attributes.PAYMENT_FORM, new PaymentForm(payment.balance().max(BigDecimal.ZERO), LocalDate.now(),
                    invoice.getPaymentMethod(), null));
                model.addAttribute(CommonCodes.Attributes.PAYMENT_METHODS, PaymentMethod.values());
            }
        }
        model.addAttribute(CommonCodes.Attributes.HISTORY, audit.history(id));
        model.addAttribute(CommonCodes.Attributes.NAV_SECTION, invoice.isOffer() ? "quotes" : "invoices");
        return CommonCodes.Views.INVOICE_VIEW;
    }

    @PostMapping(Endpoints.INVOICE_ISSUE)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String issue(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice issued = invoiceService.issue(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
                messages.get(MessageProperties.INVOICE_FLASH_ISSUED_PREFIX + issued.getKind().name(), issued.getInvoiceNumber()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
    }

    /** A payment typed on the invoice page (all or part of the balance). */
    @PostMapping(Endpoints.INVOICE_PAYMENTS)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String recordPayment(@PathVariable Long id, @Valid @ModelAttribute(CommonCodes.Attributes.PAYMENT_FORM) PaymentForm form,
        BindingResult result,
                                RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.INVOICE_FLASH_PAYMENT_INVALID));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
        }
        try {
            InvoicePayment payment = paymentService.record(id, form);
            Invoice invoice = payment.getInvoice();
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, invoice.getStatus() == InvoiceStatus.PAID
                ? messages.get(MessageProperties.INVOICE_FLASH_PAID_IN_FULL)
                : messages.get(MessageProperties.INVOICE_FLASH_PAYMENT_RECORDED, AmountUtils.format(payment.getAmount())));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
    }

    /** Removes a payment typed by mistake. */
    @PostMapping(Endpoints.INVOICE_PAYMENT_DELETE)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String deletePayment(@PathVariable Long id, @PathVariable Long paymentId, RedirectAttributes redirectAttributes) {
        paymentService.delete(id, paymentId);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.INVOICE_FLASH_PAYMENT_DELETED));
        return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
    }

    @PostMapping(Endpoints.INVOICE_ACCEPT)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String accept(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return quoteAnswer(id, true, redirectAttributes);
    }

    @PostMapping(Endpoints.INVOICE_REFUSE)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String refuse(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return quoteAnswer(id, false, redirectAttributes);
    }

    private String quoteAnswer(Long id, boolean accepted, RedirectAttributes redirectAttributes) {
        try {
            Invoice quote = accepted ? invoiceService.accept(id) : invoiceService.refuse(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
                messages.get(accepted ? MessageProperties.INVOICE_FLASH_QUOTE_ACCEPTED : MessageProperties.INVOICE_FLASH_QUOTE_REFUSED,
                    quote.getInvoiceNumber()));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
    }

    /** Turns a quote or a pro forma into a draft invoice, opened for a last check before issuing. */
    @PostMapping(Endpoints.INVOICE_CONVERT)
    @PreAuthorize(Privileges.Has.CREATE_INVOICE)
    public String convert(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.convertToInvoice(id);
            if (!invoice.isDraft()) {
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.MESSAGE,
                    messages.get(MessageProperties.INVOICE_FLASH_ALREADY_INVOICED, invoice.getInvoiceNumber()));
                return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, invoice.getId());
            }
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.MESSAGE, messages.get(MessageProperties.INVOICE_FLASH_CONVERTED));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_EDIT, invoice.getId());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
        }
    }

    /** Opens the credit note of an issued invoice: a draft with its lines, to reduce for a partial credit. */
    @PostMapping(Endpoints.INVOICE_CREDIT_NOTE)
    @PreAuthorize(Privileges.Has.CREATE_INVOICE)
    public String creditNote(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice creditNote = invoiceService.createCreditNote(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.MESSAGE, messages.get(MessageProperties.INVOICE_FLASH_CREDIT_NOTE_DRAFT));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_EDIT, creditNote.getId());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
        }
    }

    @PostMapping(Endpoints.INVOICE_PAY)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String pay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.pay(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.INVOICE_FLASH_PAID));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
    }

    @PostMapping(Endpoints.INVOICE_CANCEL)
    @PreAuthorize(Privileges.Has.ISSUE_INVOICE)
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.cancel(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.INVOICE_FLASH_CANCELLED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
    }

    @PostMapping(Endpoints.INVOICE_DELETE)
    @PreAuthorize(Privileges.Has.DELETE_INVOICE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean offer = invoiceService.findById(id).isOffer();
            invoiceService.delete(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.INVOICE_FLASH_DRAFT_DELETED));
            return offer ? Endpoints.REDIRECT + Endpoints.QUOTES : Endpoints.REDIRECT + Endpoints.INVOICES;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
        }
    }

    @GetMapping(Endpoints.INVOICE_PDF)
    @PreAuthorize(Privileges.Has.VIEW_INVOICES)
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
        model.addAttribute(CommonCodes.Attributes.CREDIT_NOTE_OF, creditNoteOf);
        model.addAttribute(CommonCodes.Attributes.FORM_KIND, kind);
        model.addAttribute(CommonCodes.Attributes.FORM_TITLE, kind == DocumentKind.CREDIT_NOTE
            ? messages.get(MessageProperties.INVOICE_FORM_TITLE_CREDIT_NOTE, creditNoteOf.getInvoiceNumber())
            : messages.get((creating ? MessageProperties.INVOICE_FORM_TITLE_NEW_PREFIX
                : MessageProperties.INVOICE_FORM_TITLE_EDIT_PREFIX) + kind.name()));
        // Droit de timbre: invoices only
        model.addAttribute(CommonCodes.Attributes.STAMP_DUTY_RULES, kind == DocumentKind.INVOICE ? stampDutyRules() : null);
        // Suggested while typing a line's description (invoice-form.js fills the unit, price and TVA)
        model.addAttribute(CommonCodes.Attributes.CATALOG_ITEMS, catalogService.findAll());
        model.addAttribute(CommonCodes.Attributes.CLIENTS, clientService.findAll());
        model.addAttribute(CommonCodes.Attributes.PAYMENT_METHODS, PaymentMethod.values());
        // TVA per line for a company only (a draft follows the current issuer type)
        model.addAttribute(CommonCodes.Attributes.COMPANY, issuerService.getIssuer().isCompany());
        model.addAttribute(CommonCodes.Attributes.VAT_RATES, properties.vatRates());
        model.addAttribute(CommonCodes.Attributes.NAV_SECTION, DocumentKind.OFFERS.contains(kind) ? "quotes" : "invoices");
        return CommonCodes.Views.INVOICE_FORM;
    }
}
