package dev.ryvrook.dominion.events;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScheduleTest {
    private static final ZoneId UTC = ZoneOffset.UTC;

    private static long utc(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test
    void parsesEveryForms() {
        assertEquals(30, Schedule.parse("every 30m").everyMinutes());
        assertEquals(30, Schedule.parse("every 30").everyMinutes());
        assertEquals(45, Schedule.parse("  EVERY   45 minutes ").everyMinutes());
        assertEquals("every 30m", Schedule.parse("every 30").text());
    }

    @Test
    void everyBounds() {
        assertNull(Schedule.parse("every 4m"));
        assertNotNull(Schedule.parse("every 5m"));
        assertNotNull(Schedule.parse("every 10080m"));
        assertNull(Schedule.parse("every 10081m"));
        assertNull(Schedule.parse("every soon"));
    }

    @Test
    void parsesAtTimesSortedAndDeduplicated() {
        Schedule s = Schedule.parse("at 21:30,18:00, 18:00");
        assertEquals(List.of(18 * 60, 21 * 60 + 30), s.times());
        assertEquals("at 18:00,21:30", s.text());
    }

    @Test
    void rejectsBadTimes() {
        assertNull(Schedule.parse("at 24:00"));
        assertNull(Schedule.parse("at 12:60"));
        assertNull(Schedule.parse("at noon"));
        assertNull(Schedule.parse("at "));
        assertNull(Schedule.parse("at 12"));
        assertNull(Schedule.parse("daily"));
        assertNull(Schedule.parse(null));
    }

    @Test
    void everyAddsMinutes() {
        assertEquals(1000 + 30 * 60_000L, Schedule.parse("every 30m").next(1000, UTC));
    }

    @Test
    void nextLaterTodayThenTomorrow() {
        Schedule s = Schedule.parse("at 18:00,21:30");
        assertEquals(utc(2026, 10, 14, 18, 0), s.next(utc(2026, 10, 14, 12, 0), UTC));
        assertEquals(utc(2026, 10, 14, 21, 30), s.next(utc(2026, 10, 14, 18, 0), UTC));
        assertEquals(utc(2026, 10, 15, 18, 0), s.next(utc(2026, 10, 14, 21, 30), UTC));
        assertEquals(utc(2026, 10, 15, 18, 0), s.next(utc(2026, 10, 14, 23, 59), UTC));
    }

    @Test
    void midnightSlot() {
        Schedule s = Schedule.parse("at 00:00");
        assertEquals(utc(2026, 10, 15, 0, 0), s.next(utc(2026, 10, 14, 0, 0), UTC));
        assertEquals(utc(2026, 10, 15, 0, 0), s.next(utc(2026, 10, 14, 23, 59), UTC));
    }

    @Test
    void timeSkippedBySpringForwardMovesLater() {
        ZoneId london = ZoneId.of("Europe/London");
        // On 29 March 2026 the clocks in London jump from 01:00 to 02:00.
        Schedule s = Schedule.parse("at 01:30");
        long before = LocalDateTime.of(2026, 3, 29, 0, 0).atZone(london).toInstant().toEpochMilli();
        long next = s.next(before, london);
        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30).atZone(london).toInstant().toEpochMilli(), next);
    }

    @Test
    void describeIsPlain() {
        assertEquals("every 30 minutes", Schedule.parse("every 30m").describe());
        assertEquals("daily at 18:00", Schedule.parse("at 18:00").describe());
    }
}
