package dev.ryvrook.dominion.progress;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StrikesTest {
    @TempDir
    Path dir;

    private Strikes strikes() {
        Strikes s = new Strikes(new JsonFiles(HytaleLogger.get("DominionTest")), dir.resolve("progress").resolve("strikes.json"));
        s.load();
        return s;
    }

    @Test
    void strikesSurviveARestart() {
        UUID faction = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        Strikes a = strikes();
        assertEquals(1, a.add(faction, new Strikes.Strike(2, "Griefing spawn", admin, "Mod", 100L)));
        assertEquals(2, a.add(faction, new Strikes.Strike(1, "Spam", null, "Console", 200L)));
        Strikes b = strikes();
        assertEquals(2, b.of(faction).size());
        assertEquals(3, b.weight(faction));
        assertEquals("Griefing spawn", b.of(faction).get(0).reason());
        assertEquals(admin, b.of(faction).get(0).by());
        assertNull(b.of(faction).get(1).by());
        assertEquals(200L, b.of(faction).get(1).at());
    }

    @Test
    void removeUsesPositionsAndPersists() {
        UUID faction = UUID.randomUUID();
        Strikes a = strikes();
        a.add(faction, new Strikes.Strike(2, "One", null, "A", 1L));
        a.add(faction, new Strikes.Strike(5, "Two", null, "A", 2L));
        assertNull(a.remove(faction, 2));
        assertNull(a.remove(faction, -1));
        assertEquals("One", a.remove(faction, 0).reason());
        Strikes b = strikes();
        assertEquals(1, b.of(faction).size());
        assertEquals(5, b.weight(faction));
    }

    @Test
    void negativePointsNeverRaiseTheWeightAndForgetClears() {
        UUID faction = UUID.randomUUID();
        Strikes a = strikes();
        a.add(faction, new Strikes.Strike(-4, "Odd import", null, "A", 1L));
        a.add(faction, new Strikes.Strike(1, "Real", null, "A", 2L));
        assertEquals(1, a.weight(faction));
        a.forget(faction);
        assertEquals(0, strikes().of(faction).size());
        assertEquals(0, strikes().weight(UUID.randomUUID()));
    }
}
