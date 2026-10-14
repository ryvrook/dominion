package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTest {
    @TempDir
    Path dir;

    private ImportFixtures.FakeState state;
    private Plan plan;
    private final Map<String, String> ledger = new HashMap<>();

    @BeforeEach
    void setUp() {
        state = new ImportFixtures.FakeState(ImportFixtures.settings(dir));
        plan = new Plan("test", "Test");
    }

    private Plan.Faction faction(String id, String name, UUID leader, UUID... members) {
        Plan.Faction f = new Plan.Faction(id);
        f.name = name;
        f.leader = leader;
        f.addMember(leader, null, Rank.LEADER);
        for (UUID m : members) {
            f.addMember(m, "M" + m.toString().substring(0, 4), Rank.MEMBER);
        }
        plan.factions.add(f);
        return f;
    }

    @Test
    void aCleanFactionBecomesOneDecision() {
        UUID lead = UUID.randomUUID();
        Plan.Faction f = faction("a", "Wolves", lead, UUID.randomUUID());
        f.addChunk("default", Keys.chunk(1, 1));
        Planner.Resolved r = Planner.resolve(plan, ledger, state);
        assertEquals(1, r.decisions().size());
        Planner.Decision d = r.decisions().get(0);
        assertEquals("Wolves", d.tag);
        assertEquals(lead, d.leader);
        assertEquals(1, d.joins.size());
        assertEquals(1, d.claimCount());
        assertTrue(d.issues.isEmpty(), d.issues.toString());
    }

    @Test
    void alreadyImportedFactionsAreSkipped() {
        UUID existing = UUID.randomUUID();
        state.factions.put(existing, "WLF");
        ledger.put("a", existing.toString());
        ledger.put("b", UUID.randomUUID().toString());
        faction("a", "Wolves", UUID.randomUUID());
        faction("b", "Bears", UUID.randomUUID());
        Planner.Resolved r = Planner.resolve(plan, ledger, state);
        assertTrue(r.decisions().isEmpty());
        assertTrue(r.skipped().get(0).contains("already imported as WLF"));
        assertTrue(r.skipped().get(1).contains("no longer exists"));
    }

    @Test
    void membersInDominionAreLeftOutAndTheBestFreeMemberLeads() {
        UUID lead = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        state.members.put(lead, "OLD");
        Plan.Faction f = faction("a", "Wolves", lead, member);
        f.addMember(officer, "Off", Rank.OFFICER);
        Planner.Decision d = Planner.resolve(plan, ledger, state).decisions().get(0);
        assertEquals(officer, d.leader);
        assertEquals(1, d.joins.size());
        assertTrue(d.issues.stream().anyMatch(i -> i.contains("already in the Dominion faction OLD")));
        assertTrue(d.issues.stream().anyMatch(i -> i.contains("Off becomes the leader")));
    }

    @Test
    void aFactionWithNobodyFreeIsSkippedAndFreesNobody() {
        UUID lead = UUID.randomUUID();
        state.members.put(lead, "OLD");
        faction("a", "Wolves", lead);
        Planner.Resolved r = Planner.resolve(plan, ledger, state);
        assertTrue(r.decisions().isEmpty());
        assertTrue(r.skipped().get(0).contains("none of its members is free"));
    }

    @Test
    void aPlayerInTwoSourceFactionsJoinsOnlyTheFirst() {
        UUID both = UUID.randomUUID();
        faction("a", "Wolves", UUID.randomUUID(), both);
        faction("b", "Bears", UUID.randomUUID(), both);
        Planner.Resolved r = Planner.resolve(plan, ledger, state);
        assertEquals(1, r.decisions().get(0).joins.size());
        assertEquals(0, r.decisions().get(1).joins.size());
    }

    @Test
    void tagsAndChunksDoNotCollide() {
        state.tags.add("wolves");
        long chunk = Keys.chunk(4, 4);
        state.claims.add("default/" + Keys.chunk(9, 9));
        Plan.Faction a = faction("a", "Wolves", UUID.randomUUID());
        Plan.Faction b = faction("b", "Wolves!", UUID.randomUUID());
        a.addChunk("default", chunk);
        a.addChunk("default", Keys.chunk(9, 9));
        b.addChunk("default", chunk);
        Planner.Resolved r = Planner.resolve(plan, ledger, state);
        Planner.Decision da = r.decisions().get(0);
        Planner.Decision db = r.decisions().get(1);
        assertEquals("Wolves2", da.tag);
        assertEquals("Wolves3", db.tag);
        assertEquals(1, da.claimCount());
        assertEquals(1, da.taken);
        assertEquals(0, db.claimCount());
        assertEquals(1, db.duplicate);
        assertTrue(da.issues.stream().anyMatch(i -> i.contains("already used in Dominion")));
    }

    @Test
    void namesAreCleanedAndCut() {
        faction("a", "Bad\u0007Name " + "x".repeat(80), UUID.randomUUID());
        Planner.Decision d = Planner.resolve(plan, ledger, state).decisions().get(0);
        assertTrue(d.name.length() <= 32);
        assertTrue(!d.name.contains("\u0007"));
        assertEquals("Player" + d.leader.toString().substring(0, 8), d.leaderName);
    }

    @Test
    void pluralsReadNaturally() {
        assertEquals("1 faction", Planner.plural(1, "faction"));
        assertEquals("2 factions", Planner.plural(2, "faction"));
        assertEquals("1 of its chunks is", Planner.plural(1, "of its chunks"));
        assertEquals("3 of its chunks are", Planner.plural(3, "of its chunks"));
    }
}
