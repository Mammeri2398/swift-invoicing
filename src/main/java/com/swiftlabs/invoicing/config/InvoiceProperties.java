package com.swiftlabs.invoicing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/** Invoicing settings, bound from the {@code invoice.*} keys of application.properties. */
@ConfigurationProperties(prefix = "invoice")
public record InvoiceProperties(
    @DefaultValue("FA") String numberPrefix,
    @DefaultValue("TVA non applicable") String vatMention,
    @DefaultValue("5000000") BigDecimal annualCeiling,
    @DefaultValue("DZD") String currency,
    @DefaultValue("DA") String currencySymbol,
    @DefaultValue("fr") String locale
) {
}
