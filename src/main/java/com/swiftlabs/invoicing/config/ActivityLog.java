package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.Endpoints;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class ActivityLog implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("activity");
    private static final String STARTED = ActivityLog.class.getName() + ".started";
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(token|password|code|secret|key)=[^&]*");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            request.setAttribute(STARTED, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
        if (!log.isInfoEnabled() || !(handler instanceof HandlerMethod method) || !(request.getAttribute(STARTED) instanceof Long started)) {
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
