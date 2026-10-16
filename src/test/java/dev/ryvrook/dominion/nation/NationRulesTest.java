package dev.ryvrook.dominion.nation;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.FactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NationRulesTest {
    Fixtures fx;
    FactionServiceImpl fs;
    NationServiceImpl nations;
    Faction a;
    Faction b;
    Faction c;

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fs = new FactionServiceImpl(fx.ctx);
        fx.ctx.register(FactionService.class, fs);
        nations = new NationServiceImpl(fx.ctx);
        a = fx.faction("Alpha");
        b = fx.faction("Bravo");
        c = fx.faction("Charlie");
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    static void ok(Result r) {
        assertTrue(r.success(), r.message());
    }

    static void fails(Result r, String fragment) {
        assertTrue(r.failed(), "expected a failure but got " + r.message());
        assertTrue(r.message().contains(fragment), r.message());
    }

    Nation founded() {
        ok(nations.create(a.leader(), "NAT", "The Nation"));
        return nations.byTag("nat");
    }

    void bring(Faction f) {
        ok(nations.invite(a.leader(), f.tag()));
        ok(nations.join(f.leader(), "NAT"));
    }

    @Test
    void foundingRules() {
        fails(nations.create(fx.member(a, "Off", Rank.OFFICER), "NAT", null), "Only a faction leader");
        fails(nations.create(a.leader(), "N", null), "at least 2");
        fails(nations.create(a.leader(), "NATIO", null), "at most 4");
        fails(nations.create(a.leader(), "N-T", null), "letters and digits");
        fails(nations.create(a.leader(), "NAT", "x".repeat(33)), "at most 32");
        Nation n = founded();
        assertEquals(a.id(), n.leaderFaction());
        assertEquals(n.id(), a.nationId());
        fails(nations.create(a.leader(), "OTH", null), "already in a nation");
        fails(nations.create(b.leader(), "nat", null), "already exists");
        fx.set("nation.enabled", false);
        fails(nations.create(c.leader(), "CEE", null), "turned off");
    }

    @Test
    void invitesAndJoining() {
        Nation n = founded();
        bring(b);
        fails(nations.invite(b.leader(), "Charlie"), "leader of the leading faction");
        fails(nations.invite(a.leader(), "Nobody"), "No faction has that tag");
        fails(nations.invite(a.leader(), "Bravo"), "already in your nation");
        fails(nations.join(c.leader(), "NAT"), "has not invited");
        ok(nations.invite(a.leader(), "Charlie"));
        fails(nations.invite(a.leader(), "Charlie"), "already has an invite");
        fails(nations.join(fx.member(c, "Off", Rank.OFFICER), "NAT"), "Only a faction leader");
        fails(nations.join(c.leader(), "NOPE"), "No nation has that tag");
        ok(nations.join(c.leader(), "nat"));
        assertTrue(nations.sameNation(b, c));
        assertFalse(nations.sameNation(b, b));
        assertEquals(3, n.members().size());
        Faction zone = fx.faction("Safe");
        zone.setType(FactionType.SAFEZONE);
        fails(nations.invite(a.leader(), "Safe"), "No faction has that tag");
    }

    @Test
    void sizeLimitCoversInvitesAndJoins() {
        fx.set("nation.maxFactions", 2);
        founded();
        ok(nations.invite(a.leader(), "Bravo"));
        ok(nations.invite(a.leader(), "Charlie"));
        ok(nations.join(b.leader(), "NAT"));
        fails(nations.join(c.leader(), "NAT"), "most factions allowed");
        fails(nations.invite(a.leader(), "Delta"), "No faction");
        fx.faction("Delta");
        fails(nations.invite(a.leader(), "Delta"), "most factions allowed");
        fx.set("nation.maxFactions", 3);
        ok(nations.join(c.leader(), "NAT"));
    }

    @Test
    void factionsInAnotherNationCannotBeInvited() {
        founded();
        ok(nations.create(b.leader(), "TWO", null));
        fails(nations.invite(a.leader(), "Bravo"), "already in another nation");
        ok(nations.invite(a.leader(), "Charlie"));
        ok(nations.invite(b.leader(), "Charlie"));
        ok(nations.join(c.leader(), "TWO"));
        fails(nations.join(c.leader(), "NAT"), "already in a nation");
    }

    @Test
    void leavingAndTheLeadingFaction() {
        Nation n = founded();
        bring(b);
        fails(nations.leave(a.leader()), "Hand the nation to another faction first");
        fails(nations.leave(c.leader()), "not in a nation");
        ok(nations.leave(b.leader()));
        assertNull(b.nationId());
        ok(nations.leave(a.leader()));
        assertNull(nations.byTag("NAT"), "the last faction out removes the nation");
        assertNull(a.nationId());
        assertTrue(nations.all().isEmpty());
        assertFalse(n.isMember(a.id()));
    }

    @Test
    void kickAndHandOver() {
        Nation n = founded();
        bring(b);
        ok(nations.invite(a.leader(), "Charlie"));
        fails(nations.kick(a.leader(), "Alpha"), "cannot remove your own faction");
        Result w = nations.kick(a.leader(), "Charlie");
        ok(w);
        assertEquals("Withdrew the invite for Charlie", w.message());
        fails(nations.kick(a.leader(), "Charlie"), "not in your nation");
        fails(nations.kick(b.leader(), "Alpha"), "leader of the leading faction");
        fails(nations.setLeaderFaction(a.leader(), "Charlie"), "not in your nation");
        fails(nations.setLeaderFaction(a.leader(), "Alpha"), "already leads");
        ok(nations.setLeaderFaction(a.leader(), "Bravo"));
        assertEquals(b.id(), n.leaderFaction());
        ok(nations.kick(b.leader(), "Alpha"));
        assertNull(a.nationId());
        assertEquals(1, n.members().size());
    }

    @Test
    void disbandNeedsConfirm() {
        founded();
        bring(b);
        fails(nations.disband(b.leader(), true), "leader of the leading faction");
        fails(nations.disband(a.leader(), false), "confirm");
        ok(nations.disband(a.leader(), true));
        assertNull(a.nationId());
        assertNull(b.nationId());
        assertNull(nations.byTag("NAT"));
    }

    @Test
    void aDisbandedLeadingFactionPassesTheNationOn() {
        Nation n = founded();
        bring(b);
        ok(nations.invite(a.leader(), "Charlie"));
        nations.onEvent(new DominionEvent.FactionDisbanded(a.id(), a.tag()));
        assertEquals(b.id(), n.leaderFaction());
        assertFalse(n.isMember(a.id()));
        nations.onEvent(new DominionEvent.FactionDisbanded(b.id(), b.tag()));
        assertNull(nations.byTag("NAT"));
    }

    @Test
    void reconcileRepairsLinksBothWays() {
        Nation n = founded();
        bring(b);
        UUID ghost = UUID.randomUUID();
        n.addMember(ghost);
        c.setNationId(UUID.randomUUID());
        b.setNationId(null);
        nations.reconcile();
        assertFalse(n.isMember(ghost), "a missing faction leaves the member list");
        assertNull(c.nationId(), "a link to a missing nation is cleared");
        assertEquals(n.id(), b.nationId(), "a listed member gets its link back");
    }
}
