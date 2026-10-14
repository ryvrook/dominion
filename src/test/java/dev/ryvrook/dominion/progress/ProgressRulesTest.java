package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.model.Mission;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressRulesTest {
    @Test
    void upgradeCostGrowsPerLevel() {
        assertEquals(500.0, ProgressServiceImpl.cost(500, 1.8, 0), 1e-9);
        assertEquals(900.0, ProgressServiceImpl.cost(500, 1.8, 1), 1e-9);
        assertEquals(1620.0, ProgressServiceImpl.cost(500, 1.8, 2), 1e-9);
        assertEquals(2916.0, ProgressServiceImpl.cost(500, 1.8, 3), 1e-9);
    }

    @Test
    void upgradeCostRoundsToCents() {
        assertEquals(333.33, ProgressServiceImpl.cost(100, 3.3333333, 1), 1e-9);
        assertEquals(100.0, ProgressServiceImpl.cost(100, 1.0, 4), 1e-9);
    }

    @Test
    void upgradeCostEdgeSettings() {
        assertEquals(500.0, ProgressServiceImpl.cost(500, 1.8, -2), 1e-9);
        assertEquals(0.0, ProgressServiceImpl.cost(-500, 1.8, 1), 1e-9);
        assertEquals(0.0, ProgressServiceImpl.cost(0, 1.8, 1), 1e-9);
        assertEquals(Double.MAX_VALUE, ProgressServiceImpl.cost(Double.NaN, 1.8, 1));
        assertEquals(Double.MAX_VALUE, ProgressServiceImpl.cost(1e300, 1e300, 2));
    }

    @Test
    void pointsCostRoundsUp() {
        assertEquals(334, ProgressServiceImpl.pointsCost(333.01));
        assertEquals(333, ProgressServiceImpl.pointsCost(333.0));
        assertEquals(Integer.MAX_VALUE, ProgressServiceImpl.pointsCost(Double.MAX_VALUE));
    }

    @Test
    void missionCreditNeverPassesTheTarget() {
        assertEquals(1, ProgressServiceImpl.credit(0, 1, 3));
        assertEquals(1, ProgressServiceImpl.credit(2, 5, 3));
        assertEquals(0, ProgressServiceImpl.credit(3, 5, 3));
        assertEquals(0, ProgressServiceImpl.credit(4, 5, 3));
        assertEquals(0, ProgressServiceImpl.credit(0, 0, 3));
        assertEquals(0, ProgressServiceImpl.credit(0, -2, 3));
        assertEquals(48, ProgressServiceImpl.credit(0, 1000, 48));
    }

    @Test
    void staleMissionKeys() {
        long day = 20_000;
        long week = Math.floorDiv(day + 3, 7);
        assertFalse(ProgressServiceImpl.staleMissionKey("mission.timber." + day, day, week));
        assertTrue(ProgressServiceImpl.staleMissionKey("mission.timber." + (day - 1), day, week));
        assertFalse(ProgressServiceImpl.staleMissionKey("mission.war_path." + week, day, week));
        assertTrue(ProgressServiceImpl.staleMissionKey("mission.war_path." + day, day, week));
        assertTrue(ProgressServiceImpl.staleMissionKey("mission.removed_mission." + day, day, week));
        assertTrue(ProgressServiceImpl.staleMissionKey("mission.", day, week));
        assertFalse(ProgressServiceImpl.staleMissionKey("siegesWon", day, week));
        assertFalse(ProgressServiceImpl.staleMissionKey(null, day, week));
    }

    @Test
    void weeksRollOverOnMondayUtc() {
        long sunday = LocalDate.of(2026, 10, 11).toEpochDay();
        long monday = LocalDate.of(2026, 10, 12).toEpochDay();
        long nextSunday = LocalDate.of(2026, 10, 18).toEpochDay();
        assertEquals(Math.floorDiv(sunday + 3, 7) + 1, Math.floorDiv(monday + 3, 7));
        assertEquals(Math.floorDiv(monday + 3, 7), Math.floorDiv(nextSunday + 3, 7));
        // The epoch day 0 was a Thursday in week 0.
        assertEquals(0, Math.floorDiv(0 + 3, 7));
        assertEquals(1, Math.floorDiv(LocalDate.of(1970, 1, 5).toEpochDay() + 3, 7));
    }

    @Test
    void pickIsDeterministicAndPrefersDistinctKinds() {
        for (long period = 0; period < 50; period++) {
            List<MissionPool.Template> a = MissionPool.pick(false, period, 3);
            assertEquals(a, MissionPool.pick(false, period, 3));
            assertEquals(3, a.size());
            Set<String> kinds = new HashSet<>();
            for (MissionPool.Template t : a) {
                assertFalse(t.weekly());
                kinds.add(t.kind());
            }
            assertEquals(3, kinds.size());
        }
    }

    @Test
    void pickBounds() {
        assertEquals(0, MissionPool.pick(true, 5, 0).size());
        assertEquals(0, MissionPool.pick(true, 5, -3).size());
        long weekly = MissionPool.TEMPLATES.stream().filter(MissionPool.Template::weekly).count();
        assertEquals(weekly, MissionPool.pick(true, 5, 100).size());
        assertEquals(weekly, new HashSet<>(MissionPool.pick(true, 5, 100)).size());
    }

    @Test
    void templatesHaveUniqueIdsAndPositiveTargets() {
        Set<String> ids = new HashSet<>();
        for (MissionPool.Template t : MissionPool.TEMPLATES) {
            assertTrue(ids.add(t.id()), t.id());
            assertTrue(t.target() > 0);
            assertFalse(t.id().contains("."), "ids must not contain the key separator");
            assertNotNull(MissionPool.byId(t.id()));
            assertFalse(t.description().contains(";"));
        }
    }

    @Test
    void rewardsScaleWithSettings() {
        Mission m = MissionPool.byId("ore_rush").toMission(100.0, 10);
        assertEquals(200.0, m.rewardCoins(), 1e-9);
        assertEquals(20, m.rewardPoints());
        Mission s = MissionPool.byId("skirmish").toMission(40.0, 3);
        assertEquals(60.0, s.rewardCoins(), 1e-9);
        assertEquals(5, s.rewardPoints());
    }
}
