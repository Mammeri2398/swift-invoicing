package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.invoice.Invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Turnover of a year against the auto-entrepreneur annual ceiling. */
public final class DashboardStats {

    private static final BigDecimal HUNDRED = new BigDecimal(100);
    private static final BigDecimal WARNING_PERCENT = new BigDecimal(80);

    private final int year;
    private final BigDecimal totalAmount;
    private final BigDecimal ceiling;
    private final List<Invoice> recentInvoices;
    private final long unpaidCount;
    private final BigDecimal unpaidAmount;
    private final long draftCount;
    private final long clientCount;

    public DashboardStats(int year, BigDecimal totalAmount, BigDecimal ceiling, List<Invoice> recentInvoices,
                          long unpaidCount, BigDecimal unpaidAmount, long draftCount, long clientCount) {
        this.year = year;
        this.totalAmount = totalAmount;
        this.ceiling = ceiling;
        this.recentInvoices = List.copyOf(recentInvoices);
        this.unpaidCount = unpaidCount;
        this.unpaidAmount = unpaidAmount;
        this.draftCount = draftCount;
        this.clientCount = clientCount;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getCeiling() {
        return ceiling;
    }

    public List<Invoice> getRecentInvoices() {
        return recentInvoices;
    }

    /** Issued invoices not paid yet, all years. */
    public long getUnpaidCount() {
        return unpaidCount;
    }

    public BigDecimal getUnpaidAmount() {
        return unpaidAmount;
    }

    public long getDraftCount() {
        return draftCount;
    }

    public long getClientCount() {
        return clientCount;
    }

    /** Share of the ceiling already billed, in percent (2 decimals). */
    public BigDecimal getPercent() {
        return ceiling.signum() == 0 ? BigDecimal.ZERO
            : totalAmount.multiply(HUNDRED).divide(ceiling, 2, RoundingMode.HALF_UP);
    }

    /** Percent capped at 100, for progress bars. */
    public BigDecimal getBarPercent() {
        return getPercent().min(HUNDRED);
    }

    public BigDecimal getRemaining() {
        return ceiling.subtract(totalAmount).max(BigDecimal.ZERO);
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
