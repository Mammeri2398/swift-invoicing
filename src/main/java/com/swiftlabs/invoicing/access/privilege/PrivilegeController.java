package com.swiftlabs.invoicing.access.privilege;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

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
@PreAuthorize("hasAuthority('MANAGE_PRIVILEGES')")
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
        model.addAttribute("privilege", new PrivilegeForm());
        return "access/privilege/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Privilege privilege = privilegeService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Privilège introuvable."));
        model.addAttribute("privilege", PrivilegeForm.from(privilege));
        return "access/privilege/form";
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("privilege") PrivilegeForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                privilegeService.save(form);
                redirectAttributes.addFlashAttribute("success", "Privilège enregistré.");
                return "redirect:/privileges";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return "access/privilege/form";
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
