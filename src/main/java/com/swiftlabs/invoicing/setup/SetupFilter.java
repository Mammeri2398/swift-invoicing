package com.swiftlabs.invoicing.setup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Until the first-run setup is done, every page leads to it; afterwards the setup page is closed. */
public class SetupFilter extends OncePerRequestFilter {

    public static final String SETUP_URL = "/setup";

    private static final List<String> ALWAYS_ALLOWED = List.of("/css/", "/js/", "/fonts/", "/images/", "/favicon.ico", "/error",
        "/actuator/health");

    private final SetupService setupService;

    public SetupFilter(SetupService setupService) {
        this.setupService = setupService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (ALWAYS_ALLOWED.stream().noneMatch(path::startsWith)) {
            boolean setupPage = path.equals(SETUP_URL);
            boolean needed = setupService.isSetupNeeded();
            if (needed && !setupPage) {
                response.sendRedirect(request.getContextPath() + SETUP_URL);
                return;
            }
            if (!needed && setupPage) {
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
