package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.config.SecurityProperties.Limit;
import com.swiftlabs.invoicing.config.SecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limits, per client IP address, the requests that can be abused without being logged in:
 * login attempts, password reset (link requests and new passwords) and the public invoice lookup.
 * Beyond the limit: HTTP 429 with a Retry-After header.
 *
 * <p>The client IP is {@code request.getRemoteAddr()}: behind a reverse proxy, Tomcat replaces it with
 * the X-Forwarded-For address only for trusted (internal) proxies (server.forward-headers-strategy=native),
 * so a client cannot pick its own address by sending the header.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final SecurityProperties.RateLimit limits;

    public RateLimitFilter(RateLimiter rateLimiter, SecurityProperties properties) {
        this.rateLimiter = rateLimiter;
        this.limits = properties.rateLimit();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String rule = rule(request);
        if (rule != null) {
            String ip = request.getRemoteAddr();
            RateLimiter.Decision decision = rateLimiter.tryAcquire(rule + ":" + ip, limit(rule));
            if (!decision.allowed()) {
                log.warn("Rate limit '{}' exceeded by {} on {} {}", rule, ip, request.getMethod(), request.getRequestURI());
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The rule protecting this request, or null when it is not limited. */
    private static String rule(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if ("POST".equals(method) && (path.equals(Endpoints.PERFORM_LOGIN) || path.equals(Endpoints.LOGIN_VERIFY))) {
            return "login";
        }
        if ("POST".equals(method) && (path.equals(Endpoints.FORGOT_PASSWORD_SEND) || path.equals(Endpoints.RESET_PASSWORD_SAVE))) {
            return "password-reset";
        }
        String number = request.getParameter("number");
        if ("GET".equals(method) && path.equals(Endpoints.INVOICE_LOOKUP) && number != null && !number.isBlank()) {
            return "lookup";
        }
        return null;
    }

    private Limit limit(String rule) {
        return switch (rule) {
            case "login" -> limits.login();
            case "password-reset" -> limits.passwordReset();
            default -> limits.lookup();
        };
    }
}
