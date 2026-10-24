package com.swiftlabs.invoicing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** Security settings, bound from the {@code app.security.*} keys of application.properties. */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    @DefaultValue Password password,
    @DefaultValue Lockout lockout,
    @DefaultValue("60m") Duration resetTokenValidity,
    @DefaultValue RateLimit rateLimit,
    @DefaultValue TwoFactor twoFactor,
    /* Password of the admin account created on first start; generated when empty. */
    String initialAdminPassword
) {

    /** Max length bounds the hashing cost of a submitted password. */
    public record Password(@DefaultValue("8") int minLength, @DefaultValue("128") int maxLength) {
    }

    /** The account is locked for {@code duration} after {@code maxAttempts} failed logins in a row. */
    public record Lockout(@DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration duration) {
    }

    /**
     * Requests allowed per client IP address (see RateLimitFilter). Complements the per-account
     * lockout: it also slows down attacks spread over many accounts, and mail flooding.
     */
    public record RateLimit(Limit login, Limit passwordReset, Limit lookup) {

        /** Defaults for limits that are not configured. */
        public RateLimit {
            login = login != null ? login : new Limit(10, Duration.ofMinutes(5));
            passwordReset = passwordReset != null ? passwordReset : new Limit(5, Duration.ofMinutes(15));
            lookup = lookup != null ? lookup : new Limit(30, Duration.ofMinutes(1));
        }
    }

    /**
     * Two-factor login. Users holding any of the {@code requiredFor} privileges must set it up
     * before using the application (empty: optional for everyone).
     */
    public record TwoFactor(List<String> requiredFor, @DefaultValue("Swift Invoicing") String issuer) {

        public TwoFactor {
            requiredFor = requiredFor == null ? List.of()
                : requiredFor.stream().map(String::trim).filter(code -> !code.isEmpty()).toList();
        }
    }

    /** At most {@code requests} per {@code window}. */
    public record Limit(int requests, Duration window) {

        public Limit {
            if (requests < 1 || window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("A rate limit needs requests >= 1 and a positive window");
            }
        }
    }
}
