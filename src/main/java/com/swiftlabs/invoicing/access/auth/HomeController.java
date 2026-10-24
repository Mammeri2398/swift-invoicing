package com.swiftlabs.invoicing.access.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Entry point after login: the first section the user is allowed to see. */
@Controller
public class HomeController {

    /** Privilege -> landing page, in order of preference. */
    private static final Map<String, String> LANDING_PAGES = new LinkedHashMap<>();

    static {
        LANDING_PAGES.put("VIEW_DASHBOARD", "/dashboard");
        LANDING_PAGES.put("VIEW_INVOICES", "/invoices");
        LANDING_PAGES.put("VIEW_CLIENTS", "/clients");
        LANDING_PAGES.put("VIEW_ISSUER", "/settings/issuer");
        LANDING_PAGES.put("MANAGE_USERS", "/users");
        LANDING_PAGES.put("MANAGE_PROFILES", "/profiles");
        LANDING_PAGES.put("MANAGE_PRIVILEGES", "/privileges");
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        Set<String> granted = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return LANDING_PAGES.entrySet().stream()
            .filter(entry -> granted.contains(entry.getKey()))
            .findFirst()
            .map(entry -> "redirect:" + entry.getValue())
            // No privilege at all: the only page left is the user's own account
            .orElse("redirect:" + ForcePasswordChangeFilter.CHANGE_PASSWORD_URL);
    }
}
