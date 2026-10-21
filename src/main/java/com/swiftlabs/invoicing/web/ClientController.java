package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.domain.Client;
import com.swiftlabs.invoicing.domain.ClientType;
import com.swiftlabs.invoicing.dto.ClientForm;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.service.ClientService;
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
    public String list(Model model) {
        model.addAttribute("clients", clientService.findAll());
        return "clients/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("client", new ClientForm());
        model.addAttribute("types", ClientType.values());
        return "clients/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Client client = clientService.findById(id);
            ClientForm form = toForm(client);
            model.addAttribute("client", form);
            model.addAttribute("types", ClientType.values());
            return "clients/form";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/clients";
        }
    }

    @PostMapping("/save")
    public String save(@ModelAttribute ClientForm form, RedirectAttributes redirectAttributes) {
        try {
            Client client = toEntity(form);
            clientService.save(client);
            redirectAttributes.addFlashAttribute("success", "Client enregistré.");
            return "redirect:/clients";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("client", form);
            return "redirect:/clients/new";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Client supprimé.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/clients";
    }

    private ClientForm toForm(Client client) {
        ClientForm form = new ClientForm();
        form.setId(client.getId());
        form.setType(client.getType() != null ? client.getType().name() : null);
        form.setName(client.getName());
        form.setAddress(client.getAddress());
        form.setCity(client.getCity());
        form.setPhone(client.getPhone());
        form.setEmail(client.getEmail());
        form.setNif(client.getNif());
        return form;
    }

    private Client toEntity(ClientForm form) {
        Client client = new Client();
        if (form.getId() != null) {
            client.setId(form.getId());
        }
        client.setType(form.getType() != null ? ClientType.valueOf(form.getType()) : null);
        client.setName(form.getName());
        client.setAddress(form.getAddress());
        client.setCity(form.getCity());
        client.setPhone(form.getPhone());
        client.setEmail(form.getEmail());
        client.setNif(form.getNif());
        return client;
    }
}
