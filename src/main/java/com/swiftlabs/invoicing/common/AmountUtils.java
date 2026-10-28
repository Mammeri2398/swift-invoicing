package com.swiftlabs.invoicing.common;

import org.springframework.context.i18n.LocaleContextHolder;

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

    /**
     * A unit price as kept: up to 6 decimals (a monthly fee divided by days, 130 000 / 22 = 5 909,090909), at least
     * 2, without the trailing zeros of its column (5 000.000000 is 5 000.00).
     */
    public static BigDecimal price(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(Math.max(2, Math.min(6, value.stripTrailingZeros().scale())), RoundingMode.HALF_UP);
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
        symbols.setGroupingSeparator(groupingSeparator());
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.###", symbols).format(quantity);
    }

    /**
     * A space between thousands. On an Arabic (right-to-left) page, a no-break space: the browser keeps
     * "2 000 000,00" in one piece, where ordinary spaces would let it reorder the groups ("000,00 000 2").
     */
    private static char groupingSeparator() {
        return Language.of(LocaleContextHolder.getLocale()).isRightToLeft() ? '\u00A0' : ' ';
    }

    /** A unit price: as many decimals as it has, from 2 to 6 ("5 909,090909 DA", "5 000,00 DA"). */
    public static String formatPrice(BigDecimal price) {
        return formatPriceNumber(price) + " DA";
    }

    /** The same without the currency, for a column whose heading says "(DA)". */
    public static String formatPriceNumber(BigDecimal price) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(groupingSeparator());
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00####", symbols).format(price == null ? BigDecimal.ZERO : price);
    }

    public static String format(BigDecimal amount) {
        return formatNumber(amount) + " DA";
    }

    /** An amount without the currency, for a column whose heading says "(DA)": "45 000,00". */
    public static String formatNumber(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(groupingSeparator());
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        df.setGroupingUsed(true);
        return df.format(amount);
    }
}
