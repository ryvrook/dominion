package dev.ryvrook.dominion.economy;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dominion amounts to Coinage minor units and back, for every number of decimals Coinage allows. */
class CoinageAmountsTest {
    @Test
    void amountsBecomeMinorUnitsExactly() {
        assertEquals(OptionalLong.of(1234), CoinageAmounts.toMinor(12.34, 2));
        assertEquals(OptionalLong.of(123_400), CoinageAmounts.toMinor(12.34, 4));
        assertEquals(OptionalLong.of(123), CoinageAmounts.toMinor(12.3, 1));
        assertEquals(OptionalLong.of(12), CoinageAmounts.toMinor(12, 0));
        assertEquals(OptionalLong.of(1), CoinageAmounts.toMinor(0.01, 2));
        assertEquals(OptionalLong.of(0), CoinageAmounts.toMinor(0, 2));
    }

    @Test
    void amountsThatDoNotFitAreRefused() {
        assertTrue(CoinageAmounts.toMinor(12.34, 0).isEmpty(), "cents in a whole currency");
        assertTrue(CoinageAmounts.toMinor(12.34, 1).isEmpty(), "cents in a one decimal currency");
        assertTrue(CoinageAmounts.toMinor(-1, 2).isEmpty());
        assertTrue(CoinageAmounts.toMinor(Double.NaN, 2).isEmpty());
        assertTrue(CoinageAmounts.toMinor(Double.POSITIVE_INFINITY, 2).isEmpty());
        assertTrue(CoinageAmounts.toMinor(1e17, 4).isEmpty(), "beyond a long");
    }

    @Test
    void minorUnitsBecomeAmountsRoundedDownToCents() {
        assertEquals(12.34, CoinageAmounts.toAmount(1234, 2));
        assertEquals(12.34, CoinageAmounts.toAmount(123_456, 4));
        assertEquals(12.3, CoinageAmounts.toAmount(123, 1));
        assertEquals(5.0, CoinageAmounts.toAmount(5, 0));
        assertEquals(0.0, CoinageAmounts.toAmount(99, 4));
        assertEquals(0.0, CoinageAmounts.toAmount(5, -1), "an unknown currency reads as nothing");
    }

    @Test
    void roundTripsKeepTheAmount() {
        for (int d = 0; d <= 4; d++) {
            for (double a : new double[] {0, 1, 7, 250, 1_000_000}) {
                assertEquals(a, CoinageAmounts.toAmount(CoinageAmounts.toMinor(a, d).getAsLong(), d), a + " at " + d);
            }
        }
        assertEquals(19.99, CoinageAmounts.toAmount(CoinageAmounts.toMinor(19.99, 3).getAsLong(), 3));
    }

    @Test
    void floorsAndSteps() {
        assertEquals(123, CoinageAmounts.floorMinor(12.34, 1));
        assertEquals(12, CoinageAmounts.floorMinor(12.99, 0));
        assertEquals(0, CoinageAmounts.floorMinor(-3, 2));
        assertEquals(1.0, CoinageAmounts.step(0));
        assertEquals(0.1, CoinageAmounts.step(1));
        assertEquals(0.01, CoinageAmounts.step(2));
        assertEquals(0.01, CoinageAmounts.step(4));
        assertEquals(150, CoinageBanks.interestOf(10_000, 1.5));
        assertEquals(0, CoinageBanks.interestOf(99, 1));
        assertEquals(0, CoinageBanks.interestOf(-5, 10));
    }
}
