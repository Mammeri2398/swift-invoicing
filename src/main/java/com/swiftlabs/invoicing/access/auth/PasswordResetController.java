package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.BusinessException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotForm() {
        return "access/forgot-password";
    }

    @PostMapping("/forgot-password/send")
    public String sendReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        passwordResetService.requestReset(email);
        // Same answer whether the email exists or not
        redirectAttributes.addFlashAttribute("message",
            "Si cet email existe, un lien de réinitialisation vous a été envoyé.");
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam String token, Model model, RedirectAttributes redirectAttributes) {
        if (!passwordResetService.isValid(token)) {
            redirectAttributes.addFlashAttribute("error", new InvalidResetTokenException().getMessage());
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        return "access/reset-password";
    }

    @PostMapping("/reset-password/save")
    public String saveReset(@RequestParam String token,
                            @RequestParam String password,
                            @RequestParam String confirmPassword,
                            RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Les mots de passe ne correspondent pas.");
            return "redirect:/reset-password?token=" + token;
        }
        try {
            passwordResetService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute("success", "Mot de passe réinitialisé. Vous pouvez vous connecter.");
            return "redirect:/login";
        } catch (InvalidResetTokenException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/login";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }
}
