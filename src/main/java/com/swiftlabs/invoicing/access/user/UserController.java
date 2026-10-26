package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.access.profile.ProfileService;
import com.swiftlabs.invoicing.access.twofactor.TwoFactorService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.Messages;
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
    private final TwoFactorService twoFactorService;
    private final Messages messages;
    private final Clock clock;

    public UserController(UserService userService, ProfileService profileService, SessionService sessionService,
                          TwoFactorService twoFactorService, Messages messages, Clock clock) {
        this.messages = messages;
        this.userService = userService;
        this.profileService = profileService;
        this.sessionService = sessionService;
        this.twoFactorService = twoFactorService;
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
        UserForm form = new UserForm();
        // By default the new colleague works in the administrator's language
        form.setLanguage(messages.current());
        model.addAttribute("user", form);
        return formView(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("user.error.notFound"));
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
                    redirectAttributes.addFlashAttribute("success", messages.get("user.created"));
                } else {
                    User user = userService.update(form, me.getId());
                    if (!user.isEnabled()) {
                        sessionService.expireSessions(user.getUsername(), null);
                    }
                    redirectAttributes.addFlashAttribute("success", messages.get("user.updated"));
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
            redirectAttributes.addFlashAttribute("success", messages.get("user.passwordReset"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/users";
    }

    /** Lost phone and recovery codes: the user logs in with the password alone and sets it up again. */
    @PostMapping("/{id}/reset-two-factor")
    public String resetTwoFactor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id).orElseThrow(() -> new ResourceNotFoundException("user.error.notFound"));
        twoFactorService.reset(id);
        sessionService.expireSessions(user.getUsername(), null);
        redirectAttributes.addFlashAttribute("success", messages.get("user.twoFactorReset", user.getUsername()));
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.delete(id, me.getId());
            redirectAttributes.addFlashAttribute("success", messages.get("user.deleted"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", messages.get("user.error.deleteFailed"));
        }
        return "redirect:/users";
    }

    private String formView(Model model) {
        model.addAttribute("profiles", profileService.findAll());
        model.addAttribute("languages", Language.values());
        return "access/user/form";
    }
}
