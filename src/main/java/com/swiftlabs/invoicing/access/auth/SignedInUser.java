package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.twofactor.TwoFactorLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Collection;

/** Replaces the signed-in user kept in the session (new name, language, company...), without logging in again. */
public final class SignedInUser {

    private static final SecurityContextRepository REPOSITORY = new HttpSessionSecurityContextRepository();

    private SignedInUser() {
    }

    public static void replace(AppUserPrincipal updated, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        // A user still waiting for their two-factor code keeps only that waiting state, whatever the update
        Collection<? extends GrantedAuthority> authorities = TwoFactorLogin.isPending(current)
            ? current.getAuthorities() : updated.getAuthorities();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, authorities));
        SecurityContextHolder.setContext(context);
        REPOSITORY.saveContext(context, request, response);
    }
}
