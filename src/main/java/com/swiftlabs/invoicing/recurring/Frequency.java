package com.swiftlabs.invoicing.recurring;

/** How often a recurring invoice comes back, in months. */
public enum Frequency {

    MONTHLY(1),
    QUARTERLY(3),
    SEMIANNUAL(6),
    YEARLY(12);

    private final int months;

    Frequency(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }
}
