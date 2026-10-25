package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.config.SecurityProperties;
import com.swiftlabs.invoicing.setup.NetworkAddresses;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final SecurityProperties.Lockout lockout;
    private final NetworkAddresses networkAddresses;

    public AuthController(SecurityProperties properties, NetworkAddresses networkAddresses) {
        this.lockout = properties.lockout();
        this.networkAddresses = networkAddresses;
    }

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "logout", required = false) String logout,
                        @RequestParam(name = "expired", required = false) String expired,
                        @RequestParam(name = "setup", required = false) String setup,
                        Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        if (error != null) {
            // Never tells whether the user exists, is locked or disabled
            model.addAttribute("error", "Identifiants invalides. Après " + lockout.maxAttempts()
                + " échecs consécutifs, le compte est verrouillé " + lockout.duration().toMinutes() + " minutes.");
        }
        if (logout != null) {
            model.addAttribute("logout", "Vous avez été déconnecté.");
        }
        if (setup != null) {
            model.addAttribute("logout", "Installation terminée : connectez-vous avec le compte administrateur que vous venez de créer.");
            model.addAttribute("networkAddresses", networkAddresses.urls());
        }
        if (expired != null) {
            model.addAttribute("logout", "Votre session a été fermée (mot de passe modifié ou compte désactivé). Reconnectez-vous.");
        }
        return "access/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access/access-denied";
    }
}
