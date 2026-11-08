package com.swiftlabs.invoicing.client;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.invoice.PaymentService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping(Endpoints.CLIENTS)
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
    @PreAuthorize(Privileges.Has.VIEW_CLIENTS)
    public String list(Authentication authentication, Model model) {
        model.addAttribute(CommonCodes.Attributes.CLIENTS, clientService.findAll());
        // What each client still owes on issued invoices: invoice data, for who may see the invoices only
        boolean seesInvoices = authentication.getAuthorities().stream()
            .anyMatch(authority -> Privileges.VIEW_INVOICES.equals(authority.getAuthority()));
        model.addAttribute(CommonCodes.Attributes.SHOW_BALANCES, seesInvoices);
        model.addAttribute(CommonCodes.Attributes.BALANCES, seesInvoices ? paymentService.balancesByClient() : Map.of());
        return CommonCodes.Views.CLIENT_LIST;
    }

    @GetMapping(Endpoints.CLIENT_NEW)
    @PreAuthorize(Privileges.Has.CREATE_CLIENT)
    public String newForm(Model model) {
        model.addAttribute(CommonCodes.Attributes.CLIENT, new ClientForm());
        return formView(model);
    }

    @GetMapping(Endpoints.CLIENT_EDIT)
    @PreAuthorize(Privileges.Has.CREATE_CLIENT)
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute(CommonCodes.Attributes.CLIENT, ClientForm.from(clientService.findById(id)));
        return formView(model);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping(Endpoints.CLIENT_SAVE)
    @PreAuthorize(Privileges.Has.CREATE_CLIENT)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.CLIENT) ClientForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                clientService.save(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.CLIENT_SAVED));
                return Endpoints.REDIRECT + Endpoints.CLIENTS;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    @PostMapping(Endpoints.CLIENT_DELETE)
    @PreAuthorize(Privileges.Has.DELETE_CLIENT)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.delete(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.CLIENT_DELETED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.CLIENTS;
    }

    private String formView(Model model) {
        model.addAttribute(CommonCodes.Attributes.TYPES, ClientType.values());
        return CommonCodes.Views.CLIENT_FORM;
    }
}
