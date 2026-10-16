package dev.ryvrook.dominion.importer;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared helpers for importer tests. */
final class ImportFixtures {
    private ImportFixtures() {}

    /** Settings with tagMax raised to 16, so the length rules below have room to show. */
    static Settings settings(Path dir) {
        Settings s = defaults(dir);
        s.set("faction.tagMax", 16);
        return s;
    }

    static Settings defaults(Path dir) {
        return new Settings(new JsonFiles(HytaleLogger.get("DominionImportTest")), dir.resolve("config.json"));
    }

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    static Locator locator(Path work) {
        return new Locator(work, List.of(work.resolve("mods")), work.resolve("universe"));
    }

    static Plan read(Source source, Path work) throws IOException {
        Plan plan = new Plan(source.id(), source.label());
        source.read(locator(work), plan);
        return plan;
    }

    /** A Dominion state held in plain maps. */
    static final class FakeState implements Planner.State {
        final Settings settings;
        final Map<UUID, String> factions = new HashMap<>();
        final Map<UUID, String> members = new HashMap<>();
        final Set<String> tags = new HashSet<>();
        final Set<String> claims = new HashSet<>();

        FakeState(Settings settings) {
            this.settings = settings;
        }

        @Override public Settings settings() { return settings; }
        @Override public String tagOf(UUID id) { return id == null ? null : factions.get(id); }
        @Override public Map<UUID, String> memberships() { return members; }
        @Override public boolean tagTaken(String tag) { return tags.contains(tag.toLowerCase()); }
        @Override public boolean claimed(String world, long chunk) { return claims.contains(world + "/" + chunk); }
        @Override public String knownName(UUID player) { return null; }
    }
}
