package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.land.ClaimServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.power.PowerServiceImpl;
import dev.ryvrook.dominion.relation.RelationServiceImpl;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real faction, land and relation services against the counter fanout, so the events and the audit text
 * the mapper relies on are checked where they are made.
 */
class BusCountersTest {
    static final String W = "world";

    Fixtures fx;
    FactionServiceImpl fs;
    ClaimServiceImpl claims;
    final List<String> counted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fs = new FactionServiceImpl(fx.ctx);
        fx.ctx.register(FactionService.class, fs);
        fx.ctx.register(PowerService.class, new PowerServiceImpl(fx.ctx));
        fx.ctx.register(RelationService.class, new RelationServiceImpl(fx.ctx));
        claims = newClaims(fx);
        StatFanout fanout = new StatFanout(new ContextFacts(fx.ctx));
        fanout.add((p, k, n) -> counted.add(fx.ctx.player(p).name() + " " + k + " " + n));
        fx.ctx.bus().subscribe(fanout);
    }

    /** The land service constructor is package private, so it is opened here as the land tests would call it. */
    static ClaimServiceImpl newClaims(Fixtures fx) {
        try {
            var c = ClaimServiceImpl.class.getDeclaredConstructor(dev.ryvrook.dominion.DominionContext.class);
            c.setAccessible(true);
            return c.newInstance(fx.ctx);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    static void ok(Result r) {
        assertTrue(r.success(), r.message());
    }

    @Test
    void foundingInvitingClaimingAndReleasing() {
        UUID ann = fx.player("Ann");
        ok(fs.create(ann, "Ann", "Wolf", null));
        assertEquals(List.of("Ann dominion:factions_founded 1"), counted);
        counted.clear();

        UUID bo = fx.player("Bo");
        ok(fs.invite(ann, bo, "Bo"));
        assertEquals(List.of("Ann dominion:invites 1"), counted);
        counted.clear();
        ok(fs.invite(ann, bo, "Bo"));
        assertEquals(List.of(), counted, "renewing an open invite counts nothing");

        ok(claims.claim(ann, W, Keys.chunk(0, 0)));
        ok(claims.claim(ann, W, Keys.chunk(1, 0)));
        assertEquals(List.of("Ann dominion:claims 1", "Ann dominion:claims 1"), counted);
        counted.clear();

        ok(claims.unclaim(ann, W, Keys.chunk(1, 0)));
        assertEquals(List.of("Ann dominion:unclaims 1"), counted);
    }

    @Test
    void importedAndSystemFactionsAreNotFounded() {
        Faction old = new Faction(UUID.randomUUID(), "Old", "Old", fx.player("Gil"), 1L);
        fx.repo().addFaction(old);
        ContextFacts facts = new ContextFacts(fx.ctx);
        assertNull(facts.founder(old.id()));
        Faction fresh = fx.faction("Fresh");
        assertEquals(fresh.leader(), facts.founder(fresh.id()));
        fresh.setType(dev.ryvrook.dominion.model.FactionType.SAFEZONE);
        assertNull(facts.founder(fresh.id()));
        assertTrue(facts.members(fresh.id()).isEmpty());
    }

    @Test
    void warIsReadFromTheRelationService() {
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Beta");
        ContextFacts facts = new ContextFacts(fx.ctx);
        assertFalse(facts.atWar(a.id(), b.id()));
        a.setRelation(b.id(), Relation.ENEMY);
        assertTrue(facts.atWar(a.id(), b.id()));
        assertTrue(facts.atWar(b.id(), a.id()));
        assertFalse(facts.atWar(a.id(), null));
    }
}
