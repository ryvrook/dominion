package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.land.ClaimAccess;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.power.PowerServiceImpl;
import dev.ryvrook.dominion.relation.RelationServiceImpl;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StaffActionsTest {
    Fixtures fx;
    ApiImpl api;

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        fx.ctx.register(PowerService.class, new PowerServiceImpl(fx.ctx));
        fx.ctx.register(RelationService.class, new RelationServiceImpl(fx.ctx));
        fx.ctx.register(ClaimService.class, ClaimAccess.of(fx.ctx));
        api = new ApiImpl(fx.ctx, null);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    static void ok(Result r) {
        assertTrue(r.success(), r.message());
    }

    void claim(Faction f, String world, int x, int z) {
        fx.repo().putClaim(world, Keys.chunk(x, z), new ClaimEntry(f.id(), 1L, f.leader(), null));
    }

    @Test
    void disbandRemovesTheFaction() {
        Faction f = fx.faction("NRT");
        ok(api.disband(f.id(), "RestAPI key ops"));
        assertNull(fx.repo().faction(f.id()));
        assertTrue(api.disband(f.id(), "RestAPI key ops").failed());
        assertTrue(api.disband(null, null).failed());
    }

    @Test
    void setLeaderMovesLeadershipToAMember() {
        Faction f = fx.faction("NRT");
        UUID old = f.leader();
        UUID officer = fx.member(f, "Mira", Rank.OFFICER);
        ok(api.setLeader(f.id(), officer, "ops"));
        assertEquals(officer, f.leader());
        assertNotEquals(Rank.LEADER, f.rankOf(old));
        assertTrue(api.setLeader(f.id(), fx.player("Stranger"), "ops").failed());
        assertTrue(api.setLeader(f.id(), null, "ops").failed());
    }

    @Test
    void setPowerOnlyForKnownPlayers() {
        UUID p = fx.player("Mira");
        Result r = api.setPower(p, 3, "ops");
        ok(r);
        assertEquals(3.0, api.powerOf(p));
        assertTrue(r.message().contains("Mira"), r.message());
        UUID stranger = UUID.randomUUID();
        assertTrue(api.setPower(stranger, 3, "ops").failed());
        assertNull(fx.repo().playerIfLoaded(stranger), "an unknown id gets no record");
        assertTrue(api.setPower(p, Double.NaN, "ops").failed());
    }

    @Test
    void unclaimOneChunkOrAll() {
        Faction f = fx.faction("NRT");
        claim(f, "default", 1, 1);
        claim(f, "default", 1, 2);
        claim(f, "nether", 5, 5);
        ok(api.unclaim("default", 1, 1, "ops"));
        assertNull(fx.repo().ownerAt("default", Keys.chunk(1, 1)));
        assertTrue(api.unclaim("default", 1, 1, "ops").failed());
        Result all = api.unclaimAll(f.id(), "ops");
        ok(all);
        assertTrue(all.message().contains("2"), all.message());
        assertEquals(0, fx.repo().claimCount(f.id()));
        assertTrue(api.unclaimAll(f.id(), "ops").failed());
    }

    @Test
    void aBlankCallerNameStillReadsAsAName() {
        assertEquals("another mod", ApiImpl.by(" "));
        assertEquals("RestAPI key ops", ApiImpl.by(" RestAPI key ops "));
    }
}
