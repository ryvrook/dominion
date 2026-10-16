package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReportTest {
    @TempDir
    Path dir;

    private Settings settings() {
        return new Settings(new JsonFiles(TestContexts.LOGGER), dir.resolve("config.json"));
    }

    @Test
    void sectionsCountEveryKeyOnce() {
        Settings s = settings();
        Map<String, Integer> sections = ConfigReport.sections(s);
        assertEquals(s.definitions().size(), sections.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals("Factions", sections.keySet().iterator().next());
        for (String name : List.of("Interface", "Colours", "Commands", "Permissions")) {
            assertTrue(sections.containsKey(name), name);
        }
        assertEquals("Colours", ConfigReport.section(s, "colours"));
        assertNull(ConfigReport.section(s, "nothing"));
        assertEquals("raid", ConfigReport.section(s, "Raid"));
        assertTrue(ConfigReport.keys(s, "raid").stream().allMatch(d -> d.key().startsWith("raid.")));
        assertEquals(s.definitions("Colours"), ConfigReport.keys(s, "Colours"));
    }

    @Test
    void pagesSplitASectionAndClamp() {
        Settings s = settings();
        int keys = s.definitions("Factions").size();
        int pages = ConfigReport.pages(s, "Factions");
        assertEquals((keys + ConfigReport.PAGE_SIZE - 1) / ConfigReport.PAGE_SIZE, pages);
        assertEquals(ConfigReport.PAGE_SIZE, ConfigReport.page(s, "Factions", 1).size());
        assertEquals(ConfigReport.page(s, "Factions", pages), ConfigReport.page(s, "Factions", 99));
        assertEquals(ConfigReport.page(s, "Factions", 1), ConfigReport.page(s, "Factions", -3));
    }

    @Test
    void lineShowsValueDefaultAndLimits() {
        Settings s = settings();
        s.set("faction.tagMax", 12);
        assertEquals("faction.tagMax = 12   default 16, 1 to 32", ConfigReport.line(s, s.definition("faction.tagMax")));
        assertEquals("chat.tagStyle = brackets   default brackets, one of brackets, plain",
                ConfigReport.line(s, s.definition("chat.tagStyle")));
        assertTrue(ConfigReport.line(s, s.definition("colors.enemy")).endsWith("a colour written as #rrggbb"));
        assertTrue(ConfigReport.line(s, s.definition("events.payoutCommands")).endsWith("config.json only"));
        assertEquals("true or false", ConfigReport.accepts(s, "raid.enabled"));
        assertEquals("0 to any size", ConfigReport.accepts(s, "eco.costCreate"));
    }

    @Test
    void dumpListsEverySectionAndKeyInAFixedShape() {
        Settings s = settings();
        String dump = ConfigReport.dump(s, Instant.parse("2026-10-15T12:00:00Z"));
        String[] lines = dump.split("\n");
        assertEquals("Dominion settings dump written 2026-10-15T12:00:00Z", lines[0]);
        assertEquals(s.definitions().size() + " keys in " + ConfigReport.sections(s).size() + " sections", lines[1]);
        for (Map.Entry<String, Integer> e : ConfigReport.sections(s).entrySet()) {
            assertTrue(dump.contains("\n[" + e.getKey() + "] " + e.getValue() + " keys\n"), e.getKey());
        }
        for (Settings.Def d : s.definitions()) {
            assertTrue(dump.contains("\n" + d.key() + "\n  description  " + d.label() + "\n  value        "), d.key());
        }
        assertTrue(dump.contains("\nland.reclaimWindowDays\n  description  ")
                && dump.contains("  default      7\n  accepts      0 to 365\n"));
    }

    @Test
    void wholeDecimalsKeepTheirPoint() {
        assertEquals("10.0", ConfigReport.show(10.0));
        assertEquals("0.05", ConfigReport.show(0.05));
        assertEquals("3", ConfigReport.show(3));
        assertEquals("\"\"", ConfigReport.show(""));
    }
}
