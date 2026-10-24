package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;

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
 * Sends a user who typed their password but not their code yet to the code page, and a user who
 * must use two-factor login (administrators, see app.security.two-factor.required-for) but has
 * not set it up to the setup page.
 */
public class TwoFactorFilter extends OncePerRequestFilter {

    public static final String SETUP_URL = "/account/two-factor";

    private static final List<String> ALWAYS_ALLOWED = List.of("/perform-logout", "/css/", "/js/", "/fonts/", "/images/", "/error");

    private final List<String> requiredFor;

    public TwoFactorFilter(List<String> requiredFor) {
        this.requiredFor = List.copyOf(requiredFor);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (TwoFactorLogin.isPending(authentication)) {
            if (!startsWithAny(path, ALWAYS_ALLOWED) && !path.startsWith(TwoFactorLogin.VERIFY_URL)) {
                response.sendRedirect(request.getContextPath() + TwoFactorLogin.VERIFY_URL);
                return;
            }
        } else if (mustSetUp(authentication) && !startsWithAny(path, ALWAYS_ALLOWED)
            // The forced password change comes first, then the setup
            && !path.startsWith(SETUP_URL) && !path.startsWith("/account/password")) {
            response.sendRedirect(request.getContextPath() + SETUP_URL);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean mustSetUp(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
            && !principal.isTwoFactorEnabled()
            && principal.getAuthorities().stream().anyMatch(a -> requiredFor.contains(a.getAuthority()));
    }

    private static boolean startsWithAny(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(path::startsWith);
    }
}
