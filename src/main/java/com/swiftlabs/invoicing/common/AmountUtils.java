package com.swiftlabs.invoicing.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class AmountUtils {

    /** Public so views can use an instance (static methods are called through it). */
    public AmountUtils() {
    }

    public static BigDecimal round2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal sumLines(java.util.List<BigDecimal> totals) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal t : totals) {
            if (t != null) {
                sum = sum.add(t);
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** Quantity without currency: up to 3 decimals, French separators ("1 234,5"). */
    /** A TVA rate for display: 19 -> "19", 9.5 -> "9,5". */
    public static String formatRate(BigDecimal rate) {
        return rate == null ? "0" : rate.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static String formatQuantity(BigDecimal quantity) {
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.###", symbols).format(quantity);
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        df.setGroupingUsed(true);
        return df.format(amount) + " DA";
    }
}
