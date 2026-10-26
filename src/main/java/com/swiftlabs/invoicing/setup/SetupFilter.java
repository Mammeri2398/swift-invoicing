package com.swiftlabs.invoicing.setup;

import com.swiftlabs.invoicing.common.Endpoints;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/** Until the first-run setup is done, every page leads to it; afterwards the setup page is closed. */
public class SetupFilter extends OncePerRequestFilter {

    private static final List<String> ALWAYS_ALLOWED = Stream.concat(Endpoints.STATIC_FOLDERS.stream(),
        Stream.of(Endpoints.FAVICON, Endpoints.ERROR, Endpoints.HEALTH)).toList();

    private final SetupService setupService;

    public SetupFilter(SetupService setupService) {
        this.setupService = setupService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (ALWAYS_ALLOWED.stream().noneMatch(path::startsWith)) {
            boolean setupPage = path.equals(Endpoints.SETUP);
            boolean needed = setupService.isSetupNeeded();
            if (needed && !setupPage) {
                response.sendRedirect(request.getContextPath() + Endpoints.SETUP);
                return;
            }
            if (!needed && setupPage) {
                response.sendRedirect(request.getContextPath() + Endpoints.LOGIN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
