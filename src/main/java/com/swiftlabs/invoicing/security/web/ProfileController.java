package com.swiftlabs.invoicing.security.web;

import com.swiftlabs.invoicing.security.domain.Profile;
import com.swiftlabs.invoicing.security.service.ProfileService;
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
@RequestMapping("/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public String list(Model model) {
        List<Profile> profiles = profileService.findAll();
        model.addAttribute("profiles", profiles);
        return "security/profiles/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("profile", new Profile());
        return "security/profiles/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Profile profile = profileService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Profil introuvable."));
        model.addAttribute("profile", profile);
        return "security/profiles/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Profile profile, RedirectAttributes redirectAttributes) {
        try {
            profileService.save(profile);
            redirectAttributes.addFlashAttribute("success", "Profil enregistré.");
            return "redirect:/profiles";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("profile", profile);
            return "redirect:/profiles/new";
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
