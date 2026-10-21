package com.swiftlabs.invoicing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class AmountUtils {

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
