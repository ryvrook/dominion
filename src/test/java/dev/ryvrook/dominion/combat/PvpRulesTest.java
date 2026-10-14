package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Relation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpRulesTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final PvpRules.Toggles ALL_ON = new PvpRules.Toggles(true, true, true, true);
    private static final PvpRules.Toggles DEFAULTS = new PvpRules.Toggles(true, true, false, false);
    private static final PvpRules.Toggles ALL_OFF = new PvpRules.Toggles(false, false, false, false);

    private static Result hit(FactionType from, FactionType at, UUID af, UUID vf, Relation r, boolean ff, PvpRules.Toggles t) {
        return PvpRules.decide(false, from, at, af, vf, r, ff, t);
    }

    @Test
    void hittingYourselfIsNeverBlocked() {
        assertTrue(PvpRules.decide(true, FactionType.SAFEZONE, FactionType.SAFEZONE, A, A, null, false, ALL_OFF).success());
    }

    @Test
    void safeZoneBlocksEverythingEvenEnemies() {
        assertFalse(hit(null, FactionType.SAFEZONE, A, B, Relation.ENEMY, true, ALL_ON).success());
        assertFalse(hit(FactionType.SAFEZONE, null, A, B, Relation.ENEMY, true, ALL_ON).success());
        assertFalse(hit(FactionType.SAFEZONE, FactionType.WARZONE, A, B, Relation.ENEMY, true, ALL_ON).success());
        assertEquals("You cannot fight from inside the SafeZone.",
                hit(FactionType.SAFEZONE, FactionType.SAFEZONE, A, B, Relation.ENEMY, true, ALL_ON).message());
        assertEquals("You cannot fight in the SafeZone.",
                hit(FactionType.WARZONE, FactionType.SAFEZONE, null, null, null, false, ALL_ON).message());
    }

    @Test
    void warZoneAllowsAllButOwnFaction() {
        assertTrue(hit(null, FactionType.WARZONE, A, B, Relation.ALLY, false, ALL_OFF).success());
        assertTrue(hit(null, FactionType.WARZONE, A, B, Relation.TRUCE, false, ALL_OFF).success());
        assertTrue(hit(null, FactionType.WARZONE, A, B, Relation.NEUTRAL, false, ALL_OFF).success());
        assertTrue(hit(null, FactionType.WARZONE, null, null, null, false, ALL_OFF).success());
        assertTrue(hit(null, FactionType.WARZONE, A, null, null, false, ALL_OFF).success());
        assertFalse(hit(null, FactionType.WARZONE, A, A, null, true, ALL_ON).success());
    }

    @Test
    void attackerInWarZoneHittingIntoWildernessUsesNormalRules() {
        assertFalse(hit(FactionType.WARZONE, null, A, B, Relation.ALLY, false, DEFAULTS).success());
        assertTrue(hit(FactionType.WARZONE, null, A, B, Relation.ENEMY, false, ALL_OFF).success());
    }

    @Test
    void factionlessPlayers() {
        assertTrue(hit(null, null, null, null, null, false, DEFAULTS).success());
        assertTrue(hit(null, null, A, null, null, false, DEFAULTS).success());
        assertTrue(hit(null, null, null, B, null, false, DEFAULTS).success());
        assertFalse(hit(null, null, null, B, null, false, ALL_OFF).success());
        assertFalse(hit(null, FactionType.NORMAL, A, null, null, false, ALL_OFF).success());
    }

    @Test
    void sameFactionFollowsFriendlyFire() {
        assertFalse(hit(null, null, A, A, null, false, ALL_ON).success());
        assertTrue(hit(null, null, A, A, null, true, ALL_OFF).success());
        assertFalse(hit(null, FactionType.NORMAL, A, A, Relation.OWN, false, ALL_ON).success());
    }

    @Test
    void relationTableWithDefaults() {
        assertFalse(hit(null, null, A, B, Relation.ALLY, false, DEFAULTS).success());
        assertFalse(hit(null, null, A, B, Relation.TRUCE, false, DEFAULTS).success());
        assertTrue(hit(null, null, A, B, Relation.NEUTRAL, false, DEFAULTS).success());
        assertTrue(hit(null, null, A, B, Relation.ENEMY, false, DEFAULTS).success());
    }

    @Test
    void relationTableWithEveryToggle() {
        assertTrue(hit(null, null, A, B, Relation.ALLY, false, ALL_ON).success());
        assertTrue(hit(null, null, A, B, Relation.TRUCE, false, ALL_ON).success());
        assertFalse(hit(null, null, A, B, Relation.NEUTRAL, false, ALL_OFF).success());
        // Enemies can always fight outside a SafeZone, whatever the toggles say.
        assertTrue(hit(null, null, A, B, Relation.ENEMY, false, ALL_OFF).success());
        assertTrue(hit(null, FactionType.NORMAL, A, B, Relation.ENEMY, false, ALL_OFF).success());
    }

    @Test
    void ownAndEnemyLandUseTheSameRelationRules() {
        // Claimed land of a normal faction does not change the PvP table, only zones do.
        assertEquals(hit(null, null, A, B, Relation.NEUTRAL, false, ALL_OFF).success(),
                hit(FactionType.NORMAL, FactionType.NORMAL, A, B, Relation.NEUTRAL, false, ALL_OFF).success());
    }

    @Test
    void missingRelationReadsAsNeutral() {
        assertTrue(hit(null, null, A, B, null, false, DEFAULTS).success());
        assertFalse(hit(null, null, A, B, null, false, ALL_OFF).success());
    }

    @Test
    void everyDenialHasAPlainReason() {
        for (Result r : new Result[] {
                hit(FactionType.SAFEZONE, null, A, B, Relation.ENEMY, false, ALL_OFF),
                hit(null, FactionType.SAFEZONE, A, B, Relation.ENEMY, false, ALL_OFF),
                hit(null, FactionType.WARZONE, A, A, null, false, ALL_OFF),
                hit(null, null, null, B, null, false, ALL_OFF),
                hit(null, null, A, A, null, false, ALL_OFF),
                hit(null, null, A, B, Relation.ALLY, false, ALL_OFF),
                hit(null, null, A, B, Relation.TRUCE, false, ALL_OFF),
                hit(null, null, A, B, Relation.NEUTRAL, false, ALL_OFF)}) {
            assertFalse(r.success());
            assertTrue(r.message().endsWith("."), r.message());
            assertFalse(r.message().contains(";") || r.message().indexOf((char) 0x2014) >= 0, r.message());
        }
    }
}
