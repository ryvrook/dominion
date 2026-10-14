package dev.ryvrook.dominion.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {
    @Test
    void validAmountsArePositiveWithAtMostTwoDecimals() {
        assertNull(Money.invalid(0.01));
        assertNull(Money.invalid(12.5));
        assertNull(Money.invalid(999_999_999_999.0));
        assertNotNull(Money.invalid(0));
        assertNotNull(Money.invalid(-1));
        assertNotNull(Money.invalid(0.001));
        assertNotNull(Money.invalid(1.005));
        assertNotNull(Money.invalid(Double.NaN));
        assertNotNull(Money.invalid(Double.POSITIVE_INFINITY));
        assertNotNull(Money.invalid(1e12));
    }

    @Test
    void reasonsFollowTheWordingRule() {
        for (double bad : new double[] {0, -1, 0.001, Double.NaN, 1e13}) {
            String r = Money.invalid(bad);
            assertFalse(r.contains(";") || r.contains("—") || r.contains(":"), r);
        }
    }

    @Test
    void roundingAndCentsAgree() {
        assertEquals(0.3, Money.round(0.1 + 0.2));
        assertEquals(30, Money.cents(0.1 + 0.2));
        assertEquals(1.01, Money.round(1.005 + 1e-9));
        assertTrue(Money.covers(10, 10));
        assertTrue(Money.covers(9.9999999, 10));
        assertFalse(Money.covers(9.99, 10));
    }

    @Test
    void formatPutsSingleSymbolsFirstAndWordsAfter() {
        assertEquals("$1,234", Money.format(1234, "$"));
        assertEquals("$1,234.50", Money.format(1234.5, "$"));
        assertEquals("12 coins", Money.format(12, " coins"));
        assertEquals("7", Money.format(7, ""));
        assertEquals("7", Money.format(7, null));
    }
}
