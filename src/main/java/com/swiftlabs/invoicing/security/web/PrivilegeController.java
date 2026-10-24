package com.swiftlabs.invoicing.security.web;

import com.swiftlabs.invoicing.security.domain.Privilege;
import com.swiftlabs.invoicing.security.service.PrivilegeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/privileges")
public class PrivilegeController {

    private final PrivilegeService privilegeService;

    public PrivilegeController(PrivilegeService privilegeService) {
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public String list(Model model) {
        List<Privilege> privileges = privilegeService.findAll();
        model.addAttribute("privileges", privileges);
        return "security/privileges/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("privilege", new Privilege());
        return "security/privileges/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Privilege privilege = privilegeService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Privilège introuvable."));
        model.addAttribute("privilege", privilege);
        return "security/privileges/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Privilege privilege, RedirectAttributes redirectAttributes) {
        try {
            privilegeService.save(privilege);
            redirectAttributes.addFlashAttribute("success", "Privilège enregistré.");
            return "redirect:/privileges";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("privilege", privilege);
            return "redirect:/privileges/new";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            privilegeService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Privilège supprimé.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Impossible de supprimer ce privilège.");
        }
        return "redirect:/privileges";
    }
}
