package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

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

@Controller
@RequestMapping(Endpoints.INVOICE_EMAIL)
@PreAuthorize(Privileges.Has.ISSUE_INVOICE)
public class InvoiceMailController {

    private final InvoiceService invoiceService;
    private final InvoiceMailService mailService;
    private final Messages messages;

    public InvoiceMailController(InvoiceService invoiceService, InvoiceMailService mailService, Messages messages) {
        this.invoiceService = invoiceService;
        this.mailService = mailService;
        this.messages = messages;
    }

    @GetMapping
    public String form(@PathVariable Long id, @RequestParam(name = "reminder", defaultValue = "false") boolean reminder,
                       Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute(CommonCodes.Attributes.MAIL, reminder ? mailService.prepareReminder(id) : mailService.prepare(id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
            return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
        }
        return formView(id, model);
    }

    /** Invalid input or a sending failure is shown on the form, with what was typed kept. */
    @PostMapping(Endpoints.INVOICE_EMAIL_SEND)
    public String send(@PathVariable Long id, @Valid @ModelAttribute(CommonCodes.Attributes.MAIL) InvoiceEmailForm form, BindingResult result,
                       Model model, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                mailService.send(id, form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.INVOICE_MAIL_SENT, form.getTo()));
                return Endpoints.REDIRECT + Endpoints.of(Endpoints.INVOICES + Endpoints.INVOICE_VIEW, id);
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(id, model);
    }

    private String formView(Long id, Model model) {
        Invoice invoice = invoiceService.findById(id);
        model.addAttribute(CommonCodes.Attributes.INVOICE, invoice);
        model.addAttribute(CommonCodes.Attributes.NAV_SECTION, invoice.isOffer() ? "quotes" : "invoices");
        model.addAttribute(CommonCodes.Attributes.MAIL_ENABLED, mailService.isEnabled());
        model.addAttribute(CommonCodes.Attributes.FROM_ADDRESS, mailService.getFromAddress());
        return CommonCodes.Views.INVOICE_EMAIL;
    }
}
