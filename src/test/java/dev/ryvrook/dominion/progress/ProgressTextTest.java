package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTextTest {
    @Test
    void everySentenceIsADefinedIdWithTheNamedPlaceholders() throws Exception {
        TextClassCheck.check(ProgressText.class, "progress", Map.ofEntries(
                entry(ProgressText.UPGRADE_DESCRIPTION, Set.of("effect", "level", "max")),
                entry(ProgressText.NO_SUCH_UPGRADE, Set.of("key", "choices")),
                entry(ProgressText.UPGRADE_LABEL, Set.of("upgrade", "level")),
                entry(ProgressText.UPGRADE_BANK_SHORT, Set.of("cost")),
                entry(ProgressText.UPGRADE_POINTS_SHORT, Set.of("cost", "points")),
                entry(ProgressText.POINTS, Set.of("points")),
                entry(ProgressText.UPGRADE_AUDIT, Set.of("player", "upgrade", "paid")),
                entry(ProgressText.UPGRADE_BOUGHT, Set.of("upgrade", "paid")),
                entry(ProgressText.MISSION_BANK_REASON, Set.of("mission")),
                entry(ProgressText.REWARD_COINS, Set.of("coins")),
                entry(ProgressText.REWARD_COINS_AND_POINTS, Set.of("coins", "points")),
                entry(ProgressText.DAILY_DONE, Set.of("mission")),
                entry(ProgressText.DAILY_DONE_REWARD, Set.of("mission", "reward")),
                entry(ProgressText.WEEKLY_DONE, Set.of("mission")),
                entry(ProgressText.WEEKLY_DONE_REWARD, Set.of("mission", "reward")),
                entry(ProgressText.MISSION_AUDIT, Set.of("mission")),
                entry(ProgressText.MISSION_AUDIT_REWARD, Set.of("mission", "reward")),
                entry(ProgressText.STRIKE_AUDIT_ONE, Set.of("admin", "points", "reason")),
                entry(ProgressText.STRIKE_AUDIT_MANY, Set.of("admin", "points", "reason")),
                entry(ProgressText.STRIKE_GIVEN_ONE, Set.of("faction", "number", "points", "value")),
                entry(ProgressText.STRIKE_GIVEN_MANY, Set.of("faction", "number", "points", "value")),
                entry(ProgressText.NO_STRIKE_NUMBER, Set.of("faction", "index")),
                entry(ProgressText.UNSTRIKE_AUDIT, Set.of("admin", "reason")),
                entry(ProgressText.UNSTRUCK, Set.of("index", "faction", "value"))));
    }

    @Test
    void missionTemplatesReadTheirWordingFromMessageIds() {
        for (MissionPool.Template t : MissionPool.TEMPLATES) {
            assertTrue(Messages.has(t.nameId()), t.nameId());
            assertTrue(Messages.has(t.descriptionId()), t.descriptionId());
        }
        MissionPool.Template rush = MissionPool.byId("ore_rush");
        assertEquals("Ore Rush", rush.name());
        assertEquals("Mine 128 ore blocks.", rush.toMission(1.0, 1).description());
    }
}
