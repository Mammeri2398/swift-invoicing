package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.Privileges;

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
        LANDING_PAGES.put(Privileges.VIEW_DASHBOARD, Endpoints.DASHBOARD);
        LANDING_PAGES.put(Privileges.VIEW_INVOICES, Endpoints.INVOICES);
        LANDING_PAGES.put(Privileges.VIEW_CLIENTS, Endpoints.CLIENTS);
        LANDING_PAGES.put(Privileges.VIEW_ISSUER, Endpoints.ISSUER);
        LANDING_PAGES.put(Privileges.MANAGE_USERS, Endpoints.USERS);
        LANDING_PAGES.put(Privileges.MANAGE_PROFILES, Endpoints.PROFILES);
        LANDING_PAGES.put(Privileges.MANAGE_PRIVILEGES, Endpoints.PRIVILEGES);
        LANDING_PAGES.put(Privileges.MANAGE_BACKUPS, Endpoints.BACKUPS);
    }

    @GetMapping(Endpoints.HOME)
    public String home(Authentication authentication) {
        Set<String> granted = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return LANDING_PAGES.entrySet().stream()
            .filter(entry -> granted.contains(entry.getKey()))
            .findFirst()
            .map(entry -> Endpoints.REDIRECT + entry.getValue())
            // No privilege at all: the only page left is the user's own account
            .orElse(Endpoints.REDIRECT + Endpoints.ACCOUNT_PASSWORD_PAGE);
    }
}
