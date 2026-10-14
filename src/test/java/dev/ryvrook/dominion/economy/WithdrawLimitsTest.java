package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.model.Faction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WithdrawLimitsTest {
    private final Faction f = new Faction(UUID.randomUUID(), "Tag", "Name", UUID.randomUUID(), 0);
    private final UUID officer = UUID.randomUUID();

    @Test
    void usageAddsUpWithinOneDay() {
        WithdrawLimits.record(f, officer, 2.5, 100);
        WithdrawLimits.record(f, officer, 1.25, 100);
        assertEquals(375, WithdrawLimits.used(f, officer, 100));
    }

    @Test
    void aNewDayStartsFromZero() {
        WithdrawLimits.record(f, officer, 5, 100);
        assertEquals(0, WithdrawLimits.used(f, officer, 101));
        WithdrawLimits.record(f, officer, 1, 101);
        assertEquals(100, WithdrawLimits.used(f, officer, 101));
        assertEquals(0, WithdrawLimits.used(f, officer, 100));
    }

    @Test
    void givingAllowanceBackNeverGoesBelowZero() {
        WithdrawLimits.record(f, officer, 5, 100);
        // A refund processed after midnight must not leave extra allowance for the new day.
        WithdrawLimits.record(f, officer, -5, 101);
        assertEquals(0, WithdrawLimits.used(f, officer, 101));
        WithdrawLimits.record(f, officer, 3, 101);
        assertEquals(300, WithdrawLimits.used(f, officer, 101));
    }

    @Test
    void membersAreTrackedSeparately() {
        UUID other = UUID.randomUUID();
        WithdrawLimits.record(f, officer, 5, 100);
        assertEquals(0, WithdrawLimits.used(f, other, 100));
    }
}
