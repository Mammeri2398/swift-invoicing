package com.swiftlabs.invoicing.security.web;

import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import com.swiftlabs.invoicing.security.service.PasswordResetService;
import com.swiftlabs.invoicing.security.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final UserService userService;

    public PasswordResetController(UserRepository userRepository,
                                   PasswordResetService passwordResetService,
                                   UserService userService) {
        this.userRepository = userRepository;
        this.passwordResetService = passwordResetService;
        this.userService = userService;
    }

    @GetMapping("/forgot-password")
    public String forgotForm() {
        return "security/forgot-password";
    }

    @PostMapping("/forgot-password/send")
    public String sendReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetService.createResetTokenForUser(user);
        });
        redirectAttributes.addFlashAttribute("message",
            "Si cet email existe, un lien de réinitialisation vous a été envoyé.");
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam String token, Model model, RedirectAttributes redirectAttributes) {
        if (passwordResetService.findValidToken(token).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Lien de réinitialisation invalide ou expiré.");
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        return "security/reset-password";
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
        var resetToken = passwordResetService.findValidToken(token);
        if (resetToken.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Lien de réinitialisation invalide ou expiré.");
            return "redirect:/login";
        }
        try {
            userService.changePassword(resetToken.get().getUser().getId(), password);
            passwordResetService.useToken(token);
            redirectAttributes.addFlashAttribute("success", "Mot de passe réinitialisé. Vous pouvez vous connecter.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }
}
