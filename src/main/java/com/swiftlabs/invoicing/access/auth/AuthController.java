package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
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

    @GetMapping(Endpoints.LOGIN)
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "logout", required = false) String logout,
                        @RequestParam(name = "expired", required = false) String expired,
                        @RequestParam(name = "setup", required = false) String setup,
                        @RequestParam(name = "restored", required = false) String restored,
                        Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)) {
            return Endpoints.REDIRECT + Endpoints.HOME;
        }
        if (error != null) {
            // Never tells whether the user exists, is locked or disabled
            model.addAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.AUTH_LOGIN_ERROR, lockout.maxAttempts(),
                lockout.duration().toMinutes()));
        }
        if (logout != null) {
            model.addAttribute(CommonCodes.Attributes.LOGOUT, messages.get(MessageProperties.AUTH_LOGIN_LOGGED_OUT));
        }
        if (setup != null) {
            model.addAttribute(CommonCodes.Attributes.LOGOUT, messages.get(MessageProperties.AUTH_LOGIN_SETUP_DONE));
            model.addAttribute(CommonCodes.Attributes.NETWORK_ADDRESSES, networkAddresses.urls());
        }
        if (expired != null) {
            model.addAttribute(CommonCodes.Attributes.LOGOUT, messages.get(MessageProperties.AUTH_LOGIN_EXPIRED));
        }
        if (restored != null) {
            model.addAttribute(CommonCodes.Attributes.LOGOUT, messages.get(MessageProperties.AUTH_LOGIN_RESTORED));
        }
        return CommonCodes.Views.ACCESS_LOGIN;
    }

    @GetMapping(Endpoints.ACCESS_DENIED)
    public String accessDenied() {
        return CommonCodes.Views.ACCESS_ACCESS_DENIED;
    }
}
