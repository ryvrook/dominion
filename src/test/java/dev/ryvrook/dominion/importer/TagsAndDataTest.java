package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagsAndDataTest {
    @TempDir
    Path dir;

    @Test
    void tagsKeepLettersAndDigitsWithinTheLengthRules() {
        Settings s = ImportFixtures.settings(dir);
        assertEquals("Steve123", Tags.base("Steve_123", s));
        assertEquals("Imported", Tags.base("___", s));
        assertEquals("Imported", Tags.base(null, s));
        assertEquals("Ab0", Tags.base("Ab", s));
        assertEquals("Averylongn", Tags.base("A very long name indeed", s));
        assertEquals("Imported", Tags.base("ééé", s));
    }

    @Test
    void uniqueTagsAvoidTakenAndBannedOnes() {
        Settings s = ImportFixtures.settings(dir);
        assertEquals("Wolves", Tags.unique("Wolves", s, t -> true));
        assertEquals("Wolves2", Tags.unique("Wolves", s, t -> !t.equals("Wolves")));
        assertEquals("Averylong2", Tags.unique("A very long name", s, t -> !t.equals("Averylongn")));
        assertEquals("admin2", Tags.unique("admin", s, t -> true), "a banned tag gets a number");
        assertNull(Tags.unique("Wolves", s, t -> false));
    }

    @Test
    void coordinatesMustBeWholeAndSane() {
        assertEquals(12, Data.coord(12L));
        assertEquals(-3, Data.coord(-3.0));
        assertEquals(7, Data.coord(" 7 "));
        assertNull(Data.coord(1.5));
        assertNull(Data.coord(Double.NaN));
        assertNull(Data.coord(1L << 40));
        assertNull(Data.coord("x"));
        assertNull(Data.coord(null));
    }

    @Test
    void cellsFoldIntoDominionChunks() {
        int size = Keys.chunkSize();
        assertEquals(1, Data.chunks(3, 4, size).length);
        assertEquals(Keys.chunk(3, 4), Data.chunks(3, 4, size)[0]);
        assertEquals(Keys.chunkAtBlock(100, -5), Data.chunks(100, -5, 1)[0]);
        if (size == 32) {
            assertEquals(Keys.chunk(0, 0), Data.chunks(1, 1, 16)[0]);
            assertEquals(Keys.chunk(-1, -1), Data.chunks(-1, -1, 16)[0]);
            assertEquals(4, Data.chunks(0, 0, 64).length);
        }
        assertEquals(0, Data.chunks(Integer.MAX_VALUE, 0, 64).length);
    }

    @Test
    void helpersReadLooseValues() {
        assertEquals("#ff0000", Data.color(0xFFFF0000L));
        assertEquals("#00ff00", Data.hexColor("00FF00"));
        assertNull(Data.hexColor("green"));
        assertEquals(5000, Data.epochMillis(5L));
        assertEquals(200_000_000_000L, Data.epochMillis(200_000_000_000L));
        assertEquals(0, Data.epochMillis(-1L));
        assertNull(Data.uuid("nope"));
    }

    @Test
    void jsonFilesMustHoldAnObject() throws Exception {
        Path f = dir.resolve("a.json");
        ImportFixtures.write(f, "[1,2]");
        assertThrows(java.io.IOException.class, () -> Data.json(f));
        ImportFixtures.write(f, "   ");
        assertEquals(Set.of(), Data.json(f).keySet());
    }
}
