package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.audit.AuditKind;
import com.swiftlabs.invoicing.audit.AuditRecorder;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Brute-force protection, log and activity journal of the sign-ins, driven by Spring Security's authentication events.
 * A sign-in with two-factor login is journaled once the code is accepted (TwoFactorLoginController).
 */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final UserRepository userRepository;
    private final SecurityProperties.Lockout lockout;
    private final Clock clock;
    private final AuditRecorder auditRecorder;

    public LoginAttemptListener(UserRepository userRepository, SecurityProperties properties, Clock clock,
                                AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.lockout = properties.lockout();
        this.clock = clock;
        this.auditRecorder = auditRecorder;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        // The code is still to come: failures are not reset yet (see TwoFactorService#verifyLogin),
        // otherwise logging in again with the password would give unlimited tries at the code
        if (event.getAuthentication().getPrincipal() instanceof AppUserPrincipal principal && principal.isTwoFactorEnabled()) {
            log.info("Password accepted for '{}', two-factor code required", username);
            return;
        }
        userRepository.findByUsername(username).ifPresent(user -> user.recordSuccessfulLogin(LocalDateTime.now(clock)));
        log.info("Login succeeded for '{}'", username);
        auditRecorder.record(AuditKind.SIGN_IN, AuditRecorder.Actor.of(event.getAuthentication()), ip(event.getAuthentication()));
    }

    /** Wrong password, or unknown user (Spring Security reports both the same way). */
    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        Optional<User> known = userRepository.findByUsername(username);
        known.ifPresent(user -> {
            user.recordFailedLogin(lockout.maxAttempts(), lockout.duration(), LocalDateTime.now(clock));
            if (user.getLockedUntil() != null && user.getFailedLoginAttempts() == 0) {
                log.warn("Account '{}' locked until {} after {} failed logins", username, user.getLockedUntil(), lockout.maxAttempts());
            }
        });
        log.warn("Login failed for '{}'", username);
        auditRecorder.record(AuditKind.SIGN_IN_FAILED, actor(username, known), ip(event.getAuthentication()));
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        String username = event.getAuthentication().getName();
        log.warn("Login refused for locked account '{}'", username);
        auditRecorder.record(AuditKind.SIGN_IN_LOCKED, actor(username, userRepository.findByUsername(username)),
            ip(event.getAuthentication()));
    }

    /** The name typed; with the account it belongs to, so that the failure shows in the journal of its companies. */
    private static AuditRecorder.Actor actor(String username, Optional<User> known) {
        return new AuditRecorder.Actor(known.map(User::getId).orElse(null), username, null, null);
    }

    private static String ip(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : null;
    }
}
