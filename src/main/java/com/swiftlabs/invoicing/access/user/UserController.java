package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.access.profile.ProfileService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasAuthority('MANAGE_USERS')")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;
    private final SessionService sessionService;
    private final Clock clock;

    public UserController(UserService userService, ProfileService profileService, SessionService sessionService,
                          Clock clock) {
        this.userService = userService;
        this.profileService = profileService;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("now", LocalDateTime.now(clock));
        return "access/user/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new UserForm());
        return formView(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable."));
        model.addAttribute("user", UserForm.from(user));
        return formView(model);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("user") UserForm form, BindingResult result,
                       @AuthenticationPrincipal AppUserPrincipal me, Model model, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                if (form.getId() == null) {
                    UserService.NewAccount account = userService.create(form);
                    redirectAttributes.addFlashAttribute("temporaryPassword", account.temporaryPassword());
                    redirectAttributes.addFlashAttribute("temporaryPasswordUser", account.user().getUsername());
                    redirectAttributes.addFlashAttribute("success", "Utilisateur créé.");
                } else {
                    User user = userService.update(form, me.getId());
                    if (!user.isEnabled()) {
                        sessionService.expireSessions(user.getUsername(), null);
                    }
                    redirectAttributes.addFlashAttribute("success", "Utilisateur mis à jour.");
                }
                return "redirect:/users";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    /** New temporary password (forgotten password without mail, locked account...). */
    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String temporaryPassword = userService.resetToTemporaryPassword(id);
            User user = userService.findById(id).orElseThrow();
            sessionService.expireSessions(user.getUsername(), null);
            redirectAttributes.addFlashAttribute("temporaryPassword", temporaryPassword);
            redirectAttributes.addFlashAttribute("temporaryPasswordUser", user.getUsername());
            redirectAttributes.addFlashAttribute("success", "Mot de passe réinitialisé et compte déverrouillé.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.delete(id, me.getId());
            redirectAttributes.addFlashAttribute("success", "Utilisateur supprimé.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Impossible de supprimer cet utilisateur.");
        }
        return "redirect:/users";
    }

    private String formView(Model model) {
        model.addAttribute("profiles", profileService.findAll());
        return "access/user/form";
    }
}
