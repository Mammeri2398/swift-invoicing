package com.swiftlabs.invoicing.client;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.invoice.PaymentService;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;
    private final PaymentService paymentService;
    private final Messages messages;

    public ClientController(ClientService clientService, PaymentService paymentService, Messages messages) {
        this.paymentService = paymentService;
        this.clientService = clientService;
        this.messages = messages;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public String list(Model model) {
        model.addAttribute("clients", clientService.findAll());
        // What each client still owes on issued invoices
        model.addAttribute("balances", paymentService.balancesByClient());
        return "client/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CREATE_CLIENT')")
    public String newForm(Model model) {
        model.addAttribute("client", new ClientForm());
        return formView(model);
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CREATE_CLIENT')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("client", ClientForm.from(clientService.findById(id)));
        return formView(model);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping("/save")
    @PreAuthorize("hasAuthority('CREATE_CLIENT')")
    public String save(@Valid @ModelAttribute("client") ClientForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                clientService.save(form);
                redirectAttributes.addFlashAttribute("success", messages.get("client.saved"));
                return "redirect:/clients";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.delete(id);
            redirectAttributes.addFlashAttribute("success", messages.get("client.deleted"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/clients";
    }

    private String formView(Model model) {
        model.addAttribute("types", ClientType.values());
        return "client/form";
    }
}
