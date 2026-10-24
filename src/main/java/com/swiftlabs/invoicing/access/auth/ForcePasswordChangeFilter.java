package com.swiftlabs.invoicing.access.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * A user signed in with a temporary password can only reach the password change page
 * (and log out) until they have chosen their own password.
 */
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    public static final String CHANGE_PASSWORD_URL = "/account/password";

    private static final List<String> ALLOWED_PREFIXES =
        List.of(CHANGE_PASSWORD_URL, "/perform-logout", "/css/", "/js/", "/fonts/", "/images/", "/error");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
            && principal.isMustChangePassword() && !isAllowed(request)) {
            response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_URL);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
