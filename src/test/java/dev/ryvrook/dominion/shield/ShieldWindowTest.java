package dev.ryvrook.dominion.shield;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldWindowTest {
    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    @Test
    void windowInsideOneDay() {
        assertEquals(8 * 60, ShieldWindow.minutesLeft(10, 8, at(10, 0)));
        assertEquals(1, ShieldWindow.minutesLeft(10, 8, at(17, 59)));
        assertEquals(0, ShieldWindow.minutesLeft(10, 8, at(18, 0)));
        assertEquals(0, ShieldWindow.minutesLeft(10, 8, at(9, 59)));
    }

    @Test
    void windowWrapsPastMidnight() {
        // 22:00 for 8 hours runs until 06:00 the next morning.
        assertEquals(8 * 60, ShieldWindow.minutesLeft(22, 8, at(22, 0)));
        assertEquals(6 * 60 + 1, ShieldWindow.minutesLeft(22, 8, at(23, 59)));
        assertEquals(6 * 60, ShieldWindow.minutesLeft(22, 8, at(0, 0)));
        assertEquals(1, ShieldWindow.minutesLeft(22, 8, at(5, 59)));
        assertEquals(0, ShieldWindow.minutesLeft(22, 8, at(6, 0)));
        assertEquals(0, ShieldWindow.minutesLeft(22, 8, at(21, 59)));
    }

    @Test
    void windowStartingAtMidnight() {
        assertEquals(8 * 60, ShieldWindow.minutesLeft(0, 8, at(0, 0)));
        assertEquals(0, ShieldWindow.minutesLeft(0, 8, at(23, 59)));
    }

    @Test
    void fullDayAndLongerAlwaysCover() {
        for (int m = 0; m < ShieldWindow.DAY_MINUTES; m += 37) {
            assertTrue(ShieldWindow.minutesLeft(5, 24, m) > 0);
            assertTrue(ShieldWindow.minutesLeft(5, 30, m) > 0);
        }
    }

    @Test
    void noWindowWhenUnsetOrInvalid() {
        assertEquals(0, ShieldWindow.minutesLeft(-1, 8, at(12, 0)));
        assertEquals(0, ShieldWindow.minutesLeft(24, 8, at(12, 0)));
        assertEquals(0, ShieldWindow.minutesLeft(12, 0, at(12, 0)));
        assertEquals(0, ShieldWindow.minutesLeft(12, -3, at(12, 0)));
    }

    @Test
    void oneHourWindowBoundaries() {
        assertEquals(60, ShieldWindow.minutesLeft(23, 1, at(23, 0)));
        assertEquals(1, ShieldWindow.minutesLeft(23, 1, at(23, 59)));
        assertEquals(0, ShieldWindow.minutesLeft(23, 1, at(0, 0)));
    }

    @Test
    void coreRadiusAndUpgrades() {
        assertTrue(ShieldWindow.covers(1, 0, 0, 0));
        assertTrue(ShieldWindow.covers(1, 0, 1, -1));
        assertFalse(ShieldWindow.covers(1, 0, 2, 0));
        assertTrue(ShieldWindow.covers(1, 2, -3, 3));
        assertFalse(ShieldWindow.covers(1, 2, 0, 4));
        assertTrue(ShieldWindow.covers(0, 0, 0, 0));
        assertFalse(ShieldWindow.covers(0, 0, 1, 0));
        // Negative settings never shrink the core below the home chunk.
        assertTrue(ShieldWindow.covers(-5, -2, 0, 0));
        assertFalse(ShieldWindow.covers(-5, -2, 1, 0));
    }

    @Test
    void grace() {
        assertFalse(ShieldWindow.graceActive(false, 0, 100));
        assertTrue(ShieldWindow.graceActive(true, 0, 100));
        assertTrue(ShieldWindow.graceActive(true, 101, 100));
        assertFalse(ShieldWindow.graceActive(true, 100, 100));
        assertFalse(ShieldWindow.graceActive(true, 99, 100));
        assertFalse(ShieldWindow.graceActive(false, 500, 100));
    }

    @Test
    void pendingChangeDelay() {
        long hour = 3_600_000L;
        assertFalse(ShieldWindow.pendingDue(0, hour, 10 * hour));
        assertFalse(ShieldWindow.pendingDue(hour, 24 * hour, 25 * hour - 1));
        assertTrue(ShieldWindow.pendingDue(hour, 24 * hour, 25 * hour));
        assertTrue(ShieldWindow.pendingDue(hour, 0, hour));
        assertTrue(ShieldWindow.pendingDue(hour, -5, hour));
    }
}
