package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

class IntegrationTextTest {
    @Test
    void everySentenceIsADefinedIdWithTheNamedPlaceholders() throws Exception {
        TextClassCheck.check(IntegrationText.class, "integration", Map.ofEntries(
                entry(IntegrationText.STATUS_OFF, Set.of("plugin", "setting", "mode")),
                entry(IntegrationText.STATUS_MISSING, Set.of("plugin", "setting", "mode")),
                entry(IntegrationText.STATUS_TOO_OLD, Set.of("plugin", "version", "minimum", "setting", "mode")),
                entry(IntegrationText.STATUS_BOUND, Set.of("plugin", "version", "setting", "mode")),
                entry(IntegrationText.STATUS_CHANGED, Set.of("plugin", "command")),
                entry(IntegrationText.STATUS_FAILED, Set.of("plugin", "version")),
                entry(IntegrationText.STATUS_WAITING, Set.of("plugin", "version")),
                entry(IntegrationText.SIEGE_LABEL, Set.of("attacker", "defender")),
                entry(IntegrationText.SIEGE_POPUP, Set.of("attacker", "defender", "x", "z", "state")),
                entry(IntegrationText.SIEGE_WHEN, Set.of("time")),
                entry(IntegrationText.SIEGE_CAPTURING_UNTIL, Set.of("when")),
                entry(IntegrationText.SIEGE_BREACHED_UNTIL, Set.of("when")),
                entry(IntegrationText.SIEGE_PHASE, Set.of("phase"))));
    }
}
