package dev.ryvrook.dominion.economy;

import java.util.Locale;

/** Amount validation, rounding and display. */
final class Money {
    private Money() {}

    private static final double EPSILON = 1e-6;

    /** Rounds to whole cents. */
    static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    /** Null when the amount is a positive number with at most two decimals, otherwise the reason. */
    static String invalid(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "That is not a valid amount";
        }
        if (amount <= 0) {
            return "The amount must be more than zero";
        }
        double cents = amount * 100.0;
        if (Math.abs(cents - Math.rint(cents)) > EPSILON) {
            return "Amounts can have at most two decimals";
        }
        if (amount >= 1e12) {
            return "That amount is too large";
        }
        return null;
    }

    static boolean covers(double balance, double amount) {
        return balance + EPSILON >= amount;
    }

    static long cents(double amount) {
        return Math.round(amount * 100.0);
    }

    static String format(double amount, String symbol) {
        double v = round(amount);
        String n = v == Math.rint(v)
                ? String.format(Locale.ROOT, "%,d", (long) v)
                : String.format(Locale.ROOT, "%,.2f", v);
        if (symbol == null || symbol.isEmpty()) {
            return n;
        }
        boolean prefix = symbol.length() == 1 && !Character.isLetter(symbol.charAt(0));
        return prefix ? symbol + n : n + symbol;
    }
}
