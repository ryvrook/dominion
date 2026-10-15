package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The keys that replaced constants keep the old value as their default and refuse values outside their range. */
class NewKeysTest {
    @TempDir
    Path dir;

    /** Key, old constant value, lowest allowed, highest allowed. */
    private static final Object[][] KEYS = {
            {"land.reclaimWindowDays", 7, 0, 365},
            {"land.territoryMessageMillis", 2000, 0, 600_000},
            {"land.nearDefault", 64, 1, 4096},
            {"land.nearMax", 256, 1, 4096},
            {"power.dtrBase", 2.0, 0, 100_000},
            {"power.dtrPerMember", 1.0, 0, 100_000},
            {"power.dtrCap", 10.0, 0, 100_000},
            {"power.dtrFloor", -3.0, -100_000, 0},
            {"power.dtrLossPerDeath", 1.0, 0, 100_000},
            {"power.dtrRegenPerOnline", 0.05, 0, 100_000},
            {"power.dtrRegenCap", 0.1, 0, 100_000},
            {"power.regenStep", 0.25, 0, 100},
            {"power.upgradeStep", 2.0, 0, 1_000_000},
            {"combat.homeRespawnDelayMillis", 1000, 50, 60_000},
            {"combat.homeRespawnAttempts", 5, 1, 100},
            {"combat.safeZoneMaxStep", 64.0, 1, 10_000},
            {"home.stuckSearchRadius", 32, 1, 256},
            {"home.warmupMoveTolerance", 1.0, 0, 1000},
            {"missions.farmGuardMaxPlaced", 100_000, 0, 10_000_000},
            {"missions.farmGuardMaxDrops", 256, 0, 100_000},
            {"missions.farmGuardDropMemoryMinutes", 5, 0, 10_080},
            {"integrations.foundedWindowSeconds", 60, 1, 86_400},
            {"market.auctionHours", 24, 1, 8760},
            {"raid.upkeepUnpaidDays", 3, 1, 3650},
            {"vault.memberPages", 1, 0, 64},
            {"siege.holdOffSeconds", 30, 1, 86_400},
            {"siege.resumeGraceSeconds", 120, 0, 86_400},
            {"commands.confirmSeconds", 30, 5, 3600},
            {"ui.marketPageSize", 10, 1, 50},
            {"ui.logPageSize", 25, 1, 100},
            {"ui.gridRadius", 7, 2, 7},
            {"ui.topLimit", 30, 1, 500},
            {"ui.eventRows", 20, 1, 100},
            {"ui.siegeHistory", 10, 0, 100},
            {"ui.vaultMaxPages", 30, 1, 64},
            {"ui.confirmLockMillis", 3000, 0, 60_000},
            {"ui.maxDots", 10, 1, 10},
    };

    private Settings settings() {
        return new Settings(new JsonFiles(TestContexts.LOGGER), dir.resolve("config.json"));
    }

    @Test
    void everyNewKeyHasTheOldValueAndItsRange() {
        Settings s = settings();
        for (Object[] k : KEYS) {
            String key = (String) k[0];
            assertTrue(s.known(key), key);
            assertEquals(((Number) k[1]).doubleValue(), s.decimal(key), 1e-9, key);
            Settings.Range r = s.rangeOf(key);
            assertNotNull(r, key);
            assertEquals(((Number) k[2]).doubleValue(), r.min(), key);
            assertEquals(((Number) k[3]).doubleValue(), r.max(), key);
            boolean whole = s.definition(key).defaultValue() instanceof Integer;
            Object below = whole ? (Object) (int) (r.min() - 1) : (Object) (r.min() - 0.5);
            Object above = whole ? (Object) (int) (r.max() + 1) : (Object) (r.max() + 0.5);
            assertNotNull(s.trySet(key, below), key + " below");
            assertNotNull(s.trySet(key, above), key + " above");
            assertNull(s.trySet(key, k[1]), key + " default");
        }
    }

    @Test
    void nearDefaultCannotPassNearMax() {
        Settings s = settings();
        assertNotNull(s.trySet("land.nearDefault", 300));
        assertNull(s.trySet("land.nearMax", 400));
        assertNull(s.trySet("land.nearDefault", 300));
    }

    @Test
    void bannerIconFallsBackWhenBlank() {
        Settings s = settings();
        assertEquals("Furniture_Flag_Small_Red", s.text("ui.bannerIcon"));
    }
}
