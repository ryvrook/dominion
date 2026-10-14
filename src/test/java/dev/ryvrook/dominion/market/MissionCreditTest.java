package dev.ryvrook.dominion.market;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionCreditTest {
    private final MissionCredit credit = new MissionCredit();
    private final UUID buyer = UUID.randomUUID();
    private final UUID factionA = UUID.randomUUID();
    private final UUID factionB = UUID.randomUUID();

    @Test
    void oneCoinTradesDoNotCount() {
        assertFalse(credit.counts(buyer, factionA, UUID.randomUUID(), factionB, 1, 0));
        assertTrue(credit.counts(buyer, factionA, UUID.randomUUID(), factionB, MissionCredit.MIN_VALUE, 0));
    }

    @Test
    void tradesInsideOneFactionOrWithYourselfDoNotCount() {
        assertFalse(credit.counts(buyer, factionA, UUID.randomUUID(), factionA, 500, 0));
        assertFalse(credit.counts(buyer, factionA, buyer, factionB, 500, 0));
        // Two players without a faction are not faction mates.
        assertTrue(credit.counts(buyer, null, UUID.randomUUID(), null, 500, 0));
    }

    @Test
    void theSamePartnerCountsOncePerWindow() {
        UUID alt = UUID.randomUUID();
        assertTrue(credit.counts(buyer, factionA, alt, factionB, 50, 0));
        assertFalse(credit.counts(buyer, factionA, alt, factionB, 50, 1000));
        assertTrue(credit.counts(buyer, factionA, alt, factionB, 50, MissionCredit.WINDOW_MS));
    }

    @Test
    void aBuyerHasACapPerWindow() {
        for (int i = 0; i < MissionCredit.MAX_PER_WINDOW; i++) {
            assertTrue(credit.counts(buyer, factionA, UUID.randomUUID(), factionB, 50, i));
        }
        assertFalse(credit.counts(buyer, factionA, UUID.randomUUID(), factionB, 50, 100));
        assertTrue(credit.counts(buyer, factionA, UUID.randomUUID(), factionB, 50, MissionCredit.WINDOW_MS + 1));
        assertTrue(credit.counts(UUID.randomUUID(), factionA, UUID.randomUUID(), factionB, 50, 100));
    }
}
