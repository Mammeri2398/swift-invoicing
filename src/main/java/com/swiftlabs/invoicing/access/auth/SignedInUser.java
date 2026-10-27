package com.swiftlabs.invoicing.access.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/** Replaces the signed-in user kept in the session (new name, language, company...), without logging in again. */
public final class SignedInUser {

    private static final SecurityContextRepository REPOSITORY = new HttpSessionSecurityContextRepository();

    private SignedInUser() {
    }

    public static void replace(AppUserPrincipal updated, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        REPOSITORY.saveContext(context, request, response);
    }
}
