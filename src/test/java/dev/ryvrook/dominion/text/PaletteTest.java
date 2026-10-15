package dev.ryvrook.dominion.text;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaletteTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);

    @AfterEach
    void backToDefaults() {
        Palette.refresh(key -> null);
    }

    @Test
    void defaultsMatchTheShippedColours() {
        new Settings(files, dir.resolve("config.json"));
        assertEquals("#E8A93B", Msg.GOLD);
        assertEquals("#ff6b6b", Msg.BAD);
        for (Relation r : Relation.values()) {
            assertEquals(r.defaultColor(), r.color(), r.name());
        }
        assertEquals("#e0413c", Palette.warZone());
    }

    @Test
    void setRefreshesMsgRelationAndListeners() {
        Settings s = new Settings(files, dir.resolve("config.json"));
        AtomicInteger calls = new AtomicInteger();
        Palette.onChange(calls::incrementAndGet);
        assertNull(s.trySet("colors.accent", "#123456"));
        assertNull(s.trySet("colors.enemy", "#abcdef"));
        assertNull(s.trySet("colors.safeZone", "#010203"));
        assertEquals("#123456", Msg.GOLD);
        assertEquals("#abcdef", Relation.ENEMY.color());
        assertEquals("#010203", Palette.safeZone());
        assertTrue(calls.get() >= 3);
    }

    @Test
    void coloursMustBeHex() {
        Settings s = new Settings(files, dir.resolve("config.json"));
        assertNotNull(s.trySet("colors.good", "green"));
        assertNotNull(s.trySet("colors.good", "#12345"));
        assertNotNull(s.trySet("colors.good", "#12345g"));
        assertNull(s.trySet("colors.good", "#A1b2C3"));
        assertEquals("#A1b2C3", Msg.GOOD);
    }

    @Test
    void reloadPicksUpAColourFromTheFile() throws Exception {
        Path file = dir.resolve("config.json");
        Settings s = new Settings(files, file);
        String json = Files.readString(file).replace("\"#7aa7e6\"", "\"#0000ff\"");
        Files.writeString(file, json);
        s.reload();
        assertEquals("#0000ff", Msg.INFO);
    }

    @Test
    void settingsListenersRunOnEveryChange() {
        Settings s = new Settings(files, dir.resolve("config.json"));
        AtomicInteger calls = new AtomicInteger();
        s.onChange(x -> calls.incrementAndGet());
        s.set("hud.top", 10);
        s.applyPreset(dev.ryvrook.dominion.config.Defaults.preset("classic"));
        assertEquals(2, calls.get());
    }
}
