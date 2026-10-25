package com.swiftlabs.invoicing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

/** Invoicing settings, bound from the {@code invoice.*} keys of application.properties. */
@ConfigurationProperties(prefix = "invoice")
public record InvoiceProperties(
    @DefaultValue("FA") String numberPrefix,
    @DefaultValue("AV") String creditNotePrefix,
    @DefaultValue("TVA non applicable") String vatMention,
    /* TVA rates offered on invoice lines of a company, in percent. */
    @DefaultValue({"19", "9", "0"}) List<BigDecimal> vatRates,
    @DefaultValue("DZD") String currency,
    @DefaultValue("DA") String currencySymbol,
    @DefaultValue("fr") String locale
) {
}
