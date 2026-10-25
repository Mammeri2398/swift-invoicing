package com.swiftlabs.invoicing.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Turnover of one year, month by month: issued and paid invoices, before TVA (HT), by month of
 * issue; cancelled invoices are shown apart and never counted. For a company, the TVA collected too.
 */
public final class TurnoverReport {

    /** One month: {@code billed} = {@code paid} + {@code pending}, all HT; {@code vat} on the billed invoices. */
    public record Month(int number, String name, long invoiceCount, BigDecimal billed, BigDecimal paid,
                        BigDecimal pending, BigDecimal vat, long cancelledCount, BigDecimal cancelledAmount,
                        BigDecimal cumulative, boolean overCeiling) {
    }

    private final int year;
    private final List<Month> months;
    private final CeilingUsage usage;
    private final boolean company;

    public TurnoverReport(int year, List<Month> months, BigDecimal ceiling, boolean company) {
        this.year = year;
        this.months = List.copyOf(months);
        this.usage = new CeilingUsage(sum(months, Month::billed), ceiling);
        this.company = company;
    }

    /** A company: TVA collected shown, no annual ceiling (auto-entrepreneur only). */
    public boolean isCompany() {
        return company;
    }

    public BigDecimal getVat() {
        return sum(months, Month::vat);
    }

    public int getYear() {
        return year;
    }

    public List<Month> getMonths() {
        return months;
    }

    public CeilingUsage getUsage() {
        return usage;
    }

    public BigDecimal getBilled() {
        return usage.getBilled();
    }

    public BigDecimal getPaid() {
        return sum(months, Month::paid);
    }

    public BigDecimal getPending() {
        return sum(months, Month::pending);
    }

    public long getInvoiceCount() {
        return months.stream().mapToLong(Month::invoiceCount).sum();
    }

    public long getCancelledCount() {
        return months.stream().mapToLong(Month::cancelledCount).sum();
    }

    public BigDecimal getCancelledAmount() {
        return sum(months, Month::cancelledAmount);
    }

    /** Highest monthly turnover, the 100 % of the chart (never zero). */
    public BigDecimal getBestMonth() {
        BigDecimal best = months.stream().map(Month::billed).reduce(BigDecimal.ZERO, BigDecimal::max);
        return best.signum() == 0 ? BigDecimal.ONE : best;
    }

    private static BigDecimal sum(List<Month> months, Function<Month, BigDecimal> amount) {
        return months.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
