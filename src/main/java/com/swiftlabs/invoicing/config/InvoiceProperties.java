package com.swiftlabs.invoicing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Invoicing settings, bound from the {@code invoice.*} keys of application.properties. */
@ConfigurationProperties(prefix = "invoice")
public record InvoiceProperties(
    @DefaultValue("FA") String numberPrefix,
    @DefaultValue("AV") String creditNotePrefix,
    @DefaultValue("TVA non applicable") String vatMention,
    /* TVA rates offered on invoice lines of a company, in percent. */
    @DefaultValue({"19", "9", "0"}) List<BigDecimal> vatRates,
    @DefaultValue StampDuty stampDuty,
    @DefaultValue("DZD") String currency,
    @DefaultValue("DA") String currencySymbol,
    @DefaultValue("fr") String locale
) {

    /**
     * Droit de timbre on invoices paid in cash (code du timbre). The rate of the bracket the amount
     * falls into applies to the whole amount (TTC); the result is rounded up to the dinar, then
     * held between {@code minimum} and {@code maximum} (none when null). The rules change with the
     * lois de finances: every value is a setting (invoice.stamp-duty.*).
     */
    public record StampDuty(Boolean enabled, List<Bracket> brackets, BigDecimal minimum, BigDecimal maximum) {

        public StampDuty {
            enabled = enabled == null || enabled;
            brackets = brackets == null || brackets.isEmpty()
                ? List.of(new Bracket(new BigDecimal("30000"), BigDecimal.ONE),
                          new Bracket(new BigDecimal("100000"), new BigDecimal("1.5")),
                          new Bracket(null, new BigDecimal("2")))
                : List.copyOf(brackets);
            minimum = minimum != null ? minimum : new BigDecimal("5");
        }

        /** Duty for an amount paid in cash. */
        public BigDecimal compute(BigDecimal amount) {
            if (!enabled || amount == null || amount.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal rate = brackets.get(brackets.size() - 1).rate();
            for (Bracket bracket : brackets) {
                if (bracket.upTo() == null || amount.compareTo(bracket.upTo()) <= 0) {
                    rate = bracket.rate();
                    break;
                }
            }
            BigDecimal duty = amount.multiply(rate).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
            duty = duty.max(minimum);
            if (maximum != null) {
                duty = duty.min(maximum);
            }
            return duty.setScale(2, RoundingMode.UNNECESSARY);
        }
    }

    /** Rate in percent for amounts up to {@code upTo} DA included (null: every amount above the previous bracket). */
    public record Bracket(BigDecimal upTo, BigDecimal rate) {
    }
}
