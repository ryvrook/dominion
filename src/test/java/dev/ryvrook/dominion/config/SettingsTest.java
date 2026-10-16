package dev.ryvrook.dominion.config;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);

    private Settings settings() {
        return new Settings(files, dir.resolve("config.json"));
    }

    @Test
    void firstStartWritesEveryKeyWithItsDefault() {
        Settings s = settings();
        Document d = files.read(dir.resolve("config.json"));
        assertEquals(s.definitions().size(), d.size());
        for (Settings.Def def : s.definitions()) {
            Object v = d.get(def.key());
            if (def.defaultValue() instanceof Number n) {
                assertEquals(n.doubleValue(), ((Number) v).doubleValue(), def.key());
            } else {
                assertEquals(def.defaultValue(), v, def.key());
            }
        }
    }

    /** Every literal key read anywhere in the code must be defined, or it silently reads as zero. */
    @Test
    void everyKeyReadInTheCodeIsDefined() throws IOException {
        Settings s = settings();
        Pattern read = Pattern.compile("\\.(bool|integer|decimal|text|longValue|raw|set|trySet)\\(\"([a-z]+\\.[A-Za-z0-9.]+)\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = read.matcher(Files.readString(p));
                while (m.find()) {
                    if (!s.known(m.group(2))) {
                        missing.add(m.group(2) + " in " + p.getFileName());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Keys used but not in Defaults " + missing);
    }

    @Test
    void everyRangeAndChoiceBelongsToADefinedKeyAndFitsItsDefault() {
        Settings s = settings();
        for (Settings.Def d : s.definitions()) {
            assertNull(s.check(d.key(), d.defaultValue()), "default of " + d.key() + " is outside its own limits");
        }
    }

    @Test
    void numbersAreCheckedAgainstTheirRange() {
        Settings s = settings();
        assertNotNull(s.trySet("export.intervalSeconds", "0"));
        assertNotNull(s.trySet("export.intervalSeconds", -5));
        assertNotNull(s.trySet("market.taxPercent", "150"));
        assertNull(s.trySet("market.taxPercent", "12.5"));
        assertEquals(12.5, s.decimal("market.taxPercent"));
        assertTrue(s.trySet("storage.autosaveSeconds", "5").contains("between 10 and 3600"));
    }

    @Test
    void wrongTypesAreRefused() {
        Settings s = settings();
        assertNotNull(s.trySet("faction.maxMembers", "lots"));
        assertNotNull(s.trySet("faction.maxMembers", "2.5"));
        assertNotNull(s.trySet("raid.enabled", "maybe"));
        assertNotNull(s.trySet("power.max", "NaN"));
        assertNotNull(s.trySet("power.max", "Infinity"));
        assertNotNull(s.trySet("no.such.key", "1"));
        assertEquals(20, s.integer("faction.maxMembers"));
    }

    @Test
    void booleansAcceptTheUsualWords() {
        Settings s = settings();
        assertNull(s.trySet("raid.enabled", "off"));
        assertFalse(s.bool("raid.enabled"));
        assertNull(s.trySet("raid.enabled", "YES"));
        assertTrue(s.bool("raid.enabled"));
    }

    @Test
    void choicesAreCheckedWithoutCase() {
        Settings s = settings();
        assertNotNull(s.trySet("chat.tagStyle", "curly"));
        assertNull(s.trySet("chat.tagStyle", "Plain"));
        assertNotNull(s.trySet("eco.provider", "bank"));
        assertNull(s.trySet("eco.provider", "item"));
    }

    @Test
    void changesPersistAcrossARestart() {
        settings().set("faction.maxMembers", 42);
        assertEquals(42, settings().integer("faction.maxMembers"));
    }

    @Test
    void badValuesInTheFileFallBackToDefaultsWithReasons() throws Exception {
        Files.writeString(dir.resolve("config.json"), "{\"faction.maxMembers\": -3, \"raid.enabled\": \"perhaps\","
                + " \"export.intervalSeconds\": 5, \"typo.key\": 1, \"chat.tagStyle\": \"weird\"}");
        Settings s = settings();
        assertEquals(20, s.integer("faction.maxMembers"));
        assertTrue(s.bool("raid.enabled"));
        assertEquals(5, s.integer("export.intervalSeconds"));
        assertEquals("brackets", s.text("chat.tagStyle"));
    }

    @Test
    void damagedConfigAtStartUsesDefaultsAndKeepsTheBrokenFile() throws Exception {
        Files.writeString(dir.resolve("config.json"), "{ \"raid.enabled\": false,");
        Settings s = settings();
        assertTrue(s.bool("raid.enabled"));
        try (Stream<Path> l = Files.list(dir)) {
            assertTrue(l.anyMatch(p -> p.getFileName().toString().startsWith("config.json.corrupt-")));
        }
    }

    @Test
    void reloadPicksUpEditsAndReportsProblems() throws Exception {
        Settings s = settings();
        Path f = dir.resolve("config.json");
        Document d = files.read(f);
        d.put("faction.maxMembers", 33);
        d.put("market.taxPercent", 500.0);
        Files.writeString(f, d.toJson());
        Settings.LoadReport r = s.reload();
        assertTrue(r.fileRead());
        assertEquals(33, s.integer("faction.maxMembers"));
        assertEquals(5.0, s.decimal("market.taxPercent"), "the bad value keeps the current one");
        assertEquals(1, r.problems().size());
        assertTrue(Files.readString(f).contains("500"), "the admin's file is left alone when it has problems");
    }

    @Test
    void reloadOfABrokenFileChangesNothingAndKeepsIt() throws Exception {
        Settings s = settings();
        s.set("faction.maxMembers", 12);
        Path f = dir.resolve("config.json");
        Files.writeString(f, "{ \"faction.maxMembers\": 99,, }");
        Settings.LoadReport r = s.reload();
        assertFalse(r.fileRead());
        assertEquals(12, s.integer("faction.maxMembers"));
        assertTrue(Files.readString(f).contains("99"));
    }

    @Test
    void reloadPutsARemovedKeyBackToItsDefault() throws Exception {
        Settings s = settings();
        s.set("faction.maxMembers", 12);
        Path f = dir.resolve("config.json");
        Document d = files.read(f);
        d.remove("faction.maxMembers");
        Files.writeString(f, d.toJson());
        assertTrue(s.reload().clean());
        assertEquals(20, s.integer("faction.maxMembers"));
    }

    @Test
    void fileOnlyKeysAreMarked() {
        Settings s = settings();
        assertTrue(s.fileOnly("events.payoutCommands"));
        assertFalse(s.fileOnly("raid.enabled"));
    }

    @Test
    void everyPresetSetsTheSameKeysAndAllFit() {
        Settings s = settings();
        Set<String> first = null;
        for (String name : Defaults.PRESETS) {
            Map<String, Object> p = Defaults.preset(name);
            assertFalse(p.isEmpty(), name);
            if (first == null) {
                first = new HashSet<>(p.keySet());
            }
            assertEquals(first, p.keySet(), name + " must name the same keys as the others");
            assertTrue(s.applyPreset(p).isEmpty(), name + " has a value that does not fit");
            assertEquals(name, s.text("misc.preset"));
        }
    }

    @Test
    void switchingPresetsIsReversible() {
        Settings s = settings();
        s.applyPreset(Defaults.preset("singleplayer"));
        assertFalse(s.bool("events.enabled"));
        assertEquals(0.0, s.decimal("power.lossPerDeath"));
        s.applyPreset(Defaults.preset("classic"));
        for (Map.Entry<String, Object> e : Defaults.preset("classic").entrySet()) {
            if (!e.getKey().equals("misc.preset")) {
                assertEquals(s.definition(e.getKey()).defaultValue(), s.raw(e.getKey()), e.getKey());
            }
        }
    }

    @Test
    void unknownPresetIsEmptyAndNamesIgnoreCase() {
        assertTrue(Defaults.preset("chaos").isEmpty());
        assertFalse(Defaults.isPreset(null));
        assertTrue(Defaults.isPreset("HardCore"));
        assertEquals(List.of("classic", "hardcore", "peaceful", "singleplayer"), Defaults.PRESETS);
    }

    @Test
    void readsNeverSeeAHalfReload() throws Exception {
        Settings s = settings();
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                assertTrue(s.integer("faction.maxMembers") > 0);
            }
        });
        reader.start();
        for (int i = 0; i < 50; i++) {
            s.reload();
            s.set("faction.maxMembers", 10 + i);
        }
        reader.join();
    }

    @Test
    void minimumCannotPassMaximum() {
        Settings s = settings();
        String why = s.trySet("faction.tagMin", 17);
        assertNotNull(why, "the default tagMax is 4");
        assertTrue(why.contains("faction.tagMin") && why.contains("faction.tagMax"), why);
        assertEquals(2, s.integer("faction.tagMin"));
        assertNotNull(s.trySet("faction.tagMax", 1));
        assertNull(s.trySet("faction.tagMax", 2), "equal is allowed");
        assertNull(s.trySet("faction.tagMin", 2));
        assertNotNull(s.trySet("power.min", 25.0), "the default power.max is 20");
        assertNotNull(s.trySet("power.max", -6.0), "the default power.min is -5");
        assertNull(s.trySet("power.max", 50.0));
        assertNull(s.trySet("power.min", 25.0), "fine once the maximum moved first");
        assertNotNull(s.trySet("nation.tagMin", 20));
    }

    @Test
    void outOfOrderPairsInTheFileUseDefaultsAtStart() throws Exception {
        Files.writeString(dir.resolve("config.json"), "{\"faction.tagMin\": 8, \"faction.tagMax\": 6, \"power.min\": 30.0}");
        Settings s = settings();
        assertEquals(2, s.integer("faction.tagMin"));
        assertEquals(4, s.integer("faction.tagMax"));
        assertEquals(-5.0, s.decimal("power.min"));
        List<String> problems = s.startReport().problems();
        assertEquals(2, problems.size(), String.valueOf(problems));
        assertTrue(problems.get(0).contains("cannot be above"));
    }

    @Test
    void outOfOrderPairsOnReloadKeepTheCurrentValues() throws Exception {
        Settings s = settings();
        s.set("faction.tagMax", 12);
        s.set("faction.tagMin", 5);
        Path f = dir.resolve("config.json");
        Document d = files.read(f);
        d.put("faction.tagMin", 9);
        d.put("faction.tagMax", 6);
        d.put("faction.maxMembers", 30);
        Files.writeString(f, d.toJson());
        Settings.LoadReport r = s.reload();
        assertEquals(5, s.integer("faction.tagMin"));
        assertEquals(12, s.integer("faction.tagMax"));
        assertEquals(30, s.integer("faction.maxMembers"), "the rest of the file still applies");
        assertEquals(1, r.problems().size());
        assertTrue(r.problems().get(0).contains("keep their current values"));
    }

    @Test
    void everyPairIsInOrderByDefault() {
        Settings s = settings();
        assertFalse(s.orderedPairs().isEmpty());
        for (String[] p : s.orderedPairs()) {
            assertTrue(s.known(p[0]) && s.known(p[1]), p[0] + " " + p[1]);
            assertTrue(s.decimal(p[0]) <= s.decimal(p[1]), p[0]);
        }
        assertTrue(s.startReport().problems().isEmpty());
    }
}
