package com.swiftlabs.invoicing.dashboard;

import com.swiftlabs.invoicing.invoice.Invoice;

import java.math.BigDecimal;
import java.util.List;

/** Turnover of a year against the auto-entrepreneur annual ceiling. */
public final class DashboardStats {

    private final int year;
    private final BigDecimal totalAmount;
    private final BigDecimal ceiling;
    private final boolean ceilingApplies;
    private final List<Invoice> recentInvoices;
    private final long unpaidCount;
    private final BigDecimal unpaidAmount;
    private final long draftCount;
    private final long clientCount;
    private final CeilingUsage usage;

    public DashboardStats(int year, BigDecimal totalAmount, BigDecimal ceiling, boolean ceilingApplies, List<Invoice> recentInvoices,
                          long unpaidCount, BigDecimal unpaidAmount, long draftCount, long clientCount) {
        this.year = year;
        this.totalAmount = totalAmount;
        this.ceiling = ceiling;
        this.ceilingApplies = ceilingApplies;
        this.recentInvoices = List.copyOf(recentInvoices);
        this.unpaidCount = unpaidCount;
        this.unpaidAmount = unpaidAmount;
        this.draftCount = draftCount;
        this.clientCount = clientCount;
        this.usage = new CeilingUsage(totalAmount, ceiling);
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /** The annual ceiling concerns auto-entrepreneurs only. */
    public boolean isCeilingApplies() {
        return ceilingApplies;
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

    public BigDecimal getPercent() {
        return usage.getPercent();
    }

    public BigDecimal getBarPercent() {
        return usage.getBarPercent();
    }

    public BigDecimal getRemaining() {
        return usage.getRemaining();
    }

    public BigDecimal getWarningThreshold() {
        return usage.getWarningThreshold();
    }

    public boolean isWarning80() {
        return usage.isWarning80();
    }

    public boolean isAlert100() {
        return usage.isAlert100();
    }
}
