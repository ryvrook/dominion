package dev.ryvrook.dominion.importer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a real Simple Claims shaped database with the sqlite3 tool when it is installed, then
 * reads it back and checks that damaged copies only ever fail with a readable IOException.
 */
class SqliteFileTest {
    private static final String OWNER = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path dir;

    private Path build(int parties) throws Exception {
        Path db = dir.resolve("SimpleClaims.db");
        StringBuilder sql = new StringBuilder("""
                CREATE TABLE parties (id TEXT PRIMARY KEY, owner TEXT, name TEXT, color INTEGER, created_user_name TEXT);
                CREATE TABLE party_members (party_id TEXT, member_uuid TEXT);
                CREATE TABLE claims (id INTEGER PRIMARY KEY, party_owner TEXT, dimension TEXT, chunkx INTEGER, chunkz INTEGER);
                CREATE TABLE name_cache (uuid TEXT, name TEXT);
                INSERT INTO name_cache VALUES ('%s', 'Steve');
                """.formatted(OWNER));
        for (int i = 0; i < parties; i++) {
            String owner = i == 0 ? OWNER : String.format("%08d-0000-0000-0000-000000000000", i);
            sql.append("INSERT INTO parties VALUES ('p").append(i).append("', '").append(owner).append("', '")
                    .append("Party with a long name to fill pages ".repeat(i % 7 + 1)).append("', ").append(i).append(", 'u").append(i).append("');\n");
            sql.append("INSERT INTO claims (party_owner, dimension, chunkx, chunkz) VALUES ('p").append(i).append("', 'default', ")
                    .append(i).append(", ").append(-i).append(");\n");
        }
        sql.append("INSERT INTO parties VALUES ('big', '").append(OWNER).append("', '").append("x".repeat(9000)).append("', NULL, NULL);\n");
        Process p;
        try {
            p = new ProcessBuilder("sqlite3", db.toString()).redirectErrorStream(true).start();
        } catch (IOException e) {
            Assumptions.abort("sqlite3 is not installed");
            return null;
        }
        p.getOutputStream().write(sql.toString().getBytes());
        p.getOutputStream().close();
        p.getInputStream().readAllBytes();
        Assumptions.assumeTrue(p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0, "sqlite3 failed");
        return db;
    }

    @Test
    void readsTablesRowsAndOverflowPages() throws Exception {
        Path db = build(400);
        SqliteFile f = SqliteFile.open(db);
        assertTrue(f.hasTable("parties"));
        assertTrue(f.hasTable("CLAIMS"));
        assertFalse(f.hasTable("missing"));
        List<Map<String, Object>> parties = f.rows("parties");
        assertEquals(401, parties.size());
        Map<String, Object> first = parties.get(0);
        assertEquals("p0", first.get("id"));
        assertEquals(OWNER, first.get("owner"));
        assertEquals(0L, first.get("color"));
        Map<String, Object> big = parties.stream().filter(r -> "big".equals(r.get("id"))).findFirst().orElseThrow();
        assertEquals(9000, big.get("name").toString().length(), "a row larger than a page follows its overflow chain");
        List<Map<String, Object>> claims = f.rows("claims");
        assertEquals(400, claims.size());
        assertEquals(1L, claims.get(0).get("id"), "an integer primary key reads the row id");
        assertEquals(-399L, claims.get(399).get("chunkz"));
        assertTrue(f.rows("missing").isEmpty());
    }

    @Test
    void theSimpleClaimsReaderUsesTheDatabase() throws Exception {
        Path db = build(3);
        Path folder = dir.resolve("work").resolve("universe").resolve("SimpleClaims");
        Files.createDirectories(folder);
        Files.copy(db, folder.resolve("SimpleClaims.db"));
        Plan plan = ImportFixtures.read(new SimpleClaimsSource(), dir.resolve("work"));
        assertEquals(folder.resolve("SimpleClaims.db"), plan.found);
        assertEquals(4, plan.factions.size());
        assertEquals("Steve", plan.factions.get(0).name);
        assertEquals(1, plan.factions.get(0).chunkCount());
    }

    @Test
    void notADatabaseIsRefused() throws Exception {
        Path f = dir.resolve("x.db");
        Files.writeString(f, "hello");
        assertThrows(IOException.class, () -> SqliteFile.open(f));
        Files.write(f, new byte[200]);
        assertThrows(IOException.class, () -> SqliteFile.open(f));
    }

    /** Random damage to a real file never escapes as anything but an IOException and never hangs. */
    @Test
    void damagedFilesFailCleanly() throws Exception {
        byte[] good = Files.readAllBytes(build(200));
        Random rnd = new Random(42);
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int round = 0; round < 1500; round++) {
                byte[] bad = good.clone();
                int flips = 1 + rnd.nextInt(40);
                for (int i = 0; i < flips; i++) {
                    int at = rnd.nextInt(bad.length);
                    bad[at] = (byte) rnd.nextInt(256);
                }
                if (rnd.nextInt(5) == 0) {
                    bad = java.util.Arrays.copyOf(bad, Math.max(100, rnd.nextInt(bad.length)));
                }
                Path f = dir.resolve("bad.db");
                Files.write(f, bad);
                try {
                    SqliteFile s = SqliteFile.open(f);
                    for (String t : new String[] {"parties", "claims", "party_members", "name_cache"}) {
                        s.rows(t);
                    }
                } catch (IOException expected) {
                    // a readable reason, which is what the preview shows
                }
            }
        });
    }
}
