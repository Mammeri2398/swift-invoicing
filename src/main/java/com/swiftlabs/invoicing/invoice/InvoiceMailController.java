package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Messages;

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
@RequestMapping("/invoices/{id}/email")
@PreAuthorize("hasAuthority('ISSUE_INVOICE')")
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
            model.addAttribute("mail", reminder ? mailService.prepareReminder(id) : mailService.prepare(id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
            return "redirect:/invoices/" + id;
        }
        return formView(id, model);
    }

    /** Invalid input or a sending failure is shown on the form, with what was typed kept. */
    @PostMapping("/send")
    public String send(@PathVariable Long id, @Valid @ModelAttribute("mail") InvoiceEmailForm form, BindingResult result,
                       Model model, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                mailService.send(id, form);
                redirectAttributes.addFlashAttribute("success", messages.get("invoiceMail.sent", form.getTo()));
                return "redirect:/invoices/" + id;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(id, model);
    }

    private String formView(Long id, Model model) {
        Invoice invoice = invoiceService.findById(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("navSection", invoice.isOffer() ? "quotes" : "invoices");
        model.addAttribute("mailEnabled", mailService.isEnabled());
        model.addAttribute("fromAddress", mailService.getFromAddress());
        return "invoice/email";
    }
}
