package com.swiftlabs.invoicing.security.web;

import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.domain.Profile;
import com.swiftlabs.invoicing.security.repository.ProfileRepository;
import com.swiftlabs.invoicing.security.service.UserService;
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
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final ProfileRepository profileRepository;

    public UserController(UserService userService, ProfileRepository profileRepository) {
        this.userService = userService;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<User> users = userService.findAll();
        model.addAttribute("users", users);
        return "security/users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        // Keep the submitted values when redirected back after a failed save
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new User());
        }
        model.addAttribute("profiles", profileRepository.findAll());
        return "security/users/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", user);
        }
        model.addAttribute("profiles", profileRepository.findAll());
        return "security/users/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        try {
            if (user.getId() == null) {
                userService.create(user, "Temp1234!");
                redirectAttributes.addFlashAttribute("success", "Utilisateur créé. Mot de passe temporaire : Temp1234!");
            } else {
                userService.update(user);
                redirectAttributes.addFlashAttribute("success", "Utilisateur mis à jour.");
            }
            return "redirect:/users";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("user", user);
            return user.getId() == null ? "redirect:/users/new" : "redirect:/users/" + user.getId() + "/edit";
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

    @ModelAttribute("profiles")
    public List<Profile> profiles() {
        return profileRepository.findAll();
    }
}
