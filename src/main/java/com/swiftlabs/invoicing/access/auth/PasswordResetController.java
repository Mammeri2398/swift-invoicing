package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.PasswordPolicy;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final PasswordPolicy passwordPolicy;
    private final Messages messages;
    private final long linkValidityMinutes;

    public PasswordResetController(PasswordResetService passwordResetService, PasswordPolicy passwordPolicy, Messages messages,
                                   SecurityProperties properties) {
        this.passwordResetService = passwordResetService;
        this.passwordPolicy = passwordPolicy;
        this.messages = messages;
        this.linkValidityMinutes = properties.resetTokenValidity().toMinutes();
    }

    @GetMapping("/forgot-password")
    public String forgotForm(Model model) {
        model.addAttribute("linkValidityMinutes", linkValidityMinutes);
        return "access/forgot-password";
    }

    @PostMapping("/forgot-password/send")
    public String sendReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        passwordResetService.requestReset(email);
        // Same answer whether the email exists or not
        redirectAttributes.addFlashAttribute("message", messages.get("auth.forgot.sent"));
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam String token, Model model, RedirectAttributes redirectAttributes) {
        if (!passwordResetService.isValid(token)) {
            redirectAttributes.addFlashAttribute("error", messages.get(InvalidResetTokenException.CODE));
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        model.addAttribute("passwordRules", passwordPolicy.describe());
        return "access/reset-password";
    }

    @PostMapping("/reset-password/save")
    public String saveReset(@RequestParam String token,
                            @RequestParam String password,
                            @RequestParam String confirmPassword,
                            RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", messages.get("auth.reset.mismatch"));
            return "redirect:/reset-password?token=" + token;
        }
        try {
            passwordResetService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute("success", messages.get("auth.reset.done"));
            return "redirect:/login";
        } catch (InvalidResetTokenException ex) {
            redirectAttributes.addFlashAttribute("error", messages.get(InvalidResetTokenException.CODE));
            return "redirect:/login";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
            return "redirect:/reset-password?token=" + token;
        }
    }
}
