package dev.ryvrook.dominion.shield;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

class ShieldTextTest {
    @Test
    void everySentenceIsADefinedIdWithTheNamedPlaceholders() throws Exception {
        TextClassCheck.check(ShieldText.class, "shield", Map.ofEntries(
                entry("shield.alreadyStarts", Set.of("hour")),
                entry("shield.auditAskRemove", Set.of("delay")),
                entry("shield.auditAskMove", Set.of("hour", "delay")),
                entry("shield.auditStarts", Set.of("hour")),
                entry("shield.willBeRemoved", Set.of("delay")),
                entry("shield.nowStarts", Set.of("hour")),
                entry("shield.willStart", Set.of("hour", "delay")),
                entry("shield.statusActive", Set.of("time")),
                entry("shield.statusStarts", Set.of("hour")),
                entry("shield.whenIn", Set.of("time")),
                entry("shield.pendingRemove", Set.of("when")),
                entry("shield.pendingMove", Set.of("hour", "when")),
                entry("shield.graceFor", Set.of("time"))
        ));
    }
}
