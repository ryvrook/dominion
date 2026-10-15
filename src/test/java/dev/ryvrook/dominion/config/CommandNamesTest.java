package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandNamesTest {
    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        CommandNames.resetForTests();
    }

    private Settings settings() {
        return new Settings(new JsonFiles(TestContexts.LOGGER), dir.resolve("config.json"));
    }

    @Test
    void rootFallsBackToTheDefaultWhenBlankOrInvalid() {
        assertEquals("clan", CommandNames.name(" Clan ", "f"));
        assertEquals("f", CommandNames.name("", "f"));
        assertEquals("f", CommandNames.name("two words", "f"));
        assertEquals("fa", CommandNames.name(null, "fa"));
    }

    @Test
    void aliasesAreCleanedDedupedAndSkipTheRoot() {
        assertEquals(List.of("faction", "factions"), CommandNames.aliases("faction, factions", "f"));
        assertEquals(List.of("clan"), CommandNames.aliases(" clan,CLAN,,f, bad name", "f"));
        assertEquals(List.of(), CommandNames.aliases("", "f"));
        assertEquals(List.of(), CommandNames.aliases("clan", "clan"));
    }

    @Test
    void disabledListIsLowerCase() {
        assertEquals(Set.of("map", "top"), CommandNames.disabledIn("Map, top"));
        CommandNames.registeredRoot("f", CommandNames.disabledIn("map,top"));
        assertTrue(CommandNames.disabled("MAP"));
        assertFalse(CommandNames.disabled("claim"));
    }

    @Test
    void localizeRewritesOnlyRenamedRoots() {
        String text = "Use /f claim or /fa set, see /fe list, /fashop edit and /f.";
        assertSame(text, CommandNames.localize(text));
        CommandNames.registeredRoot("clan", Set.of());
        CommandNames.registeredAdmin("clanadmin");
        assertEquals("Use /clan claim or /clanadmin set, see /fe list, /fashop edit and /clan.", CommandNames.localize(text));
        assertEquals("a/f b and /fx stay", CommandNames.localize("a/f b and /fx stay"));
    }

    @Test
    void commandSettingsAreValidated() {
        Settings s = settings();
        assertEquals("f", s.text("commands.root"));
        assertEquals("faction,factions", s.text("commands.rootAliases"));
        assertEquals("fa", s.text("commands.adminRoot"));
        assertEquals("", s.text("commands.disabled"));
        assertNotNull(s.trySet("commands.root", "two words"));
        assertNotNull(s.trySet("commands.root", ""));
        assertNull(s.trySet("commands.root", "clan"));
        assertNull(s.trySet("commands.rootAliases", ""));
        assertNull(s.trySet("commands.rootAliases", "c, guild"));
        assertNotNull(s.trySet("commands.rootAliases", "c,,guild"));
        assertNull(s.trySet("commands.disabled", "map,top,balance"));
        assertNotNull(s.trySet("commands.disabled", "map;top"));
    }

    @Test
    void permissionNodesComeFromSettings() {
        Settings s = settings();
        assertEquals("dominion.admin", Perms.admin());
        assertEquals("dominion.admin.payouts", Perms.payouts());
        assertNull(s.trySet(Perms.ADMIN, "group.staff"));
        assertEquals("group.staff", Perms.admin());
        assertNotNull(s.trySet(Perms.PAYOUTS, "bad node"));
        assertEquals("dominion.admin.payouts", Perms.payouts());
        assertEquals("unknown.key", Perms.node("unknown.key"));
    }

    @Test
    void everyPermissionNodeInTheCodeHasAKey() throws Exception {
        // Nodes must be read through Perms, so the only literals are the shipped defaults and their documented constants.
        java.util.regex.Pattern node = java.util.regex.Pattern.compile("\"(dominion\\.[a-z.]+)\"");
        java.util.Set<String> found = new java.util.TreeSet<>();
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(Path.of("src/main/java"))) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                java.util.regex.Matcher m = node.matcher(java.nio.file.Files.readString(p));
                while (m.find()) {
                    found.add(m.group(1));
                }
            }
        }
        assertTrue(Perms.DEFAULTS.values().containsAll(found), "nodes without a permissions key " + found);
    }
}
