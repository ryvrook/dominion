package dev.ryvrook.dominion.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TimeTest {
    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(Duration.ofDays(7), Time.parse("7d"));
        assertEquals(Duration.ofHours(36), Time.parse("1d12h"));
        assertEquals(Duration.ofHours(36), Time.parse("1d 12h"));
        assertEquals(Duration.ofMinutes(90), Time.parse("90M"));
        assertEquals(Duration.ofSeconds(45), Time.parse("45"));
    }

    @Test
    void refusesBadInput() {
        assertNull(Time.parse(null));
        assertNull(Time.parse(""));
        assertNull(Time.parse("d"));
        assertNull(Time.parse("5w"));
        assertNull(Time.parse("-5d"));
        assertNull(Time.parse("1d12"), "a trailing number without a unit is ambiguous");
        assertNull(Time.parse("99999999999999999999d"), "an overflowing number never throws");
        assertNull(Time.parse("200000d"), "longer than a century");
    }

    @Test
    void remainingText() {
        assertEquals("now", Time.remaining(0));
        assertEquals("45s", Time.remaining(45_000));
        assertEquals("2m 5s", Time.remaining(125_000));
        assertEquals("1h 1m", Time.remaining(3_660_000));
        assertEquals("2d 3h", Time.remaining((2 * 86400 + 3 * 3600) * 1000L));
    }
}
