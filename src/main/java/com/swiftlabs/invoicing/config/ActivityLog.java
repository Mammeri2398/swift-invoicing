package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.audit.AuditKind;
import com.swiftlabs.invoicing.audit.AuditRecorder;
import com.swiftlabs.invoicing.audit.AuditSubjects;
import com.swiftlabs.invoicing.common.Endpoints;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * One line in the log for each page shown and each action (save, delete, issue...), to follow what happens:
 * <pre>POST /clients/5/delete -> 302 (21 ms) [admin @ Swift-Labs] ClientController#delete</pre>
 * Pages of the application only (no CSS, scripts or fonts). Form contents are never written, and secrets in
 * addresses (reset links, codes) are masked. Logger "activity": logging.level.activity=WARN turns it off.
 * <p>
 * The same is kept in the activity journal (Réglages > Journal d'activité, see {@link AuditRecorder}) for signed-in
 * users, and for the forms sent without being signed in (password reset, invoice lookup); not for the pages shown to
 * visitors, which anyone could fill the journal with.
 */
@Component
public class ActivityLog implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("activity");
    private static final String STARTED = ActivityLog.class.getName() + ".started";
    private static final String SUBJECT = ActivityLog.class.getName() + ".subject";
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(token|password|code|secret|key)=[^&]*");

    private final AuditRecorder auditRecorder;
    private final AuditSubjects auditSubjects;

    public ActivityLog(AuditRecorder auditRecorder, AuditSubjects auditSubjects) {
        this.auditRecorder = auditRecorder;
        this.auditSubjects = auditSubjects;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            request.setAttribute(STARTED, System.nanoTime());
            // Before the action: a deleted invoice or client can still be named
            if (isJournaled(request)) {
                AuditSubjects.Subject subject = auditSubjects.of(request, method);
                if (subject != null) {
                    request.setAttribute(SUBJECT, subject);
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
        if (!(handler instanceof HandlerMethod method) || !(request.getAttribute(STARTED) instanceof Long started)) {
            return;
        }
        journal(request, response, method, failure, started);
        if (!log.isInfoEnabled()) {
            return;
        }
        // An error page shown for another address: logged under that address, not at all for a missing static file
        Object failedAddress = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (failedAddress != null && isStatic(failedAddress.toString().substring(request.getContextPath().length()))) {
            return;
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        String action = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
        String line = String.format("%-4s %s -> %d (%d ms) [%s] %s", request.getMethod(), address(request), response.getStatus(),
            millis, who(), action);
        if (failure != null) {
            log.warn("{} failed: {}", line, failure.toString());
        } else if (response.getStatus() >= 400) {
            log.warn(line);
        } else {
            log.info(line);
        }
    }

    /**
     * The request as the user sent it: not again for its error page or the access-denied page it is forwarded to (the
     * request itself is kept, with its status), nor the health checks of the server.
     */
    private void journal(HttpServletRequest request, HttpServletResponse response, HandlerMethod method, Exception failure,
                         long started) {
        if (!isJournaled(request)) {
            return;
        }
        boolean view = isView(request);
        AuditRecorder.Actor actor = AuditRecorder.Actor.of(SecurityContextHolder.getContext().getAuthentication());
        int status = failure == null ? response.getStatus() : failure instanceof AccessDeniedException ? 403 : 500;
        auditRecorder.record(view ? AuditKind.VIEW : AuditKind.ACTION, actor, request.getMethod(), address(request), status,
            (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000),
            method.getBeanType().getSimpleName() + "#" + method.getMethod().getName(), request.getRemoteAddr(),
            (AuditSubjects.Subject) request.getAttribute(SUBJECT));
    }

    private static boolean isJournaled(HttpServletRequest request) {
        if (request.getDispatcherType() != DispatcherType.REQUEST
            || request.getRequestURI().startsWith(request.getContextPath() + Endpoints.HEALTH)) {
            return false;
        }
        return !isView(request) || !AuditRecorder.Actor.of(SecurityContextHolder.getContext().getAuthentication()).isAnonymous();
    }

    private static boolean isView(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    }

    /** The address, with the query string of GET pages (filters, searches), its secrets masked. */
    private static String address(HttpServletRequest request) {
        Object failedAddress = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (failedAddress != null) {
            return failedAddress.toString();
        }
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null || !"GET".equals(request.getMethod()) ? ""
            : "?" + SECRET.matcher(query).replaceAll("$1=***"));
    }

    private static boolean isStatic(String path) {
        return Endpoints.STATIC_FOLDERS.stream().anyMatch(path::startsWith) || path.equals(Endpoints.FAVICON);
    }

    /** "admin @ Swift-Labs", "admin" before choosing a company, "anonyme" when not signed in. */
    private static String who() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "anonyme";
        }
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal && principal.getCompanyName() != null) {
            return principal.getUsername() + " @ " + principal.getCompanyName();
        }
        return authentication.getName();
    }
}
