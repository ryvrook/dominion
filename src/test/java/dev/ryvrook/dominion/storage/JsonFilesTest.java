package dev.ryvrook.dominion.storage;

import dev.ryvrook.dominion.TestContexts;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JsonFilesTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);

    @Test
    void writeThenReadRoundTrips() {
        Path f = dir.resolve("a/b/x.json");
        assertTrue(files.write(f, new Document("K", 1).append("S", "é \" \n")));
        Document d = files.read(f);
        assertEquals(1, d.getInteger("K"));
        assertEquals("é \" \n", d.getString("S"));
    }

    @Test
    void missingFileReadsAsNull() {
        assertNull(files.read(dir.resolve("nope.json")));
    }

    @Test
    void secondWriteKeepsTheFirstAsBackup() {
        Path f = dir.resolve("x.json");
        files.write(f, new Document("V", 1));
        files.write(f, new Document("V", 2));
        assertEquals(2, files.read(f).getInteger("V"));
        assertEquals(1, Document.parse(read(JsonFiles.backupOf(f))).getInteger("V"));
    }

    @Test
    void damagedFileIsMovedAsideAndTheBackupIsUsed() throws Exception {
        Path f = dir.resolve("x.json");
        files.write(f, new Document("V", 1));
        files.write(f, new Document("V", 2));
        Files.writeString(f, "{\"V\": 3,,, broken");
        Document d = files.read(f);
        assertNotNull(d);
        assertEquals(1, d.getInteger("V"), "falls back to the backup");
        assertTrue(listNames().stream().anyMatch(n -> n.startsWith("x.json.corrupt-")), "the damaged file is kept for inspection");
    }

    @Test
    void damagedFileWithoutBackupReadsAsNullAndIsKept() throws Exception {
        Path f = dir.resolve("y.json");
        Files.writeString(f, "not json at all");
        assertNull(files.read(f));
        assertFalse(Files.exists(f));
        assertTrue(listNames().stream().anyMatch(n -> n.startsWith("y.json.corrupt-")));
    }

    @Test
    void converterFailureLeavesTheFileInPlaceAndKeepsACopy() {
        Path f = dir.resolve("z.json");
        files.write(f, new Document("Id", "good"));
        files.write(f, new Document("Id", 5));
        String id = files.read(f, d -> {
            Object v = d.get("Id");
            if (!(v instanceof String s)) {
                throw new IllegalStateException("wrong type");
            }
            return s;
        });
        assertNull(id, "a record the code cannot use is skipped, not swapped for an older version");
        assertTrue(Files.exists(f), "valid JSON is never quarantined");
        assertTrue(listNames().stream().noneMatch(n -> n.contains(".corrupt-")));
        assertTrue(listNames().stream().anyMatch(n -> n.startsWith("z.json.unread-")), "a copy survives any later save");
        assertEquals(5, files.read(f).getInteger("Id"));
    }

    @Test
    void guardedWriteRespectsItsGuard() {
        Path f = dir.resolve("g.json");
        assertFalse(files.writeIf(f, () -> false, () -> new Document("V", 1)));
        assertFalse(Files.exists(f));
        assertTrue(files.writeIf(f, () -> true, () -> new Document("V", 2)));
        assertEquals(2, files.read(f).getInteger("V"));
    }

    @Test
    void emptyFileIsTreatedAsDamaged() throws Exception {
        Path f = dir.resolve("e.json");
        Files.writeString(f, "");
        assertNull(files.read(f));
    }

    @Test
    void parseLeavesAnUnreadableFileInPlace() throws Exception {
        Path f = dir.resolve("c.json");
        Files.writeString(f, "{ oops");
        assertThrows(java.io.IOException.class, () -> files.parse(f));
        assertTrue(Files.exists(f));
    }

    @Test
    void noTemporaryFilesAreLeftBehind() {
        Path f = dir.resolve("t.json");
        for (int i = 0; i < 5; i++) {
            files.write(f, new Document("I", i));
        }
        assertTrue(listNames().stream().noneMatch(n -> n.endsWith(".tmp")));
    }

    @Test
    void sweepRemovesStaleTemporaryFiles() throws Exception {
        Files.createDirectories(dir.resolve("players"));
        Files.writeString(dir.resolve("players/a.json.99-1.tmp"), "{");
        Files.writeString(dir.resolve("players/a.json"), "{}");
        assertEquals(1, files.sweep(dir));
        assertTrue(Files.exists(dir.resolve("players/a.json")));
    }

    @Test
    void listMatchesOnlyJsonFiles() throws Exception {
        files.write(dir.resolve("a.json"), new Document());
        files.write(dir.resolve("a.json"), new Document());
        Files.writeString(dir.resolve("b.json.123-4.tmp"), "");
        List<Path> found = files.list(dir, "*.json");
        assertEquals(1, found.size());
    }

    @Test
    void concurrentWritesToOneFileNeverCorruptIt() throws Exception {
        Path f = dir.resolve("busy.json");
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int n = t;
            Thread th = new Thread(() -> {
                for (int i = 0; i < 25; i++) {
                    files.write(f, new Document("T", n).append("I", i).append("Pad", "x".repeat(2000)));
                }
            });
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) {
            th.join();
        }
        Document d = Document.parse(read(f));
        assertEquals(24, d.getInteger("I"));
    }

    private List<String> listNames() {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
