package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.api.SiegeInfo;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiImplTest {
    Fixtures fx;
    ApiImpl api;

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        api = new ApiImpl(fx.ctx, null);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void factionsListsZonesTooSortedByTag() {
        Faction b = fx.faction("Bravo");
        Faction a = fx.faction("alpha");
        Faction safe = new Faction(UUID.randomUUID(), "Spawn", "Spawn", null, 1L);
        safe.setType(FactionType.SAFEZONE);
        fx.repo().addFaction(safe);
        List<Faction> all = api.factions();
        assertEquals(List.of(a, b, safe), all);
        assertEquals(safe, api.factionById(safe.id()).orElseThrow());
        assertTrue(api.factionById(null).isEmpty());
        assertTrue(api.factionById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void siegesCarryBothSidesAndThePhase() {
        Faction att = fx.faction("ATK");
        Faction def = fx.faction("DEF");
        long chunk = Keys.chunk(3, -4);
        Siege running = new Siege(UUID.randomUUID(), att.id(), def.id(), "default", chunk, 100.5, 64, -120.5, 2000L, att.leader());
        running.setPhase(Siege.Phase.BREACHED, 9000L);
        Siege older = new Siege(UUID.randomUUID(), def.id(), att.id(), "default", chunk, 1, 2, 3, 1000L, null);
        Siege done = new Siege(UUID.randomUUID(), att.id(), UUID.randomUUID(), "default", chunk, 1, 2, 3, 500L, null);
        done.setPhase(Siege.Phase.DEFENDED, 600L);
        fx.ctx.register(SiegeService.class, TestContexts.fake(SiegeService.class, Map.of(
                "active", args -> List.of(running, older),
                "history", args -> List.of(done))));

        List<SiegeInfo> now = api.sieges();
        assertEquals(2, now.size());
        assertEquals(older.id(), now.get(0).id(), "oldest first");
        SiegeInfo s = now.get(1);
        assertEquals(att.id(), s.attackerId());
        assertEquals("ATK", s.attackerTag());
        assertEquals("DEF", s.defenderTag());
        assertEquals("BREACHED", s.phase());
        assertEquals(9000L, s.phaseEndsAt());
        assertEquals(3, s.chunkX());
        assertEquals(-4, s.chunkZ());
        assertEquals(100.5, s.x());
        assertTrue(s.active());

        List<SiegeInfo> history = api.siegeHistory(att.id(), 5);
        assertEquals(1, history.size());
        assertNull(history.get(0).defenderTag(), "a disbanded defender has no tag");
        assertFalse(history.get(0).active());
        assertTrue(api.siegeHistory(def.id(), 5).isEmpty());
        assertTrue(api.siegeHistory(att.id(), 0).isEmpty());
    }

    @Test
    void noSiegesWithoutTheSiegeModule() {
        assertTrue(api.sieges().isEmpty());
        assertTrue(api.siegeHistory(null, 10).isEmpty());
    }
}
