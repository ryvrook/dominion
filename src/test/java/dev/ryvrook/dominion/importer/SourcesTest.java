package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.model.Rank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcesTest {
    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String MATE = "22222222-2222-2222-2222-222222222222";

    @TempDir
    Path work;

    @Test
    void nothingFoundLeavesThePlanEmpty() throws Exception {
        for (Source s : new Source[] {new SimpleClaimsSource(), new HyFactionsSource(), new HyperFactionsSource(), new UltimateFactionsSource()}) {
            Plan p = ImportFixtures.read(s, work);
            assertNull(p.found, s.id());
            assertTrue(p.factions.isEmpty());
            assertTrue(!p.searched.isEmpty(), s.id());
        }
    }

    @Test
    void simpleClaimsLegacyJson() throws Exception {
        Path dir = work.resolve("universe").resolve("SimpleClaims");
        ImportFixtures.write(dir.resolve("Parties.json"), "{\"Parties\":[{\"Id\":\"p1\",\"Owner\":\"" + OWNER + "\",\"Name\":\"Farm\",\"Color\":16711680,"
                + "\"Members\":[\"" + OWNER + "\",\"" + MATE + "\",\"bad\"]},{\"Id\":\"p2\"}]}");
        ImportFixtures.write(dir.resolve("NameCache.json"), "{\"Values\":[{\"UUID\":\"" + OWNER + "\",\"Name\":\"Steve\"}]}");
        ImportFixtures.write(dir.resolve("Claims.json"), "{\"Dimensions\":[{\"Dimension\":\"default\",\"ChunkInfo\":["
                + "{\"UUID\":\"p1\",\"ChunkX\":1,\"ChunkY\":2},{\"UUID\":\"p1\",\"ChunkX\":1,\"ChunkY\":2},"
                + "{\"UUID\":\"nobody\",\"ChunkX\":5,\"ChunkY\":5},{\"UUID\":\"p1\",\"ChunkX\":\"x\"},"
                + "{\"UUID\":\"p1\",\"ChunkX\":9999999999,\"ChunkY\":1}]}]}");
        Plan p = ImportFixtures.read(new SimpleClaimsSource(), work);
        assertEquals(dir, p.found);
        assertEquals(1, p.factions.size());
        Plan.Faction f = p.factions.get(0);
        assertEquals("Steve", f.name);
        assertEquals("Farm", f.description);
        assertEquals("#ff0000", f.color);
        assertEquals(2, f.members.size());
        assertEquals(Rank.LEADER, f.members.get(UUID.fromString(OWNER)).rank());
        assertEquals(1, f.chunkCount());
        assertEquals(4, p.skipped.size(), p.skipped.toString());
    }

    @Test
    void brokenJsonIsAReadError() throws Exception {
        Path dir = work.resolve("universe").resolve("SimpleClaims");
        ImportFixtures.write(dir.resolve("Parties.json"), "{\"Parties\":[{\"Id\": ");
        assertThrows(Exception.class, () -> ImportFixtures.read(new SimpleClaimsSource(), work));
    }

    @Test
    void hyperFactionsSkipsUnreadableFilesAndKeepsTheRest() throws Exception {
        Path dir = work.resolve("mods").resolve("com.hyperfactions_HyperFactions").resolve("data").resolve("factions");
        ImportFixtures.write(dir.resolve("a.json"), "{\"id\":\"a\",\"name\":\"Wolves\",\"tag\":\"WLF\",\"color\":\"#00ff00\",\"createdAt\":1700000000,"
                + "\"members\":[{\"uuid\":\"" + OWNER + "\",\"role\":\"LEADER\",\"username\":\"Steve\"},{\"uuid\":\"x\"},"
                + "{\"uuid\":\"" + MATE + "\",\"role\":\"officer\"}],"
                + "\"claims\":[{\"world\":\"default\",\"chunkX\":3,\"chunkZ\":-4},{\"world\":\"default\",\"chunkX\":1.5,\"chunkZ\":0}]}");
        ImportFixtures.write(dir.resolve("b.json"), "{broken");
        ImportFixtures.write(dir.resolve("c.json"), "{\"name\":\"No id\"}");
        Plan p = ImportFixtures.read(new HyperFactionsSource(), work);
        assertEquals(1, p.factions.size());
        Plan.Faction f = p.factions.get(0);
        assertEquals("WLF", f.preferredTag);
        assertEquals(UUID.fromString(OWNER), f.leader);
        assertEquals(Rank.OFFICER, f.members.get(UUID.fromString(MATE)).rank());
        assertEquals(1_700_000_000_000L, f.createdAt);
        assertEquals(1, f.chunkCount());
        assertEquals(4, p.skipped.size(), p.skipped.toString());
    }

    @Test
    void hyFactionsReadsFactionFilesAndGrades() throws Exception {
        Path config = work.resolve("mods").resolve("Kaws_Hyfaction").resolve("config");
        ImportFixtures.write(config.resolve("faction").resolve("f.json"), "{\"Id\":\"f1\",\"Owner\":\"" + OWNER + "\",\"Name\":\"Raiders\","
                + "\"Members\":[\"" + MATE + "\"],\"MemberGrades\":{\"" + MATE + "\":\"Officer\"}}");
        Plan p = ImportFixtures.read(new HyFactionsSource(), work);
        assertTrue(p.experimental);
        assertEquals(1, p.factions.size());
        assertEquals(Rank.OFFICER, p.factions.get(0).members.get(UUID.fromString(MATE)).rank());
        assertTrue(p.notes.stream().anyMatch(n -> n.contains("Claims.json was not found")));
    }

    @Test
    void ultimateFactionsFoldsSixteenBlockCellsAndSkipsZones() throws Exception {
        Path dir = work.resolve("plugins_data").resolve("HytaleFactions");
        ImportFixtures.write(dir.resolve("factions.json"), "{\"factions\":[{\"id\":\"u1\",\"name\":\"Kings\",\"leaderUuid\":\"" + OWNER + "\","
                + "\"members\":{\"" + MATE + "\":\"MEMBER\",\"zzz\":\"OFFICER\"}}]}");
        ImportFixtures.write(dir.resolve("claims.json"), "{\"claims\":[{\"factionId\":\"u1\",\"worldKey\":\"default\",\"chunkX\":0,\"chunkZ\":0},"
                + "{\"factionId\":\"u1\",\"worldKey\":\"default\",\"chunkX\":1,\"chunkZ\":1},"
                + "{\"factionId\":\"__SAFEZONE__\",\"worldKey\":\"default\",\"chunkX\":9,\"chunkZ\":9},"
                + "{\"factionId\":\"u1\",\"chunkX\":2,\"chunkZ\":2},{\"factionId\":\"ghost\",\"worldKey\":\"default\",\"chunkX\":2,\"chunkZ\":2}]}");
        Plan p = ImportFixtures.read(new UltimateFactionsSource(), work);
        Plan.Faction f = p.factions.get(0);
        assertEquals(UUID.fromString(OWNER), f.leader);
        assertNotNull(f.members.get(UUID.fromString(OWNER)), "a leader missing from members is added");
        assertEquals(2, f.sourceCells);
        assertEquals(dev.ryvrook.dominion.util.Keys.chunkSize() == 32 ? 1 : 2, f.chunkCount());
        assertTrue(p.skipped.stream().anyMatch(s -> s.contains("safe zone")), p.skipped.toString());
        assertTrue(p.skipped.stream().anyMatch(s -> s.contains("unknown faction ghost")), p.skipped.toString());
        assertTrue(p.skipped.stream().anyMatch(s -> s.contains("missing its world")), p.skipped.toString());
    }

    @Test
    void sourceMessagesFollowTheWordingRule() throws Exception {
        Path dir = work.resolve("plugins_data").resolve("HytaleFactions");
        ImportFixtures.write(dir.resolve("factions.json"), "{\"factions\":[{\"id\":\"u1\"}]}");
        Plan p = ImportFixtures.read(new UltimateFactionsSource(), work);
        for (String line : p.skipped) {
            assertTrue(!line.contains(";") && !line.contains("—"), line);
        }
        for (String line : p.notes) {
            assertTrue(!line.contains(";") && !line.contains("—"), line);
        }
    }
}
