package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.model.Rank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhoAndTopTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    void ageShowsTheTwoLargestUnits() {
        assertEquals("3 days, 4 hours", InfoCommands.age(3 * DAY + 4 * HOUR + 17 * MINUTE));
        assertEquals("1 day, 1 hour", InfoCommands.age(DAY + HOUR));
        assertEquals("2 days", InfoCommands.age(2 * DAY + 5 * MINUTE));
        assertEquals("2 hours, 17 minutes", InfoCommands.age(2 * HOUR + 17 * MINUTE));
        assertEquals("1 hour", InfoCommands.age(HOUR + 30_000));
        assertEquals("1 minute", InfoCommands.age(MINUTE));
        assertEquals("less than a minute", InfoCommands.age(59_000));
        assertEquals("less than a minute", InfoCommands.age(-5 * DAY), "a clock running behind never prints a negative age");
    }

    @Test
    void onlyLeadersAndOfficersAreMarked() {
        assertEquals("**", InfoCommands.marker(Rank.LEADER));
        assertEquals("*", InfoCommands.marker(Rank.OFFICER));
        assertEquals("", InfoCommands.marker(Rank.MEMBER));
        assertEquals("", InfoCommands.marker(Rank.RECRUIT));
    }

    @Test
    void pagingArithmetic() {
        assertEquals(1, ProgressCommands.pages(0, 10));
        assertEquals(1, ProgressCommands.pages(1, 10));
        assertEquals(1, ProgressCommands.pages(10, 10));
        assertEquals(2, ProgressCommands.pages(11, 10));
        assertEquals(36, ProgressCommands.pages(354, 10));
        assertEquals(0, ProgressCommands.firstIndex(1, 10));
        assertEquals(10, ProgressCommands.firstIndex(2, 10));
        assertEquals(350, ProgressCommands.firstIndex(36, 10));
        assertEquals(0, ProgressCommands.firstIndex(0, 10), "page zero reads as the first page");
    }

    @Test
    void pageSizeStaysInRange() {
        assertEquals(5, ProgressCommands.pageSize(1));
        assertEquals(10, ProgressCommands.pageSize(10));
        assertEquals(25, ProgressCommands.pageSize(100));
    }
}
