package dev.ryvrook.dominion.storage;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {
    @TempDir
    Path dir;

    private final JsonFiles files = new JsonFiles(TestContexts.LOGGER);
    private final Function<UUID, PlayerData> fresh = id -> new PlayerData(id, "", 10, 100);

    private Repository open() {
        Repository r = new Repository(files, dir, TestContexts.LOGGER);
        r.loadAll(fresh);
        return r;
    }

    private static Faction faction(String tag) {
        UUID leader = UUID.randomUUID();
        Faction f = new Faction(UUID.randomUUID(), tag, tag + " name", leader, 1L);
        f.putMember(new Member(leader, tag + "Lead", Rank.LEADER, 1L));
        return f;
    }

    @Test
    void factionsClaimsAndPlayersSurviveARestart() {
        Repository r = open();
        Faction f = faction("Wolf");
        r.addFaction(f);
        UUID by = UUID.randomUUID();
        r.putClaim("default", 5L, new ClaimEntry(f.id(), 11L, by, "farm"));
        r.putClaim("default", -7L, new ClaimEntry(f.id(), 12L, null, null));
        r.putClaim("nether world", 1L, new ClaimEntry(f.id(), 13L, null, null));
        UUID pid = UUID.randomUUID();
        r.player(pid, fresh).setName("Ann");
        r.saveAll();

        Repository again = open();
        assertEquals("Wolf", again.factionByTag("WOLF").tag());
        assertEquals(3, again.claimCount(f.id()));
        ClaimEntry e = again.claimAt("default", 5L);
        assertEquals(by, e.claimedBy());
        assertEquals("farm", e.zoneId());
        assertNull(again.claimAt("default", -7L).zoneId());
        assertNotNull(again.claimAt("nether world", 1L));
        assertEquals("Ann", again.player(pid, fresh).name());
    }

    @Test
    void saveDirtyWritesOnlyWhatChanged() {
        Repository r = open();
        Faction f = faction("Bear");
        r.addFaction(f);
        assertFalse(f.isDirty());
        f.setMotd("hello");
        assertTrue(f.isDirty());
        r.saveDirty();
        assertFalse(f.isDirty());
        assertEquals("hello", open().faction(f.id()).motd());
    }

    @Test
    void newPlayerIsDirtyUntilSaved() {
        Repository r = open();
        PlayerData p = r.player(UUID.randomUUID(), fresh);
        assertTrue(p.isDirty());
        r.saveDirty();
        assertFalse(p.isDirty());
    }

    @Test
    void unloadSavesAndDrops() {
        Repository r = open();
        UUID id = UUID.randomUUID();
        r.player(id, fresh).setPower(3.5);
        r.unloadPlayer(id);
        assertNull(r.playerIfLoaded(id));
        assertEquals(3.5, r.player(id, fresh).power());
    }

    @Test
    void removedFactionDropsItsClaimsAndFileAndIsNotResaved() {
        Repository r = open();
        Faction f = faction("Gone");
        r.addFaction(f);
        r.putClaim("default", 1L, new ClaimEntry(f.id(), 1L, null, null));
        r.removeFaction(f);
        f.setMotd("late change");
        assertFalse(r.saveFaction(f), "a disbanded faction must not come back on disk");
        r.saveAll();
        Repository again = open();
        assertNull(again.faction(f.id()));
        assertNull(again.claimAt("default", 1L));
        assertFalse(Files.exists(dir.resolve("factions").resolve(f.id() + ".json")));
    }

    @Test
    void damagedFactionFileIsRestoredFromBackup() throws Exception {
        Repository r = open();
        Faction f = faction("Hawk");
        r.addFaction(f);
        f.setMotd("v2");
        r.saveFaction(f);
        Path file = dir.resolve("factions").resolve(f.id() + ".json");
        Files.writeString(file, "{ \"Id\": ");
        Repository again = open();
        assertNotNull(again.faction(f.id()), "the backup brings the faction back");
        assertEquals("", again.faction(f.id()).motd(), "the backup is the version before the last save");
    }

    @Test
    void claimsOfAMissingFactionAreSavedAside() throws Exception {
        Repository r = open();
        Faction f = faction("Lost");
        r.addFaction(f);
        r.putClaim("default", 9L, new ClaimEntry(f.id(), 1L, null, null));
        r.saveAll();
        Files.delete(dir.resolve("factions").resolve(f.id() + ".json"));
        Files.delete(JsonFiles.backupOf(dir.resolve("factions").resolve(f.id() + ".json")));
        Repository again = open();
        try (Stream<Path> s = Files.list(dir.resolve("claims"))) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("default.orphans-")));
        }
        assertNull(again.claimAt("default", 9L), "a claim of a faction that did not load is not enforced");
        again.putClaim("default", 10L, new ClaimEntry(UUID.randomUUID(), 1L, null, null));
        again.saveClaims();
        again.saveClaims();
        org.bson.Document world = files.read(dir.resolve("claims").resolve("default.json"));
        assertTrue(world.containsKey("9"), "the kept claim survives every save");
    }

    @Test
    void factionFileThatTheCodeCannotReadIsKeptAndItsClaimsSurvive() throws Exception {
        Repository r = open();
        Faction f = faction("Odd");
        r.addFaction(f);
        r.putClaim("default", 3L, new ClaimEntry(f.id(), 1L, null, null));
        r.saveAll();
        Path file = dir.resolve("factions").resolve(f.id() + ".json");
        // Valid JSON the model cannot use, as a newer or broken version might write.
        org.bson.Document d = files.read(file);
        d.put("Id", "not-a-uuid");
        Files.writeString(file, d.toJson());
        Repository again = open();
        assertNull(again.faction(f.id()));
        assertTrue(Files.exists(file), "the file stays where it is");
        again.saveAll();
        assertTrue(files.read(dir.resolve("claims").resolve("default.json")).containsKey("3"));
    }

    @Test
    void unloadKeepsThePlayerWhenTheSaveFails() throws Exception {
        Repository r = open();
        UUID id = UUID.randomUUID();
        PlayerData p = r.player(id, fresh);
        r.saveAll();
        Path players = dir.resolve("players");
        Path away = dir.resolve("players.away");
        Files.move(players, away);
        Files.writeString(players, "not a folder");
        p.setBalance(777);
        assertFalse(r.unloadPlayer(id));
        assertSame(p, r.playerIfLoaded(id), "the record stays loaded so the change is not lost");
        assertTrue(p.isDirty());
        Files.delete(players);
        Files.move(away, players);
        assertTrue(r.unloadPlayer(id));
        assertNull(r.playerIfLoaded(id));
        assertEquals(777, open().player(id, fresh).balance());
    }

    @Test
    void saveRacingADisbandNeverBringsTheFileBack() throws Exception {
        for (int round = 0; round < 50; round++) {
            Repository r = open();
            Faction f = faction("Race" + round);
            r.addFaction(f);
            Thread saver = new Thread(() -> {
                for (int i = 0; i < 20; i++) {
                    f.setMotd("m" + i);
                    r.saveFaction(f);
                }
            });
            saver.start();
            r.removeFaction(f);
            saver.join();
            assertFalse(Files.exists(dir.resolve("factions").resolve(f.id() + ".json")), "round " + round);
        }
    }

    @Test
    void damagedPlayerFileFallsBackInsteadOfResetting() throws Exception {
        Repository r = open();
        UUID id = UUID.randomUUID();
        PlayerData p = r.player(id, fresh);
        p.setBalance(500);
        r.savePlayer(p);
        p.setBalance(600);
        r.savePlayer(p);
        Files.writeString(dir.resolve("players").resolve(id + ".json"), "garbage");
        assertEquals(500, open().player(id, fresh).balance(), "the previous save is better than a fresh record");
    }

    @Test
    void reclaimMovesTheCountBetweenFactions() {
        Repository r = open();
        Faction a = faction("Aaa");
        Faction b = faction("Bbb");
        r.addFaction(a);
        r.addFaction(b);
        r.putClaim("w", 1L, new ClaimEntry(a.id(), 1L, null, null));
        r.putClaim("w", 1L, new ClaimEntry(b.id(), 2L, null, null));
        assertEquals(0, r.claimCount(a.id()));
        assertEquals(1, r.claimCount(b.id()));
        r.removeClaim("w", 1L);
        assertEquals(0, r.claimCount(b.id()));
        assertTrue(r.claimsDirty());
    }

    @Test
    void retagUpdatesTheIndex() {
        Repository r = open();
        Faction f = faction("Old");
        r.addFaction(f);
        r.retag(f, "New");
        assertNull(r.factionByTag("old"));
        assertSame(f, r.factionByTag("NEW"));
        assertTrue(r.tagTaken("new"));
    }

    @Test
    void replaceClaimOnlyWritesWhenTheEntryIsUnchanged() {
        Repository r = open();
        Faction a = faction("Aaa");
        Faction b = faction("Bbb");
        r.addFaction(a);
        r.addFaction(b);
        ClaimEntry first = new ClaimEntry(a.id(), 1L, null, null);
        assertTrue(r.replaceClaim("w", 4L, null, first), "claims an empty chunk");
        assertFalse(r.replaceClaim("w", 4L, null, new ClaimEntry(b.id(), 2L, null, null)), "the chunk is no longer empty");
        ClaimEntry zoned = new ClaimEntry(a.id(), 1L, null, "farm");
        assertTrue(r.replaceClaim("w", 4L, new ClaimEntry(a.id(), 1L, null, null), zoned), "equal by value is enough");
        // Another thread took the chunk in between, so a stale zone assignment must not land.
        r.putClaim("w", 4L, new ClaimEntry(b.id(), 3L, null, null));
        assertFalse(r.replaceClaim("w", 4L, zoned, new ClaimEntry(a.id(), 1L, null, "barn")));
        assertEquals(b.id(), r.claimAt("w", 4L).factionId());
        assertEquals(0, r.claimCount(a.id()));
        assertEquals(1, r.claimCount(b.id()));
        assertTrue(r.replaceClaim("w", 4L, r.claimAt("w", 4L), null), "a null update removes");
        assertNull(r.claimAt("w", 4L));
        assertEquals(0, r.claimCount(b.id()));
    }

    @Test
    void replaceClaimIsAtomicUnderContention() throws Exception {
        Repository r = open();
        Faction a = faction("Aaa");
        r.addFaction(a);
        int threads = 8;
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Thread> list = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int n = t;
            Thread th = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException ignored) {
                    return;
                }
                if (r.replaceClaim("w", 1L, null, new ClaimEntry(a.id(), n, null, null))) {
                    wins.incrementAndGet();
                }
            });
            list.add(th);
            th.start();
        }
        go.countDown();
        for (Thread th : list) {
            th.join();
        }
        assertEquals(1, wins.get());
        assertEquals(1, r.claimCount(a.id()));
    }

    @Test
    void failedWritesKeepTheRecordDirtyAndAreRetried() throws Exception {
        Repository r = open();
        Faction f = faction("Fail");
        r.addFaction(f);
        UUID pid = UUID.randomUUID();
        PlayerData p = r.player(pid, fresh);
        r.saveAll();
        // Swap the folders for plain files so every write under them fails.
        Path factions = dir.resolve("factions");
        Path players = dir.resolve("players");
        Path fs = dir.resolve("factions.away");
        Path ps = dir.resolve("players.away");
        Files.move(factions, fs);
        Files.move(players, ps);
        Files.writeString(factions, "not a folder");
        Files.writeString(players, "not a folder");
        f.setMotd("unsaved");
        p.setPower(1.25);
        assertFalse(r.saveFaction(f));
        assertFalse(r.savePlayer(p));
        r.saveDirty();
        assertTrue(f.isDirty(), "a failed write leaves the faction dirty");
        assertTrue(p.isDirty(), "a failed write leaves the player dirty");
        Files.delete(factions);
        Files.delete(players);
        Files.move(fs, factions);
        Files.move(ps, players);
        r.saveDirty();
        assertFalse(f.isDirty());
        assertFalse(p.isDirty());
        Repository again = open();
        assertEquals("unsaved", again.faction(f.id()).motd());
        assertEquals(1.25, again.player(pid, fresh).power());
    }

    @Test
    void racingSavesNeverLeaveAnOlderPlayerOnDisk() throws Exception {
        for (int round = 0; round < 20; round++) {
            Repository r = open();
            UUID id = UUID.randomUUID();
            PlayerData p = r.player(id, fresh);
            int threads = 6;
            int steps = 40;
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.List<Thread> list = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Thread th = new Thread(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int i = 0; i < steps; i++) {
                        synchronized (p) {
                            p.setBalance(p.balance() + 1);
                        }
                        r.savePlayer(p);
                    }
                });
                list.add(th);
                th.start();
            }
            go.countDown();
            for (Thread th : list) {
                th.join();
            }
            // Every change was followed by a save, so the newest state must be the one on disk.
            double onDisk = files.read(dir.resolve("players").resolve(id + ".json")).get("Balance", Number.class).doubleValue();
            assertEquals(p.balance(), onDisk, "round " + round);
            assertEquals(100 + threads * steps, onDisk);
        }
    }

    @Test
    void racingSavesNeverLeaveAnOlderFactionOnDisk() throws Exception {
        Repository r = open();
        Faction f = faction("Bank");
        r.addFaction(f);
        java.util.List<Thread> list = new java.util.ArrayList<>();
        for (int t = 0; t < 6; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < 40; i++) {
                    synchronized (f) {
                        f.setBank(f.bank() + 1);
                    }
                    r.saveFaction(f);
                }
            });
            list.add(th);
            th.start();
        }
        for (Thread th : list) {
            th.join();
        }
        assertEquals(240.0, open().faction(f.id()).bank());
    }

    @Test
    void anOlderSnapshotThatArrivesLateIsDropped() {
        Path file = dir.resolve("x.json");
        assertEquals(JsonFiles.Versioned.WRITTEN, files.writeVersioned(file, 2, () -> true, new org.bson.Document("V", "new")));
        assertEquals(JsonFiles.Versioned.SUPERSEDED, files.writeVersioned(file, 1, () -> true, new org.bson.Document("V", "old")));
        assertEquals("new", files.read(file).getString("V"));
        assertEquals(JsonFiles.Versioned.REFUSED, files.writeVersioned(file, 3, () -> false, new org.bson.Document("V", "gone")));
        assertEquals(JsonFiles.Versioned.WRITTEN, files.writeVersioned(file, 3, () -> true, new org.bson.Document("V", "newer")));
        assertEquals("newer", files.read(file).getString("V"));
    }
}
