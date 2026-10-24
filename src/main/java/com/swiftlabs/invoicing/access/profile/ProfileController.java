package com.swiftlabs.invoicing.access.profile;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
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
        // After a failed save the submitted form comes back as a flash attribute
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", new ProfileForm());
        }
        model.addAttribute("allPrivileges", profileService.findAllPrivileges());
        return "access/profile/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Profile profile = profileService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Profil introuvable."));
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", ProfileForm.from(profile));
        }
        model.addAttribute("allPrivileges", profileService.findAllPrivileges());
        return "access/profile/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute ProfileForm form, RedirectAttributes redirectAttributes) {
        try {
            profileService.save(form);
            redirectAttributes.addFlashAttribute("success", "Profil enregistré.");
            return "redirect:/profiles";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("profile", form);
            return form.getId() == null ? "redirect:/profiles/new" : "redirect:/profiles/" + form.getId() + "/edit";
        }
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
}
