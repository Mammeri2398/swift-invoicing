package com.swiftlabs.invoicing.security.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "logout", required = false) String logout,
                        org.springframework.ui.Model model) {
        if (error != null) {
            model.addAttribute("error", "Nom d'utilisateur ou mot de passe invalide.");
        }
        if (logout != null) {
            model.addAttribute("logout", "Vous avez été déconnecté.");
        }
        return "security/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "security/access-denied";
    }
}
