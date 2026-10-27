package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SignedInUser;
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
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Points each request of a signed-in user at their company. A user who has not chosen one yet (several companies)
 * is sent to the choice; until then only the shared tables can be reached.
 *
 * The access is checked again on every page: taken away, or its company deactivated, the user leaves the company at
 * once (and chooses another); their profile there changed, their privileges follow at once.
 */
public class CompanyFilter extends OncePerRequestFilter {

    /** Reachable without a company: the choice itself, signing out, and what every page needs. */
    private static final List<String> WITHOUT_COMPANY = Stream.concat(Endpoints.STATIC_FOLDERS.stream(),
        Stream.of(Endpoints.COMPANIES, Endpoints.PERFORM_LOGOUT, Endpoints.ERROR, Endpoints.LOGIN_VERIFY,
            Endpoints.ACCOUNT_PASSWORD_PAGE)).toList();

    private final CompanyAccessService accessService;

    public CompanyFilter(CompanyAccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal signedIn)) {
            chain.doFilter(request, response);
            return;
        }
        AppUserPrincipal principal = signedIn.hasCompany() && !isStatic(request) ? current(signedIn, request, response) : signedIn;
        if (!principal.hasCompany() && !isAllowedWithoutCompany(request)) {
            response.sendRedirect(request.getContextPath() + Endpoints.COMPANIES + Endpoints.COMPANY_SELECT);
            return;
        }
        try (CompanyContext.Scope ignored = CompanyContext.open(
                principal.hasCompany() ? principal.getCompanySchema() : CompanySchemas.SHARED)) {
            chain.doFilter(request, response);
        }
    }

    /** The user as they are now in their company: gone from it without an active access, with its current privileges. */
    private AppUserPrincipal current(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal now = principal.withCompany(accessService.findActive(principal.getId(), principal.getCompanyId()).orElse(null));
        if (now.getCompanyId() == null || !now.getAuthorities().equals(principal.getAuthorities())
            || !Objects.equals(now.getProfileName(), principal.getProfileName())) {
            SignedInUser.replace(now, request, response);
            return now;
        }
        return principal;
    }

    private static boolean isStatic(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return Endpoints.STATIC_FOLDERS.stream().anyMatch(path::startsWith) || path.equals(Endpoints.FAVICON);
    }

    private static boolean isAllowedWithoutCompany(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return WITHOUT_COMPANY.stream().anyMatch(path::startsWith);
    }
}
