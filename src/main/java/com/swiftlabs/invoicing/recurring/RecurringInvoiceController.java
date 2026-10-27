package com.swiftlabs.invoicing.recurring;

import com.swiftlabs.invoicing.catalog.CatalogService;
import com.swiftlabs.invoicing.client.ClientService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.invoice.PaymentMethod;
import com.swiftlabs.invoicing.issuer.IssuerService;

import jakarta.validation.Valid;
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

import java.time.Clock;
import java.time.LocalDate;

/** Recurring invoices: managed by whoever writes invoices, shown under "Factures". */
@Controller
@RequestMapping(Endpoints.RECURRING)
@PreAuthorize(Privileges.Has.CREATE_INVOICE)
public class RecurringInvoiceController {

    private final RecurringInvoiceService service;
    private final ClientService clientService;
    private final CatalogService catalogService;
    private final IssuerService issuerService;
    private final InvoiceProperties properties;
    private final Messages messages;
    private final Clock clock;

    public RecurringInvoiceController(RecurringInvoiceService service, ClientService clientService, CatalogService catalogService,
                                      IssuerService issuerService, InvoiceProperties properties, Messages messages, Clock clock) {
        this.service = service;
        this.clientService = clientService;
        this.catalogService = catalogService;
        this.issuerService = issuerService;
        this.properties = properties;
        this.messages = messages;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(CommonCodes.Attributes.ITEMS, service.findAll());
        model.addAttribute(CommonCodes.Attributes.NOW, LocalDate.now(clock));
        model.addAttribute(CommonCodes.Attributes.COMPANY, issuerService.getIssuer().isCompany());
        model.addAttribute(CommonCodes.Attributes.NAV_SECTION, "invoices");
        return CommonCodes.Views.RECURRING_LIST;
    }

    /** A new model, empty or copied from an invoice ({@code ?from=}). */
    @GetMapping(Endpoints.RECURRING_NEW)
    public String newForm(@RequestParam(name = "from", required = false) Long invoiceId, Model model) {
        RecurringInvoiceForm form;
        if (invoiceId != null) {
            form = service.formFromInvoice(invoiceId);
        } else {
            form = new RecurringInvoiceForm();
            form.setNextDate(LocalDate.now(clock));
            form.addEmptyLine();
        }
        model.addAttribute(CommonCodes.Attributes.RECURRING, form);
        return formView(model);
    }

    @GetMapping(Endpoints.RECURRING_EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute(CommonCodes.Attributes.RECURRING, service.form(id));
        return formView(model);
    }

    /** Saved, then the drafts already due are prepared at once (a model starting today gives today's draft). */
    @PostMapping(Endpoints.RECURRING_SAVE)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.RECURRING) RecurringInvoiceForm form, BindingResult result,
                       Model model, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                RecurringInvoice saved = service.save(form);
                int created = service.generateDue();
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.RECURRING_SAVED, saved.getName())
                    + (created > 0 ? " " + messages.get(MessageProperties.RECURRING_GENERATED, created) : ""));
                return Endpoints.REDIRECT + Endpoints.RECURRING;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        if (form.getLines().isEmpty()) {
            form.addEmptyLine();
        }
        return formView(model);
    }

    @PostMapping(Endpoints.RECURRING_PAUSE)
    public String pause(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        RecurringInvoice model = service.setActive(id, false);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.RECURRING_PAUSED, model.getName()));
        return Endpoints.REDIRECT + Endpoints.RECURRING;
    }

    @PostMapping(Endpoints.RECURRING_RESUME)
    public String resume(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        RecurringInvoice model = service.setActive(id, true);
        int created = service.generateDue();
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.RECURRING_RESUMED, model.getName())
            + (created > 0 ? " " + messages.get(MessageProperties.RECURRING_GENERATED, created) : ""));
        return Endpoints.REDIRECT + Endpoints.RECURRING;
    }

    @PostMapping(Endpoints.RECURRING_DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String name = service.findById(id).getName();
        service.delete(id);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.RECURRING_DELETED, name));
        return Endpoints.REDIRECT + Endpoints.RECURRING;
    }

    /** "Préparer maintenant": the check the application otherwise runs every hour. */
    @PostMapping(Endpoints.RECURRING_RUN)
    public String run(RedirectAttributes redirectAttributes) {
        int created = service.generateDue();
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, created > 0
            ? messages.get(MessageProperties.RECURRING_GENERATED, created) : messages.get(MessageProperties.RECURRING_NONE_DUE));
        return Endpoints.REDIRECT + Endpoints.RECURRING;
    }

    private String formView(Model model) {
        model.addAttribute(CommonCodes.Attributes.FREQUENCIES, Frequency.values());
        model.addAttribute(CommonCodes.Attributes.CLIENTS, clientService.findAll());
        model.addAttribute(CommonCodes.Attributes.PAYMENT_METHODS, PaymentMethod.values());
        model.addAttribute(CommonCodes.Attributes.CATALOG_ITEMS, catalogService.findAll());
        model.addAttribute(CommonCodes.Attributes.COMPANY, issuerService.getIssuer().isCompany());
        model.addAttribute(CommonCodes.Attributes.VAT_RATES, properties.vatRates());
        model.addAttribute(CommonCodes.Attributes.STAMP_DUTY_RULES, properties.stampDuty().toJson());
        model.addAttribute(CommonCodes.Attributes.NAV_SECTION, "invoices");
        return CommonCodes.Views.RECURRING_FORM;
    }
}
