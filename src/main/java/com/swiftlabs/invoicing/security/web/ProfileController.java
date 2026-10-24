package com.swiftlabs.invoicing.security.web;

import com.swiftlabs.invoicing.security.domain.Privilege;
import com.swiftlabs.invoicing.security.domain.Profile;
import com.swiftlabs.invoicing.security.service.ProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        // After a failed save, "profile" and "selectedPrivilegeIds" come back as flash attributes
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", new Profile());
            model.addAttribute("selectedPrivilegeIds", Set.of());
        }
        model.addAttribute("allPrivileges", profileService.findAllPrivileges());
        return "security/profiles/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Profile profile = profileService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Profil introuvable."));
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", profile);
            model.addAttribute("selectedPrivilegeIds",
                profile.getPrivileges().stream().map(Privilege::getId).collect(Collectors.toSet()));
        }
        model.addAttribute("allPrivileges", profileService.findAllPrivileges());
        return "security/profiles/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(name = "id", required = false) Long id,
                       @RequestParam(name = "name", required = false) String name,
                       @RequestParam(name = "description", required = false) String description,
                       @RequestParam(name = "privilegeIds", required = false) List<Long> privilegeIds,
                       RedirectAttributes redirectAttributes) {
        try {
            profileService.save(id, name, description, privilegeIds);
            redirectAttributes.addFlashAttribute("success", "Profil enregistré.");
            return "redirect:/profiles";
        } catch (IllegalArgumentException ex) {
            // Redisplay what was submitted, not the stored values
            Profile submitted = new Profile();
            submitted.setId(id);
            submitted.setName(name);
            submitted.setDescription(description);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("profile", submitted);
            redirectAttributes.addFlashAttribute("selectedPrivilegeIds",
                privilegeIds == null ? Set.of() : new HashSet<>(privilegeIds));
            return id == null ? "redirect:/profiles/new" : "redirect:/profiles/" + id + "/edit";
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
