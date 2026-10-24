package com.swiftlabs.invoicing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Security settings, bound from the {@code app.security.*} keys of application.properties. */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    @DefaultValue Password password,
    @DefaultValue Lockout lockout,
    @DefaultValue("60m") Duration resetTokenValidity,
    /* Password of the admin account created on first start; generated when empty. */
    String initialAdminPassword
) {

    /** Max length bounds the hashing cost of a submitted password. */
    public record Password(@DefaultValue("8") int minLength, @DefaultValue("128") int maxLength) {
    }

    /** The account is locked for {@code duration} after {@code maxAttempts} failed logins in a row. */
    public record Lockout(@DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration duration) {
    }
}
