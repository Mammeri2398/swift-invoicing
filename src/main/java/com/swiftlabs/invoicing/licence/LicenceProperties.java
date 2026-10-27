package com.swiftlabs.invoicing.licence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Licensing of on-premise installs (app.licence.*). Off unless {@code required}: development, tests
 * and the hosted deployment run without a licence.
 */
@ConfigurationProperties(prefix = "app.licence")
public record LicenceProperties(
    @DefaultValue("false") boolean required,
    /* The publisher's Ed25519 public key (Base64); empty: classpath:licence/public-key.txt. */
    String publicKey,
    @DefaultValue("30") int trialDays,
    @DefaultValue("3") int trialUsers,
    /* Companies the installation may hold during the trial (so that several can be tried). */
    @DefaultValue("2") int trialCompanies,
    /* Days before the expiry date when the warning starts. */
    @DefaultValue("30") int warningDays,
    /* Days after the expiry date with everything still working, before read-only mode. */
    @DefaultValue("15") int graceDays
) {
}
