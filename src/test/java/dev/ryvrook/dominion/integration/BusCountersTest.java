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
        assertEquals(List.of("Ann dominion:factions_founded 1", "Ann dominion:joined 1"), counted,
                "founding publishes a join for the founder");
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
        counted.clear();

        // Claiming the same chunk back and releasing it again within the mission window counts nothing.
        ok(claims.claim(ann, W, Keys.chunk(1, 0)));
        ok(claims.unclaim(ann, W, Keys.chunk(1, 0)));
        ok(claims.claim(ann, W, Keys.chunk(1, 0)));
        assertEquals(List.of(), counted, "reclaiming released land is not a new claim or a new release");

        ok(claims.claim(ann, W, Keys.chunk(2, 0)));
        assertEquals(List.of("Ann dominion:claims 1"), counted, "fresh land still counts");
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

    @Test
    void reinvitingTheSamePlayerCountsOnce() {
        Faction a = fx.faction("Alpha");
        ContextFacts facts = new ContextFacts(fx.ctx);
        UUID target = UUID.randomUUID();
        var invite = new dev.ryvrook.dominion.api.DominionEvent.MemberInvited(a.id(), a.leader(), target);
        assertEquals(1, StatMapper.credits(invite, facts).size());
        assertTrue(StatMapper.credits(invite, facts).isEmpty(), "revoke and invite again is not a new invite");
        assertEquals(1, StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.MemberInvited(a.id(), a.leader(),
                UUID.randomUUID()), facts).size());
    }

    @Test
    void bankDepositsCountTheNetAmountOfTheDay() {
        Faction a = fx.faction("Alpha");
        UUID p = a.leader();
        ContextFacts facts = new ContextFacts(fx.ctx);
        var deposit = new dev.ryvrook.dominion.api.DominionEvent.BankDeposited(a.id(), p, 100);
        var withdraw = new dev.ryvrook.dominion.api.DominionEvent.BankWithdrawn(a.id(), p, 100);
        assertEquals(List.of(new StatMapper.Credit(p, StatKeys.BANK_DEPOSITED, 100)), StatMapper.credits(deposit, facts));
        for (int round = 0; round < 5; round++) {
            assertTrue(StatMapper.credits(withdraw, facts).isEmpty());
            assertTrue(StatMapper.credits(deposit, facts).isEmpty(), "a deposit and withdraw loop credits nothing more");
        }
        assertEquals(List.of(new StatMapper.Credit(p, StatKeys.BANK_DEPOSITED, 50)),
                StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.BankDeposited(a.id(), p, 50.5), facts));
        StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.BankWithdrawn(a.id(), p, 1000), facts);
        assertEquals(List.of(new StatMapper.Credit(p, StatKeys.BANK_DEPOSITED, 1)),
                StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.BankDeposited(a.id(), p, 151.5), facts),
                "the net never goes below zero, so only coins past what was credited count");
    }

    @Test
    void upgradeLevelFollowsThePlayerAcrossFactions() {
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Beta");
        UUID p = a.leader();
        ContextFacts facts = new ContextFacts(fx.ctx);
        assertEquals(2, facts.upgradeLevelCredit(p, 2));
        assertEquals(0, facts.upgradeLevelCredit(p, 2), "the same level again");
        assertEquals(1, facts.upgradeLevelCredit(p, 3));
        assertEquals(0, facts.upgradeLevelCredit(p, 1), "a lower level of another upgrade");
        assertEquals(0, facts.upgradeLevelCredit(b.leader(), 0));
        assertEquals(3, fx.ctx.player(p).counter(ContextFacts.UPGRADE_LEVEL), "remembered on the player, not the faction");
        var bought = new dev.ryvrook.dominion.api.DominionEvent.UpgradeBought(a.id(), "maxpower", 4);
        assertEquals(List.of(new StatMapper.Credit(p, StatKeys.UPGRADES, 1), new StatMapper.Credit(p, StatKeys.UPGRADE_LEVEL_MAX, 1)),
                StatMapper.credits(bought, facts));
    }

    @Test
    void foreignEntriesSkipSystemFactionsAndStopAtTheCap() {
        Faction a = fx.faction("Alpha");
        Faction safe = fx.faction("Safe");
        safe.setType(dev.ryvrook.dominion.model.FactionType.SAFEZONE);
        UUID p = fx.player("Ann");
        ContextFacts facts = new ContextFacts(fx.ctx);
        assertTrue(facts.firstVisit(p, a.id()));
        assertFalse(facts.firstVisit(p, a.id()), "the same faction again");
        assertFalse(facts.firstVisit(p, safe.id()), "the safe zone is not a foreign faction");
        assertFalse(facts.firstVisit(p, UUID.randomUUID()), "a faction that no longer exists");
        assertFalse(facts.firstVisit(null, a.id()));
        for (int i = 1; i < ContextFacts.FOREIGN_CAP; i++) {
            assertTrue(facts.firstVisit(p, fx.faction("F" + i).id()), "faction " + i);
        }
        assertFalse(facts.firstVisit(p, fx.faction("Late").id()), "the memory is full");
        assertEquals(ContextFacts.FOREIGN_CAP, fx.ctx.player(p).counterCount(ContextFacts.FOREIGN));
        var enter = new dev.ryvrook.dominion.api.DominionEvent.PlayerEnteredTerritory(a.leader(), W, null, safe.id());
        assertTrue(StatMapper.credits(enter, facts).isEmpty());
    }

    @Test
    void powerMaxReadsTheServiceAndCreditsOnce() {
        Faction a = fx.faction("Alpha");
        UUID p = a.leader();
        ContextFacts facts = new ContextFacts(fx.ctx);
        double max = fx.ctx.service(PowerService.class).maxPowerOf(p);
        assertEquals(max, facts.maxPower(p));
        assertTrue(StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.PowerChanged(p, max - 1), facts).isEmpty());
        assertEquals(List.of(new StatMapper.Credit(p, StatKeys.POWER_MAX, 1)),
                StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.PowerChanged(p, max), facts));
        assertTrue(StatMapper.credits(new dev.ryvrook.dominion.api.DominionEvent.PowerChanged(p, max), facts).isEmpty(), "once");
        assertEquals(1, fx.ctx.player(p).counter(ContextFacts.POWER_MAX));
    }

    @Test
    void aBreachIsOneEventPerChunkButOneCredit() {
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Beta");
        ContextFacts facts = new ContextFacts(fx.ctx);
        var first = new dev.ryvrook.dominion.api.DominionEvent.BreachChanged(W, 1L, a.id(), b.id(), true);
        var second = new dev.ryvrook.dominion.api.DominionEvent.BreachChanged(W, 2L, a.id(), b.id(), true);
        assertEquals(List.of(new StatMapper.Credit(a.leader(), StatKeys.BREACHES, 1)), StatMapper.credits(first, facts));
        assertTrue(StatMapper.credits(second, facts).isEmpty(), "the neighbour chunk of the same breach");
        assertTrue(facts.newBreach(b.id(), a.id()), "the other direction is its own breach");
    }
}
