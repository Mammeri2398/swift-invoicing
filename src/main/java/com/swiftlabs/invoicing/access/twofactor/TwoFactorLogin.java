package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

/**
 * The state between the password and the code. After the password, the session holds an
 * authentication with no privilege at all, only {@link #PENDING_AUTHORITY}; the full one waits in
 * the session and replaces it once the code is accepted. So a page forgotten by the filters
 * still refuses access: every controller checks a privilege.
 */
public final class TwoFactorLogin {

    public static final String PENDING_AUTHORITY = "TWO_FACTOR_PENDING";
    private static final String PENDING_ATTRIBUTE = TwoFactorLogin.class.getName() + ".PENDING";

    private static final SecurityContextRepository CONTEXT_REPOSITORY = new HttpSessionSecurityContextRepository();

    private TwoFactorLogin() {
    }

    public static boolean isPending(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> PENDING_AUTHORITY.equals(a.getAuthority()));
    }

    /** After a correct password: keeps the full authentication aside and grants nothing yet. */
    static void begin(Authentication full, HttpServletRequest request, HttpServletResponse response) {
        request.getSession().setAttribute(PENDING_ATTRIBUTE, full);
        AppUserPrincipal principal = (AppUserPrincipal) full.getPrincipal();
        save(UsernamePasswordAuthenticationToken.authenticated(principal, null,
            List.of(new SimpleGrantedAuthority(PENDING_AUTHORITY))), request, response);
    }

    /** After a correct code: the full authentication, under a new session id. */
    static Authentication complete(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        Authentication full = (Authentication) session.getAttribute(PENDING_ATTRIBUTE);
        session.removeAttribute(PENDING_ATTRIBUTE);
        request.changeSessionId();
        save(full, request, response);
        return full;
    }

    /** Replaces the authentication of the current session (e.g. with an updated principal). */
    static void save(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        CONTEXT_REPOSITORY.saveContext(context, request, response);
    }
}
