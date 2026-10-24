package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Brute-force protection and audit log, driven by Spring Security's authentication events. */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final UserRepository userRepository;
    private final SecurityProperties.Lockout lockout;
    private final Clock clock;

    public LoginAttemptListener(UserRepository userRepository, SecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.lockout = properties.lockout();
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(user -> user.recordSuccessfulLogin(LocalDateTime.now(clock)));
        log.info("Login succeeded for '{}'", username);
    }

    /** Wrong password, or unknown user (Spring Security reports both the same way). */
    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(user -> {
            user.recordFailedLogin(lockout.maxAttempts(), lockout.duration(), LocalDateTime.now(clock));
            if (user.getLockedUntil() != null && user.getFailedLoginAttempts() == 0) {
                log.warn("Account '{}' locked until {} after {} failed logins", username, user.getLockedUntil(), lockout.maxAttempts());
            }
        });
        log.warn("Login failed for '{}'", username);
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        log.warn("Login refused for locked account '{}'", event.getAuthentication().getName());
    }
}
