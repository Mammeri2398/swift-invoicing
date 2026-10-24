package com.swiftlabs.invoicing.access.profile;

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
@PreAuthorize("hasAuthority('MANAGE_PROFILES')")
@RequestMapping("/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("profiles", profileService.findAll());
        return "access/profile/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("profile", new ProfileForm());
        return formView(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Profile profile = profileService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Profil introuvable."));
        model.addAttribute("profile", ProfileForm.from(profile));
        return formView(model);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("profile") ProfileForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                profileService.save(form);
                redirectAttributes.addFlashAttribute("success", "Profil enregistré.");
                return "redirect:/profiles";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            profileService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Profil supprimé.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Impossible de supprimer ce profil.");
        }
        return "redirect:/profiles";
    }

    private String formView(Model model) {
        model.addAttribute("allPrivileges", profileService.findAllPrivileges());
        return "access/profile/form";
    }
}
