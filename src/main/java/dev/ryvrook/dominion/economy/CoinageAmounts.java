package dev.ryvrook.dominion.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

/**
 * Conversion between Dominion amounts, doubles with at most two decimals, and Coinage amounts, whole minor units
 * of a currency with 0 to 4 decimals. Pure, so both ways are tested on their own.
 */
final class CoinageAmounts {
    private CoinageAmounts() {}

    /**
     * The minor units of an amount, exactly. Empty when the amount is not a finite number, is negative, has more
     * decimals than the currency or does not fit a long.
     */
    static OptionalLong toMinor(double amount, int decimals) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0 || decimals < 0) {
            return OptionalLong.empty();
        }
        BigDecimal minor = BigDecimal.valueOf(Money.round(amount)).movePointRight(decimals);
        if (minor.stripTrailingZeros().scale() > 0) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(minor.longValueExact());
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    /** The minor units of an amount rounded down to what the currency can hold, zero for nothing or a bad amount. */
    static long floorMinor(double amount, int decimals) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0 || decimals < 0) {
            return 0;
        }
        BigDecimal minor = BigDecimal.valueOf(Money.round(amount)).movePointRight(decimals).setScale(0, RoundingMode.DOWN);
        return minor.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : minor.longValue();
    }

    /**
     * The minor units of a charge rounded up to what the currency can hold, so a fraction of a coin is charged as a
     * whole coin instead of nothing. Zero for nothing or a bad amount.
     */
    static long ceilMinor(double amount, int decimals) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0 || decimals < 0) {
            return 0;
        }
        BigDecimal minor = BigDecimal.valueOf(Money.round(amount)).movePointRight(decimals).setScale(0, RoundingMode.UP);
        return minor.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : minor.longValue();
    }

    /** A Dominion amount for minor units, rounded towards zero to whole cents so Dominion never shows more than is held. */
    static double toAmount(long minor, int decimals) {
        if (decimals < 0) {
            return 0;
        }
        return BigDecimal.valueOf(minor, decimals).setScale(2, RoundingMode.DOWN).doubleValue();
    }

    /** The smallest amount Dominion can move in the currency, a cent at two decimals or more. */
    static double step(int decimals) {
        if (decimals >= 2) {
            return 0.01;
        }
        return decimals == 1 ? 0.1 : 1.0;
    }
}
