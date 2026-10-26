package com.swiftlabs.invoicing.mail;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Messages;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/mail")
public class MailSettingsController {

    private final MailSettingsService settingsService;
    private final UserService userService;
    private final Messages messages;

    public MailSettingsController(MailSettingsService settingsService, UserService userService, Messages messages) {
        this.settingsService = settingsService;
        this.userService = userService;
        this.messages = messages;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ISSUER')")
    public String form(Model model) {
        model.addAttribute("mail", MailSettingsForm.from(settingsService.current()));
        return formView(model);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EDIT_ISSUER')")
    public String save(@Valid @ModelAttribute("mail") MailSettingsForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                settingsService.save(form);
                redirectAttributes.addFlashAttribute("success", messages.get(form.isEnabled()
                    ? "mail.saved.enabled" : "mail.saved.disabled"));
                return "redirect:/settings/mail";
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    /** Sends a test message to the logged-in user's own address. */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('EDIT_ISSUER')")
    public String test(@AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        String to = userService.findById(principal.getId()).map(user -> user.getEmail()).orElse(null);
        try {
            settingsService.sendTest(to);
            redirectAttributes.addFlashAttribute("success", messages.get("mail.test.sent", to));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:/settings/mail";
    }

    private String formView(Model model) {
        model.addAttribute("hasPassword", settingsService.current().getPassword() != null);
        model.addAttribute("securities", MailSecurity.values());
        return "mail/settings";
    }
}
