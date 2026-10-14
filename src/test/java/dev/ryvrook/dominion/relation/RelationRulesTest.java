package dev.ryvrook.dominion.relation;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.nation.NationServiceImpl;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RelationRulesTest {
    Fixtures fx;
    RelationServiceImpl rel;
    Faction a;
    Faction b;
    Faction c;
    final List<DominionEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        rel = new RelationServiceImpl(fx.ctx);
        a = fx.faction("Alpha");
        b = fx.faction("Bravo");
        c = fx.faction("Charlie");
        fx.ctx.bus().subscribe(events::add);
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

    void ally(Faction x, Faction y) {
        ok(rel.request(x.leader(), y.tag(), Relation.ALLY));
        ok(rel.accept(y.leader(), x.tag()));
    }

    @Test
    void allianceNeedsARequestAndAnAnswer() {
        Result asked = rel.request(a.leader(), "bravo", Relation.ALLY);
        ok(asked);
        assertEquals("Asked Bravo. The request lasts 48 hours", asked.message());
        assertEquals(Relation.NEUTRAL, rel.between(a, b), "nothing changes until the answer");
        assertTrue(b.relationRequests().containsKey(a.id()), "the request is stored on the receiver");
        fails(rel.request(a.leader(), "Bravo", Relation.ALLY), "already asked");
        fails(rel.accept(a.leader(), "Bravo"), "has not asked your faction");
        ok(rel.accept(b.leader(), "Alpha"));
        assertEquals(Relation.ALLY, rel.between(a, b));
        assertEquals(Relation.ALLY, rel.between(b, a));
        assertTrue(b.relationRequests().isEmpty());
        assertTrue(events.stream().anyMatch(e -> e instanceof DominionEvent.RelationChanged rc && rc.relation().equals("ALLY")));
        fails(rel.request(a.leader(), "Bravo", Relation.ALLY), "already allied");
    }

    @Test
    void crossingRequestsSettleAtOnce() {
        ok(rel.request(a.leader(), "Bravo", Relation.TRUCE));
        Result r = rel.request(b.leader(), "Alpha", Relation.TRUCE);
        ok(r);
        assertEquals("You are now in a truce with Alpha", r.message());
        assertEquals(Relation.TRUCE, rel.between(a, b));
    }

    @Test
    void aRequestOfAnotherKindReplacesTheOldOne() {
        ok(rel.request(a.leader(), "Bravo", Relation.TRUCE));
        ok(rel.request(a.leader(), "Bravo", Relation.ALLY));
        assertEquals(Relation.ALLY, b.relationRequests().get(a.id()).type());
        ok(rel.request(b.leader(), "Alpha", Relation.TRUCE));
        assertEquals(Relation.TRUCE, a.relationRequests().get(b.id()).type(), "a different kind does not settle");
    }

    @Test
    void allyLimitsApplyToBothSides() {
        fx.set("relation.maxAllies", 1);
        ally(a, b);
        fails(rel.request(a.leader(), "Charlie", Relation.ALLY), "Your faction already has the maximum of 1 ally");
        ok(rel.request(c.leader(), "Alpha", Relation.ALLY));
        fails(rel.accept(a.leader(), "Charlie"), "Your faction already has the maximum of 1 ally");
        fails(rel.accept(fx.member(a, "Off", Rank.OFFICER), "Charlie"), "maximum");
        Faction d = fx.faction("Delta");
        ok(rel.request(d.leader(), "Charlie", Relation.ALLY));
        fx.set("relation.maxAllies", 0);
        fails(rel.accept(c.leader(), "Delta"), "maximum of 0 allies");
        fx.set("relation.maxAllies", 2);
        ok(rel.accept(c.leader(), "Delta"));
        ok(rel.accept(a.leader(), "Charlie"));
        assertEquals(2, a.countRelation(Relation.ALLY));
    }

    @Test
    void theOtherSideLimitIsNamed() {
        fx.set("relation.maxTruces", 1);
        ok(rel.request(c.leader(), "Bravo", Relation.TRUCE));
        ok(rel.accept(b.leader(), "Charlie"));
        ok(rel.request(a.leader(), "Bravo", Relation.TRUCE));
        fails(rel.accept(b.leader(), "Alpha"), "Your faction already has the maximum of 1 truce");
        ok(rel.request(b.leader(), "Alpha", Relation.ALLY));
        ok(rel.accept(a.leader(), "Bravo"));
        assertEquals(Relation.ALLY, rel.between(a, b), "an ally slot is separate from the truce slot");
    }

    @Test
    void switchingFromAllyToTruceFreesTheAllySlot() {
        fx.set("relation.maxAllies", 1);
        ally(a, b);
        ok(rel.request(a.leader(), "Bravo", Relation.TRUCE));
        ok(rel.accept(b.leader(), "Alpha"));
        assertEquals(Relation.TRUCE, rel.between(a, b));
        assertEquals(0, a.countRelation(Relation.ALLY));
        ally(a, c);
    }

    @Test
    void enemyIsUnilateralAndBreaksFriendship() {
        ally(a, b);
        ok(rel.request(c.leader(), "Bravo", Relation.TRUCE));
        ok(rel.request(b.leader(), "Alpha", Relation.ENEMY));
        assertEquals(Relation.ENEMY, rel.between(a, b));
        assertEquals(Relation.ENEMY, rel.between(b, a));
        assertEquals(Relation.NEUTRAL, a.relationTo(b.id()), "the ally view on the other side drops to neutral");
        fails(rel.request(b.leader(), "Alpha", Relation.ENEMY), "already enemies");
        ok(rel.request(b.leader(), "Alpha", Relation.NEUTRAL));
        assertEquals(Relation.NEUTRAL, rel.between(a, b));
    }

    @Test
    void oneSidedEnemyStillCountsForBoth() {
        ok(rel.request(a.leader(), "Bravo", Relation.ENEMY));
        ok(rel.request(b.leader(), "Alpha", Relation.ENEMY));
        ok(rel.request(a.leader(), "Bravo", Relation.NEUTRAL));
        assertEquals(Relation.ENEMY, rel.between(a, b), "Bravo still declares Alpha an enemy");
    }

    @Test
    void neutralWithdrawsOrEnds() {
        fails(rel.request(a.leader(), "Bravo", Relation.NEUTRAL), "already neutral");
        ok(rel.request(a.leader(), "Bravo", Relation.ALLY));
        Result w = rel.request(a.leader(), "Bravo", Relation.NEUTRAL);
        ok(w);
        assertEquals("Withdrew your request to Bravo", w.message());
        assertTrue(b.relationRequests().isEmpty());
        ally(a, b);
        ok(rel.request(b.leader(), "Alpha", Relation.NEUTRAL));
        assertEquals(Relation.NEUTRAL, a.relationTo(b.id()));
        assertEquals(Relation.NEUTRAL, b.relationTo(a.id()));
    }

    @Test
    void declineAndExpiry() {
        ok(rel.request(a.leader(), "Bravo", Relation.ALLY));
        ok(rel.decline(b.leader(), "Alpha"));
        fails(rel.decline(b.leader(), "Alpha"), "has not asked");
        b.putRelationRequest(a.id(), new Faction.RelationRequest(Relation.ALLY, a.id(), System.currentTimeMillis() - 1));
        fails(rel.accept(b.leader(), "Alpha"), "expired");
        assertTrue(b.relationRequests().isEmpty());
        b.putRelationRequest(a.id(), new Faction.RelationRequest(Relation.ALLY, a.id(), System.currentTimeMillis() - 1));
        c.putRelationRequest(UUID.randomUUID(), new Faction.RelationRequest(Relation.ALLY, UUID.randomUUID(), Long.MAX_VALUE));
        b.putRelationRequest(c.id(), new Faction.RelationRequest(Relation.TRUCE, c.id(), Long.MAX_VALUE));
        rel.tick();
        assertTrue(c.relationRequests().isEmpty(), "requests from factions that are gone are dropped");
        assertEquals(1, b.relationRequests().size(), "only the live request stays");
        fx.set("relation.requestHours", 1);
        Result r = rel.request(a.leader(), "Charlie", Relation.ALLY);
        assertTrue(r.message().endsWith("1 hour"), "the shortest request lasts an hour");
    }

    @Test
    void whoMayChangeRelations() {
        fails(rel.request(fx.player("Out"), "Bravo", Relation.ALLY), "not in a faction");
        UUID member = fx.member(a, "Mem", Rank.MEMBER);
        fails(rel.request(member, "Bravo", Relation.ALLY), "does not allow you to change relations");
        UUID officer = fx.member(a, "Off", Rank.OFFICER);
        ok(rel.request(officer, "Bravo", Relation.ALLY));
        fails(rel.request(a.leader(), "Nobody", Relation.ALLY), "No faction has that tag");
        fails(rel.request(a.leader(), "Alpha", Relation.ALLY), "your own faction");
        fails(rel.request(a.leader(), "Alpha", Relation.OWN), "not a relation you can set");
        Faction zone = fx.faction("Safe");
        zone.setType(FactionType.SAFEZONE);
        fails(rel.request(a.leader(), "Safe", Relation.ENEMY), "Server zones");
    }

    @Test
    void sharedNationMeansAlwaysAllies() {
        NationServiceImpl nations = new NationServiceImpl(fx.ctx);
        fx.ctx.register(NationService.class, nations);
        ok(rel.request(a.leader(), "Bravo", Relation.ENEMY));
        ok(nations.create(a.leader(), "NAT", "Nation"));
        ok(nations.invite(a.leader(), "Bravo"));
        ok(nations.join(b.leader(), "nat"));
        assertEquals(Relation.ALLY, rel.between(a, b));
        assertEquals(Relation.ALLY, rel.betweenPlayers(a.leader(), b.leader()));
        fails(rel.request(a.leader(), "Bravo", Relation.NEUTRAL), "share a nation");
        ok(nations.leave(b.leader()));
        assertEquals(Relation.ENEMY, rel.between(a, b), "the old relation returns after leaving");
    }

    @Test
    void playerViews() {
        UUID out = fx.player("Out");
        assertEquals(Relation.NEUTRAL, rel.betweenPlayers(out, a.leader()));
        assertEquals(Relation.OWN, rel.betweenPlayers(a.leader(), fx.member(a, "Mate", Rank.RECRUIT)));
        assertEquals(Relation.NEUTRAL, rel.playerToFaction(a.leader(), null));
        assertEquals(Relation.OWN, rel.playerToFaction(a.leader(), a));
    }
}
