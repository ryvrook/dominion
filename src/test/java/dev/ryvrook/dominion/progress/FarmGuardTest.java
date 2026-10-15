package dev.ryvrook.dominion.progress;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmGuardTest {
    private static final long HOUR = 3_600_000L;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID loner = UUID.randomUUID();
    private final UUID red = UUID.randomUUID();
    private final UUID blue = UUID.randomUUID();
    private final Map<UUID, UUID> factions = Map.of(alice, red, bob, red, carol, blue);

    private FarmGuard guard(long memory) {
        return new FarmGuard(() -> memory, factions::get);
    }

    @Test
    void blockKinds() {
        assertEquals("mine", GatherSystems.blockKind("Ore_Iron_Stone"));
        assertEquals("chop", GatherSystems.blockKind("Wood_Oak_Trunk"));
        assertNull(GatherSystems.blockKind("Wood_Oak_Planks"));
        assertNull(GatherSystems.blockKind("Rock_Stone"));
        assertNull(GatherSystems.blockKind(""));
        assertNull(GatherSystems.blockKind(null));
    }

    @Test
    void pickupKinds() {
        assertEquals("fish", GatherSystems.pickupKind("Fish_Salmon_Item"));
        assertEquals("farm", GatherSystems.pickupKind("Plant_Crop_Wheat_Item"));
        assertNull(GatherSystems.pickupKind("Plant_Crop_Wheat"));
        assertNull(GatherSystems.pickupKind("Ingredient_Bar_Gold"));
        assertNull(GatherSystems.pickupKind(null));
    }

    @Test
    void placedOreEarnsNothingWithinTheMemory() {
        FarmGuard g = guard(HOUR);
        g.placed("world", "Ore_Iron", 1, 2, 3, 0);
        assertTrue(g.wasPlaced("world", 1, 2, 3, HOUR));
        // The memory is used up when the block breaks.
        assertFalse(g.wasPlaced("world", 1, 2, 3, HOUR));
    }

    @Test
    void placedOreEarnsAgainAfterTheMemory() {
        FarmGuard g = guard(HOUR);
        g.placed("world", "Ore_Iron", 1, 2, 3, 0);
        assertFalse(g.wasPlaced("world", 1, 2, 3, HOUR + 1));
    }

    @Test
    void otherBlocksAndWorldsAreNotRemembered() {
        FarmGuard g = guard(HOUR);
        g.placed("world", "Rock_Stone", 1, 2, 3, 0);
        assertFalse(g.wasPlaced("world", 1, 2, 3, 10));
        g.placed("world", "Ore_Iron", 1, 2, 3, 0);
        assertFalse(g.wasPlaced("other", 1, 2, 3, 10));
        assertFalse(g.wasPlaced(null, 1, 2, 3, 10));
    }

    @Test
    void zeroMemoryTurnsTheGuardOff() {
        FarmGuard g = guard(0L);
        g.placed("world", "Ore_Iron", 1, 2, 3, 0);
        assertFalse(g.wasPlaced("world", 1, 2, 3, 0));
    }

    @Test
    void sweepForgetsOldPlacements() {
        FarmGuard g = guard(HOUR);
        g.placed("world", "Ore_Iron", 1, 1, 1, 0);
        g.placed("world", "Ore_Iron", 2, 2, 2, HOUR);
        g.sweep(HOUR + 1);
        assertFalse(g.wasPlaced("world", 1, 1, 1, HOUR + 1));
        assertTrue(g.wasPlaced("world", 2, 2, 2, HOUR + 1));
    }

    @Test
    void ownDropsEarnNothing() {
        FarmGuard g = guard(HOUR);
        g.dropped(alice, "Plant_Crop_Wheat_Item", 10, 0);
        assertEquals(10, g.ownDrops(alice, "Plant_Crop_Wheat_Item", 16, 1000));
        assertEquals(0, g.ownDrops(alice, "Plant_Crop_Wheat_Item", 16, 1000));
    }

    @Test
    void passingCropsToAFriendEarnsNothing() {
        FarmGuard g = guard(HOUR);
        g.dropped(alice, "Plant_Crop_Wheat_Item", 64, 0);
        assertEquals(64, g.ownDrops(bob, "Plant_Crop_Wheat_Item", 64, 1000));
        g.dropped(bob, "Plant_Crop_Wheat_Item", 64, 2000);
        assertEquals(64, g.ownDrops(alice, "Plant_Crop_Wheat_Item", 64, 3000));
    }

    @Test
    void dropsMatchByItemAndExpire() {
        FarmGuard g = guard(HOUR);
        g.dropped(alice, "Fish_Salmon_Item", 3, 0);
        assertEquals(0, g.ownDrops(alice, "Plant_Crop_Wheat_Item", 3, 10));
        assertEquals(0, g.ownDrops(alice, "Fish_Salmon_Item", 3, FarmGuard.DROP_MEMORY_MILLIS + 1));
    }

    @Test
    void dropMemorySurvivesAQuitUntilItExpires() {
        FarmGuard g = guard(HOUR);
        g.dropped(alice, "Fish_Salmon_Item", 5, 0);
        g.sweep(FarmGuard.DROP_MEMORY_MILLIS);
        assertEquals(5, g.ownDrops(bob, "Fish_Salmon_Item", 5, FarmGuard.DROP_MEMORY_MILLIS));
    }

    @Test
    void dropMemoryIsBoundedPerPlayer() {
        FarmGuard g = guard(HOUR);
        for (int i = 0; i < FarmGuard.MAX_DROPS_PER_PLAYER + 10; i++) {
            g.dropped(alice, "Fish_Salmon_Item", 1, i);
        }
        assertEquals(FarmGuard.MAX_DROPS_PER_PLAYER, g.ownDrops(alice, "Fish_Salmon_Item", 10_000, 1000));
    }

    @Test
    void dropsByAnotherFactionNeverWithholdCredit() {
        FarmGuard g = guard(HOUR);
        g.dropped(carol, "Plant_Crop_Wheat_Item", 32, 0);
        assertEquals(0, g.ownDrops(alice, "Plant_Crop_Wheat_Item", 32, 1000));
        // The entries were not used up, so carol picking her own crops back up still earns nothing.
        assertEquals(32, g.ownDrops(carol, "Plant_Crop_Wheat_Item", 32, 1000));
    }

    @Test
    void factionlessPickersOnlyMatchTheirOwnDrops() {
        FarmGuard g = guard(HOUR);
        g.dropped(alice, "Fish_Salmon_Item", 4, 0);
        g.dropped(loner, "Fish_Salmon_Item", 2, 0);
        assertEquals(2, g.ownDrops(loner, "Fish_Salmon_Item", 6, 10));
        assertEquals(4, g.ownDrops(bob, "Fish_Salmon_Item", 6, 10));
    }

    @Test
    void ownDropsAreUsedBeforeAFactionMates() {
        FarmGuard g = guard(HOUR);
        g.dropped(alice, "Fish_Salmon_Item", 3, 0);
        g.dropped(bob, "Fish_Salmon_Item", 3, 0);
        assertEquals(4, g.ownDrops(bob, "Fish_Salmon_Item", 4, 10));
        assertEquals(2, g.ownDrops(alice, "Fish_Salmon_Item", 10, 10));
    }

    @Test
    void dropCapAndMemoryAreSettings() {
        int[] cap = {4};
        long[] memory = {1000};
        FarmGuard g = new FarmGuard(() -> HOUR, factions::get, 10, () -> cap[0], () -> memory[0]);
        for (int i = 0; i < 10; i++) {
            g.dropped(alice, "Fish_Salmon_Item", 1, 100);
        }
        assertEquals(4, g.ownDrops(alice, "Fish_Salmon_Item", 10, 100));
        g.dropped(alice, "Fish_Salmon_Item", 3, 100);
        assertEquals(0, g.ownDrops(alice, "Fish_Salmon_Item", 3, 1101), "older than the drop memory");
        memory[0] = 5000;
        g.dropped(alice, "Fish_Salmon_Item", 3, 2000);
        assertEquals(3, g.ownDrops(alice, "Fish_Salmon_Item", 3, 6000));
    }
}
