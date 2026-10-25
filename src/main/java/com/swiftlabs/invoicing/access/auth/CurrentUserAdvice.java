package com.swiftlabs.invoicing.access.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.stream.Collectors;

/** Exposes the logged-in user to every view as {@code currentUser} (null when anonymous). */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    public CurrentUser currentUser(@CurrentSecurityContext(expression = "authentication") Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return new CurrentUser(principal.getUsername(), principal.getDisplayName(), principal.getProfileName(),
            authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }
}
