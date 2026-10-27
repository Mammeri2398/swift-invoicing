package com.swiftlabs.invoicing.company;

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
 * Points each request of a signed-in user at their company. A user who has not chosen one yet (several companies)
 * is sent to the choice; until then only the shared tables can be reached.
 */
public class CompanyFilter extends OncePerRequestFilter {

    /** Reachable without a company: the choice itself, signing out, and what every page needs. */
    private static final List<String> WITHOUT_COMPANY = Stream.concat(Endpoints.STATIC_FOLDERS.stream(),
        Stream.of(Endpoints.COMPANIES, Endpoints.PERFORM_LOGOUT, Endpoints.ERROR, Endpoints.LOGIN_VERIFY,
            Endpoints.ACCOUNT_PASSWORD_PAGE)).toList();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        if (!principal.hasCompany() && !isAllowedWithoutCompany(request)) {
            response.sendRedirect(request.getContextPath() + Endpoints.COMPANIES + Endpoints.COMPANY_SELECT);
            return;
        }
        try (CompanyContext.Scope ignored = CompanyContext.open(
                principal.hasCompany() ? principal.getCompanySchema() : CompanySchemas.SHARED)) {
            chain.doFilter(request, response);
        }
    }

    private static boolean isAllowedWithoutCompany(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return WITHOUT_COMPANY.stream().anyMatch(path::startsWith);
    }
}
