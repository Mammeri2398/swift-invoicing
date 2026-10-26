package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.access.auth.ForcePasswordChangeFilter;
import com.swiftlabs.invoicing.access.auth.RateLimitFilter;
import com.swiftlabs.invoicing.access.auth.RateLimiter;
import com.swiftlabs.invoicing.access.twofactor.TwoFactorFilter;
import com.swiftlabs.invoicing.access.twofactor.TwoFactorLoginSuccessHandler;
import com.swiftlabs.invoicing.licence.LicenceFilter;
import com.swiftlabs.invoicing.licence.LicenceService;
import com.swiftlabs.invoicing.setup.SetupFilter;
import com.swiftlabs.invoicing.setup.SetupService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.Map;

/**
 * Web security. Only public URLs are listed here; every other request requires a login, and
 * what a user may do is checked on the controllers with {@code @PreAuthorize("hasAuthority('...')")}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
        "/css/**", "/js/**", "/fonts/**", "/images/**", "/favicon.ico", "/error",
        "/login", "/forgot-password", "/forgot-password/send", "/reset-password", "/reset-password/save",
        "/invoices/lookup",
        // First-run setup of an on-premise install: open only while no user exists (SetupFilter)
        "/setup",
        // Status only (UP/DOWN), no details: see management.* in application.properties
        "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
    };

    /**
     * Everything is served by the application itself (no CDN, no inline script).
     * Inline style attributes are allowed for computed widths (progress bars).
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self'",
        "style-src-attr 'unsafe-inline'",
        "img-src 'self' data:",
        "font-src 'self'",
        "connect-src 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'",
        "base-uri 'self'",
        "object-src 'none'");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry, RateLimiter rateLimiter,
                                           SecurityProperties properties,
                                           TwoFactorLoginSuccessHandler loginSuccessHandler,
                                           SetupService setupService, LicenceService licenceService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/perform-login")
                .successHandler(loginSuccessHandler)
                // Same message for every failure (unknown user, wrong password, locked, disabled)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/perform-logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                // No limit on parallel sessions, but they are tracked so they can be ended
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredUrl("/login?expired"))
            .exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"))
            .csrf(csrf -> csrf.csrfTokenRequestHandler(eagerCsrfTokenHandler()))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy(
                    "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
            // Per-IP limits on login, password reset and the public lookup (HTTP 429 beyond)
            .addFilterBefore(new RateLimitFilter(rateLimiter, properties), UsernamePasswordAuthenticationFilter.class)
            // First-run setup (on-premise): every page leads to it until the administrator exists
            .addFilterBefore(new SetupFilter(setupService), RateLimitFilter.class)
            // On-premise licence: read-only once it (or the trial) is over
            .addFilterAfter(new LicenceFilter(licenceService), AuthorizationFilter.class)
            // Code still to type, or two-factor login to set up, before the forced password change check
            .addFilterAfter(new TwoFactorFilter(properties.twoFactor().requiredFor()), AuthorizationFilter.class)
            .addFilterAfter(new ForcePasswordChangeFilter(), TwoFactorFilter.class);
        return http.build();
    }

    /**
     * Argon2 for new hashes, behind a delegating encoder: hashes carry their algorithm
     * ({argon2}...), so the algorithm or its parameters can change later. Hashes stored before
     * the prefix existed are still checked with Argon2, then re-encoded at the next login
     * (see AppUserDetailsService#updatePassword).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("argon2",
            Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder()));
        encoder.setDefaultPasswordEncoderForMatches(argon2);
        return encoder;
    }

    /** Asks for the two-factor code when needed, then back to the page that required the login, otherwise "/". */
    @Bean
    public TwoFactorLoginSuccessHandler loginSuccessHandler() {
        return new TwoFactorLoginSuccessHandler();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the session registry when HTTP sessions end. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** Publishes login successes and failures (lockout and audit, see LoginAttemptListener). */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }

    /**
     * Loads the CSRF token at the start of every request instead of on first use.
     * With deferred loading, Thymeleaf asks for the token while rendering a form; on pages
     * without a session yet (login) the token can then no longer be stored because the
     * response is already committed, and the page is cut off. Keeps the BREACH-safe masking.
     */
    private XorCsrfTokenRequestAttributeHandler eagerCsrfTokenHandler() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
