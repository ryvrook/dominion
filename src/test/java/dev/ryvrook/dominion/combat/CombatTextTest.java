package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

class CombatTextTest {
    @Test
    void everySentenceIsADefinedIdWithTheNamedPlaceholders() throws Exception {
        TextClassCheck.check(CombatText.class, "combat", Map.ofEntries(
                entry("combat.statusUnreadable", Set.of("version")),
                entry("combat.statusOff", Set.of("version", "key")),
                entry("combat.statusUsed", Set.of("version")),
                entry("combat.slain", Set.of("victim", "killer")),
                entry("combat.killRewardReason", Set.of("victim", "killer")),
                entry("combat.killReward", Set.of("killer", "amount", "victim")),
                entry("combat.denySafezone", Set.of("action", "zone")),
                entry("combat.denyWarzone", Set.of("action", "zone")),
                entry("combat.notOnRoster", Set.of("tag")),
                entry("combat.denyRankZone", Set.of("action", "zone")),
                entry("combat.denyRank", Set.of("tag", "action")),
                entry("combat.denyEnemy", Set.of("tag", "action")),
                entry("combat.denyZone", Set.of("tag", "action", "zone")),
                entry("combat.denyAllyBuild", Set.of("tag")),
                entry("combat.denyTruceUse", Set.of("tag")),
                entry("combat.denyLand", Set.of("tag", "action"))
        ));
    }
}
