package com.swiftlabs.invoicing.access.privilege;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/privileges")
public class PrivilegeController {

    private final PrivilegeService privilegeService;

    public PrivilegeController(PrivilegeService privilegeService) {
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("privileges", privilegeService.findAll());
        return "access/privilege/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        // After a failed save the submitted form comes back as a flash attribute
        if (!model.containsAttribute("privilege")) {
            model.addAttribute("privilege", new PrivilegeForm());
        }
        return "access/privilege/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Privilege privilege = privilegeService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Privilège introuvable."));
        if (!model.containsAttribute("privilege")) {
            model.addAttribute("privilege", PrivilegeForm.from(privilege));
        }
        return "access/privilege/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute PrivilegeForm form, RedirectAttributes redirectAttributes) {
        try {
            privilegeService.save(form);
            redirectAttributes.addFlashAttribute("success", "Privilège enregistré.");
            return "redirect:/privileges";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("privilege", form);
            return form.getId() == null ? "redirect:/privileges/new" : "redirect:/privileges/" + form.getId() + "/edit";
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
