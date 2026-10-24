package com.swiftlabs.invoicing.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Turnover billed against the annual ceiling chosen in the settings. */
public final class CeilingUsage {

    private static final BigDecimal HUNDRED = new BigDecimal(100);
    private static final BigDecimal WARNING_PERCENT = new BigDecimal(80);

    private final BigDecimal billed;
    private final BigDecimal ceiling;

    public CeilingUsage(BigDecimal billed, BigDecimal ceiling) {
        this.billed = billed;
        this.ceiling = ceiling;
    }

    public BigDecimal getBilled() {
        return billed;
    }

    public BigDecimal getCeiling() {
        return ceiling;
    }

    /** Share of the ceiling already billed, in percent (2 decimals). */
    public BigDecimal getPercent() {
        return ceiling.signum() == 0 ? BigDecimal.ZERO
            : billed.multiply(HUNDRED).divide(ceiling, 2, RoundingMode.HALF_UP);
    }

    /** Percent capped at 100, for progress bars. */
    public BigDecimal getBarPercent() {
        return getPercent().min(HUNDRED);
    }

    public BigDecimal getRemaining() {
        return ceiling.subtract(billed).max(BigDecimal.ZERO);
    }

    public BigDecimal getWarningThreshold() {
        return ceiling.multiply(WARNING_PERCENT).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Between 80 % and 100 % of the ceiling. */
    public boolean isWarning80() {
        return getPercent().compareTo(WARNING_PERCENT) >= 0 && !isAlert100();
    }

    /** Ceiling reached or exceeded. */
    public boolean isAlert100() {
        return getPercent().compareTo(HUNDRED) >= 0;
    }
}
