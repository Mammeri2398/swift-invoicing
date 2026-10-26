package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.Messages;
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
    private final Messages messages;

    public AuthController(SecurityProperties properties, NetworkAddresses networkAddresses, Messages messages) {
        this.lockout = properties.lockout();
        this.networkAddresses = networkAddresses;
        this.messages = messages;
    }

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "logout", required = false) String logout,
                        @RequestParam(name = "expired", required = false) String expired,
                        @RequestParam(name = "setup", required = false) String setup,
                        @RequestParam(name = "restored", required = false) String restored,
                        Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        if (error != null) {
            // Never tells whether the user exists, is locked or disabled
            model.addAttribute("error", messages.get("auth.login.error", lockout.maxAttempts(), lockout.duration().toMinutes()));
        }
        if (logout != null) {
            model.addAttribute("logout", messages.get("auth.login.loggedOut"));
        }
        if (setup != null) {
            model.addAttribute("logout", messages.get("auth.login.setupDone"));
            model.addAttribute("networkAddresses", networkAddresses.urls());
        }
        if (expired != null) {
            model.addAttribute("logout", messages.get("auth.login.expired"));
        }
        if (restored != null) {
            model.addAttribute("logout", messages.get("auth.login.restored"));
        }
        return "access/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access/access-denied";
    }
}
