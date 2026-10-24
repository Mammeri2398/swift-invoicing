package com.swiftlabs.invoicing.client;

import com.swiftlabs.invoicing.common.BusinessException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public String list(Model model) {
        model.addAttribute("clients", clientService.findAll());
        return "client/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CREATE_CLIENT')")
    public String newForm(Model model) {
        // After a failed save the submitted form comes back as a flash attribute
        if (!model.containsAttribute("client")) {
            model.addAttribute("client", new ClientForm());
        }
        model.addAttribute("types", ClientType.values());
        return "client/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CREATE_CLIENT')")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Client client = clientService.findById(id);
            if (!model.containsAttribute("client")) {
                model.addAttribute("client", ClientForm.from(client));
            }
            model.addAttribute("types", ClientType.values());
            return "client/form";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/clients";
        }
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('CREATE_CLIENT')")
    public String save(@ModelAttribute ClientForm form, RedirectAttributes redirectAttributes) {
        try {
            clientService.save(form);
            redirectAttributes.addFlashAttribute("success", "Client enregistré.");
            return "redirect:/clients";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("client", form);
            return form.getId() == null ? "redirect:/clients/new" : "redirect:/clients/" + form.getId() + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Client supprimé.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/clients";
    }
}
