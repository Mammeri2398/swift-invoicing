package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.common.Endpoints;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Read-only mode once the licence or the trial is over: every change (POST) leads to the licence
 * page instead. Reading, PDFs and exports keep working, and so do signing in and activating a key.
 */
public class LicenceFilter extends OncePerRequestFilter {

    private static final List<String> ALWAYS_ALLOWED = List.of(Endpoints.LICENCE, Endpoints.PERFORM_LOGIN, Endpoints.PERFORM_LOGOUT,
        Endpoints.LOGIN_VERIFY, Endpoints.ACCOUNT + "/", Endpoints.SETUP, Endpoints.FORGOT_PASSWORD, Endpoints.RESET_PASSWORD,
        // Backups stay possible (the data belongs to the customer), restoring one too
        Endpoints.BACKUPS);

    private final LicenceService licenceService;

    public LicenceFilter(LicenceService licenceService) {
        this.licenceService = licenceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (licenceService.isRequired() && !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (ALWAYS_ALLOWED.stream().noneMatch(path::startsWith) && licenceService.status().isReadOnly()) {
                response.sendRedirect(request.getContextPath() + Endpoints.LICENCE + "?readonly");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
