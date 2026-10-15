package dev.ryvrook.dominion.command;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DisabledCommandsTest {
    @Test
    void skippedMatchesTheChildNameWithoutCase() {
        PlayerCmd map = new PlayerCmd("map", "Show the map.");
        PlayerCmd claim = new PlayerCmd("claim", "Claim.");
        Set<String> off = Set.of("map", "top");
        assertTrue(PlayerCmd.skipped(map, off));
        assertFalse(PlayerCmd.skipped(claim, off));
        assertFalse(PlayerCmd.skipped(map, Set.of()));
    }

    @Test
    void sealLeavesDisabledSubCommandsOut() {
        PlayerCmd root = new PlayerCmd("f", "Root.");
        root.sub(new PlayerCmd("map", "Map."));
        root.sub(new PlayerCmd("top", "Top."));
        root.sub(new PlayerCmd("claim", "Claim."));
        root.skip(Set.of("map", "top"));
        root.seal();
        assertEquals(Set.of("claim"), root.getSubCommands().keySet());
    }

    @Test
    void rootAliasesAreRegisteredOnTheRoot() {
        PlayerCmd root = new PlayerCmd("clan", "Root.");
        root.addAliases("c", "guild");
        assertEquals("clan", root.getName());
        assertTrue(root.getAliases().containsAll(Set.of("c", "guild")));
    }

    @Test
    void anAliasInTheListLeavesItsCommandOutAndNamesItsTab() {
        PlayerCmd root = new PlayerCmd("f", "Root.");
        PlayerCmd market = root.sub(new PlayerCmd("market", "Market."));
        market.addAliases("m");
        root.sub(new PlayerCmd("claim", "Claim."));
        root.skip(Set.of("m"));
        assertTrue(PlayerCmd.skipped(market, Set.of("m")));
        assertEquals(Set.of("m", "market"), root.skippedNames());
        root.seal();
        assertEquals(Set.of("claim"), root.getSubCommands().keySet());
    }
}
