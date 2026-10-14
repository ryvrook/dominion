package dev.ryvrook.dominion.events;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.ryvrook.dominion.events.Payouts.AmountProblem.NONE;
import static dev.ryvrook.dominion.events.Payouts.AmountProblem.NOT_WHOLE;
import static dev.ryvrook.dominion.events.Payouts.AmountProblem.TOO_SMALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutsTest {
    @Test
    void templatesSplitOnPipesAndDropSlashes() {
        assertEquals(List.of("say hi {tag}", "give {tag} 5", "eco add {rank}"),
                Payouts.templates(" say hi {tag} | /give {tag} 5 || / eco add {rank} | "));
        assertEquals(List.of(), Payouts.templates(""));
        assertEquals(List.of(), Payouts.templates(null));
        assertEquals(List.of(), Payouts.templates(" / | "));
    }

    @Test
    void fillReplacesEveryPlaceholder() {
        assertEquals("say Kings rank 1 Kings", Payouts.fill("say {tag} rank {rank} {tag}", "Kings", "1"));
        assertEquals("say nothing", Payouts.fill("say nothing", "Kings", "1"));
    }

    @Test
    void onlyPlainTagsReachTheConsole() {
        assertTrue(Payouts.safeTag("Kings"));
        assertTrue(Payouts.safeTag("K1ng_s"));
        assertFalse(Payouts.safeTag("Kings op me"));
        assertFalse(Payouts.safeTag("a;b"));
        assertFalse(Payouts.safeTag(""));
        assertFalse(Payouts.safeTag(null));
        assertFalse(Payouts.safeTag("x".repeat(33)));
    }

    @Test
    void bountyAmounts() {
        assertEquals(NONE, Payouts.checkAmount(50, 50, true));
        assertEquals(TOO_SMALL, Payouts.checkAmount(49.99, 50, false));
        assertEquals(TOO_SMALL, Payouts.checkAmount(0, 0, false));
        assertEquals(TOO_SMALL, Payouts.checkAmount(-10, -20, false));
        assertEquals(TOO_SMALL, Payouts.checkAmount(Double.NaN, 0, false));
        assertEquals(TOO_SMALL, Payouts.checkAmount(Double.POSITIVE_INFINITY, 0, false));
        assertEquals(NOT_WHOLE, Payouts.checkAmount(50.5, 50, true));
        assertEquals(NONE, Payouts.checkAmount(50.5, 50, false));
    }

    @Test
    void bountyCollection() {
        UUID killer = UUID.randomUUID();
        UUID placer = UUID.randomUUID();
        UUID fa = UUID.randomUUID();
        UUID fb = UUID.randomUUID();
        assertTrue(Payouts.collectable(killer, fa, fb, Set.of(placer)));
        assertTrue(Payouts.collectable(killer, null, null, Set.of(placer)));
        assertTrue(Payouts.collectable(killer, fa, null, Set.of(placer)));
        assertFalse(Payouts.collectable(killer, fa, fa, Set.of(placer)), "no farming your own member");
        assertFalse(Payouts.collectable(placer, fa, fb, Set.of(placer)), "placers cannot take their money back");
    }

    @Test
    void topListReadsNaturally() {
        assertEquals("", Payouts.topList(List.of(), 3));
        assertEquals("A", Payouts.topList(List.of("A"), 3));
        assertEquals("A and B", Payouts.topList(List.of("A", "B"), 3));
        assertEquals("A, B and C", Payouts.topList(List.of("A", "B", "C"), 3));
        assertEquals("A, B and C", Payouts.topList(List.of("A", "B", "C", "D"), 3));
        assertEquals("", Payouts.topList(List.of("A"), 0));
    }

    @Test
    void boostNamesReadNaturally() {
        assertEquals("an xp boost", EventsServiceImpl.boostName("xp"));
        assertEquals("a tax boost", EventsServiceImpl.boostName("tax"));
        assertEquals("a missions boost", EventsServiceImpl.boostName("missions"));
        assertEquals("a sell boost", EventsServiceImpl.boostName("sell"));
    }
}
