package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;

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

    public InvoiceMailController(InvoiceService invoiceService, InvoiceMailService mailService) {
        this.invoiceService = invoiceService;
        this.mailService = mailService;
    }

    @GetMapping
    public String form(@PathVariable Long id, @RequestParam(name = "reminder", defaultValue = "false") boolean reminder,
                       Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("mail", reminder ? mailService.prepareReminder(id) : mailService.prepare(id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
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
                redirectAttributes.addFlashAttribute("success", "Facture envoyée à " + form.getTo() + ".");
                return "redirect:/invoices/" + id;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(id, model);
    }

    private String formView(Long id, Model model) {
        model.addAttribute("invoice", invoiceService.findById(id));
        model.addAttribute("mailEnabled", mailService.isEnabled());
        model.addAttribute("fromAddress", mailService.getFromAddress());
        return "invoice/email";
    }
}
