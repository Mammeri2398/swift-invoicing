package com.swiftlabs.invoicing.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class AmountInWords {

    private static final String[] UNITS = {
        "zéro", "un", "deux", "trois", "quatre", "cinq",
        "six", "sept", "huit", "neuf", "dix", "onze",
        "douze", "treize", "quatorze", "quinze", "seize",
        "dix-sept", "dix-huit", "dix-neuf"
    };

    private static final String[] TENS = {
        "vingt", "trente", "quarante", "cinquante", "soixante"
    };

    public AmountInWords() {
    }

    public static String convert(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal[] parts = amount.divideAndRemainder(BigDecimal.ONE);
        long dinars = parts[0].longValue();
        BigDecimal remainder = parts[1];
        int centimes = remainder.abs().multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValue();

        StringBuilder sb = new StringBuilder();
        sb.append("Arrêtée la présente facture à la somme de : ");
        sb.append(convertInteger(dinars));
        if (dinars == 1) {
            sb.append(" dinar algérien");
        } else {
            sb.append(" dinars algériens");
        }
        if (centimes > 0) {
            sb.append(" et ").append(convertBelowThousand(centimes));
            if (centimes == 1) {
                sb.append(" centime");
            } else {
                sb.append(" centimes");
            }
        }
        sb.append(".");
        return sb.toString();
    }

    private static String convertInteger(long n) {
        if (n == 0) {
            return "zéro";
        }
        StringBuilder sb = new StringBuilder();
        long remaining = n;
        if (remaining >= 1_000_000) {
            long millions = remaining / 1_000_000;
            if (millions == 1) {
                sb.append("un million");
            } else {
                sb.append(convertBelowThousand((int) millions)).append(" millions");
            }
            remaining %= 1_000_000;
            if (remaining > 0) {
                sb.append(" ");
            }
        }
        if (remaining >= 1000) {
            long thousands = remaining / 1000;
            if (thousands == 1) {
                sb.append("mille");
            } else {
                sb.append(convertBelowThousand((int) thousands)).append(" mille");
            }
            remaining %= 1000;
            if (remaining > 0) {
                sb.append(" ");
            }
        }
        if (remaining > 0) {
            sb.append(convertBelowThousand((int) remaining));
        }
        return sb.toString();
    }

    private static String convertBelowThousand(int n) {
        if (n == 0) {
            return "";
        }
        if (n >= 100) {
            int hundreds = n / 100;
            int rest = n % 100;
            String h;
            if (hundreds == 1) {
                h = "cent";
            } else {
                h = convertBelowHundred(hundreds) + " cent";
            }
            if (rest == 0 && hundreds > 1) {
                h = h + "s";
            }
            if (rest > 0) {
                h = h + " " + convertBelowHundred(rest);
            }
            return h;
        }
        return convertBelowHundred(n);
    }

    private static String convertBelowHundred(int n) {
        if (n < 20) {
            return UNITS[n];
        }
        if (n < 70) {
            int t = n / 10;
            int u = n % 10;
            String ten = TENS[t - 2];
            if (u == 0) {
                return ten;
            }
            if (u == 1) {
                return ten + " et un";
            }
            return ten + "-" + UNITS[u];
        }
        if (n < 80) {
            int u = n - 60;
            if (u == 11) {
                return "soixante et onze";
            }
            return "soixante-" + UNITS[u];
        }
        if (n == 80) {
            return "quatre-vingts";
        }
        if (n <= 91) {
            int u = n - 80;
            if (u == 1) {
                return "quatre-vingt-un";
            }
            return "quatre-vingt-" + UNITS[u];
        }
        int u = n - 80;
        return "quatre-vingt-" + UNITS[u];
    }

    public static void main(String[] args) {
        List<Object[]> cases = new ArrayList<Object[]>();
        cases.add(new Object[]{new BigDecimal("0"), "zéro"});
        cases.add(new Object[]{new BigDecimal("1"), "un"});
        cases.add(new Object[]{new BigDecimal("21"), "vingt et un"});
        cases.add(new Object[]{new BigDecimal("71"), "soixante et onze"});
        cases.add(new Object[]{new BigDecimal("80"), "quatre-vingts"});
        cases.add(new Object[]{new BigDecimal("100"), "cent"});
        cases.add(new Object[]{new BigDecimal("1000"), "mille"});
        cases.add(new Object[]{new BigDecimal("1234.56"), "mille deux cent trente-quatre"});
        for (Object[] c : cases) {
            BigDecimal input = (BigDecimal) c[0];
            String expected = (String) c[1];
            String actual = convertIntegerPart(input);
            System.out.println(input + " -> " + actual + " | expected " + expected + " | " + (actual.equals(expected) ? "OK" : "FAIL"));
        }
        System.out.println(convert(new BigDecimal("1234.56")));
    }

    private static String convertIntegerPart(BigDecimal amount) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal[] parts = amount.divideAndRemainder(BigDecimal.ONE);
        return convertInteger(parts[0].longValue());
    }
}
