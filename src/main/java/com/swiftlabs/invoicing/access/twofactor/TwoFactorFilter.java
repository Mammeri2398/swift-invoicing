package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.Endpoints;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sends a user who typed their password but not their code yet to the code page, and a user who
 * must use two-factor login (administrators, see app.security.two-factor.required-for) but has
 * not set it up to the setup page.
 */
public class TwoFactorFilter extends OncePerRequestFilter {

    private static final List<String> ALWAYS_ALLOWED = Stream.concat(Endpoints.STATIC_FOLDERS.stream(),
        Stream.of(Endpoints.PERFORM_LOGOUT, Endpoints.ERROR)).toList();

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
            if (!startsWithAny(path, ALWAYS_ALLOWED) && !path.startsWith(Endpoints.LOGIN_VERIFY)) {
                response.sendRedirect(request.getContextPath() + Endpoints.LOGIN_VERIFY);
                return;
            }
        } else if (mustSetUp(authentication) && !startsWithAny(path, ALWAYS_ALLOWED)
            // The forced password change comes first, then the setup
            && !path.startsWith(Endpoints.TWO_FACTOR) && !path.startsWith(Endpoints.ACCOUNT_PASSWORD_PAGE)) {
            response.sendRedirect(request.getContextPath() + Endpoints.TWO_FACTOR);
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
