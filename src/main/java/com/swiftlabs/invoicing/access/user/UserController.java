package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.profile.ProfileService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {

    private static final String TEMPORARY_PASSWORD = "Temp1234!";

    private final UserService userService;
    private final ProfileService profileService;

    public UserController(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "access/user/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        // After a failed save the submitted form comes back as a flash attribute
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new UserForm());
        }
        model.addAttribute("profiles", profileService.findAll());
        return "access/user/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", UserForm.from(user));
        }
        model.addAttribute("profiles", profileService.findAll());
        return "access/user/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute UserForm form, RedirectAttributes redirectAttributes) {
        try {
            if (form.getId() == null) {
                userService.create(form, TEMPORARY_PASSWORD);
                redirectAttributes.addFlashAttribute("success",
                    "Utilisateur créé. Mot de passe temporaire : " + TEMPORARY_PASSWORD);
            } else {
                userService.update(form);
                redirectAttributes.addFlashAttribute("success", "Utilisateur mis à jour.");
            }
            return "redirect:/users";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("user", form);
            return form.getId() == null ? "redirect:/users/new" : "redirect:/users/" + form.getId() + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Utilisateur supprimé.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Impossible de supprimer cet utilisateur.");
        }
        return "redirect:/users";
    }
}
