package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.power.PowerServiceImpl;
import dev.ryvrook.dominion.relation.RelationServiceImpl;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.ryvrook.dominion.service.ProgressService;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimRulesTest {
    static final String W = "world";

    Fixtures fx;
    ClaimServiceImpl claims;
    PowerService power;
    final List<DominionEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        power = new PowerServiceImpl(fx.ctx);
        fx.ctx.register(PowerService.class, power);
        fx.ctx.register(RelationService.class, new RelationServiceImpl(fx.ctx));
        claims = new ClaimServiceImpl(fx.ctx);
        fx.ctx.bus().subscribe(events::add);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    static long c(int x, int z) {
        return Keys.chunk(x, z);
    }

    UUID lead(Faction f) {
        return f.leader();
    }

    void ok(Result r) {
        assertTrue(r.success(), r.message());
    }

    void fails(Result r, String fragment) {
        assertTrue(r.failed(), "expected a failure but got " + r.message());
        assertTrue(r.message().contains(fragment), r.message());
    }

    // ---- basic claiming -----------------------------------------------------

    @Test
    void firstClaimMayBeAnywhereThenClaimsMustTouchOnAnEdge() {
        Faction a = fx.faction("Alpha");
        ok(claims.claim(lead(a), W, c(10, 10)));
        fails(claims.claim(lead(a), W, c(20, 20)), "must touch");
        fails(claims.claim(lead(a), W, c(11, 11)), "must touch");
        ok(claims.claim(lead(a), W, c(11, 10)));
        assertEquals(2, claims.claimCount(a));
        fails(claims.claim(lead(a), W, c(11, 10)), "already owns");
        // A first claim in another world is free to go anywhere.
        ok(claims.claim(lead(a), "other", c(-50, 3)));
    }

    @Test
    void connectionCanBeTurnedOff() {
        fx.set("land.mustBeConnected", false);
        Faction a = fx.faction("Alpha");
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(a), W, c(9, 9)));
    }

    @Test
    void claimerNeedsAFactionAndTheRankAndNoHold() {
        UUID loner = fx.player("Loner");
        fails(claims.claim(loner, W, c(0, 0)), "need to be in a faction");
        Faction a = fx.faction("Alpha");
        UUID recruit = fx.member(a, "Rec", Rank.RECRUIT);
        fails(claims.claim(recruit, W, c(0, 0)), "rank cannot claim");
        UUID officer = fx.member(a, "Off", Rank.OFFICER);
        Member m = a.member(officer);
        m.setHoldUntil(System.currentTimeMillis() + 60_000L);
        fails(claims.claim(officer, W, c(0, 0)), "rank cannot claim");
        m.setHoldUntil(0);
        ok(claims.claim(officer, W, c(0, 0)));
        fails(claims.claim(lead(a), null, c(1, 0)), "position is not known");
    }

    @Test
    void allowedWorldListIgnoresCaseAndBlanks() {
        fx.set("land.allowedWorlds", " world , , Other");
        Faction a = fx.faction("Alpha");
        ok(claims.claim(lead(a), "OTHER", c(0, 0)));
        fails(claims.claim(lead(a), "nether", c(0, 0)), "cannot be claimed in this world");
        fx.set("land.allowedWorlds", " , ");
        ok(claims.claim(lead(a), "nether", c(0, 0)));
    }

    @Test
    void serverZonesCannotBeClaimed() {
        Faction a = fx.faction("Alpha");
        ok(claims.createZone(null, FactionType.SAFEZONE, "Spawn"));
        Faction zone = fx.repo().factionByTag("Spawn");
        ok(claims.adminClaim(null, zone, W, c(0, 0)));
        fails(claims.claim(lead(a), W, c(0, 0)), "belongs to the server");
        assertEquals(Integer.MAX_VALUE, claims.maxClaims(zone));
    }

    // ---- buffer --------------------------------------------------------------

    @Test
    void bufferKeepsAGapIncludingDiagonals() {
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Bravo");
        ok(claims.claim(lead(b), W, c(3, 0)));
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(a), W, c(1, 0)));
        fails(claims.claim(lead(a), W, c(2, 0)), "too close to Bravo");
        ok(claims.claim(lead(a), W, c(1, 1)));
        fails(claims.claim(lead(a), W, c(2, 1)), "too close to Bravo");
        fx.set("land.bufferChunks", 0);
        ok(claims.claim(lead(a), W, c(2, 0)));
    }

    @Test
    void bufferIgnoresServerZones() {
        Faction a = fx.faction("Alpha");
        ok(claims.createZone(null, FactionType.WARZONE, "War"));
        ok(claims.adminClaim(null, fx.repo().factionByTag("War"), W, c(1, 0)));
        ok(claims.claim(lead(a), W, c(0, 0)));
    }

    // ---- power limit -------------------------------------------------------

    @Test
    void claimsFollowPowerRoundedDown() {
        fx.set("land.bufferChunks", 0);
        Faction a = fx.faction("Alpha");
        power.setPower(lead(a), 2.9);
        assertEquals(2, claims.maxClaims(a));
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(a), W, c(1, 0)));
        fails(claims.claim(lead(a), W, c(2, 0)), "holds 2 of 2 claims");
        a.addStat("bonusClaims", 1);
        ok(claims.claim(lead(a), W, c(2, 0)));
        power.setPower(lead(a), -5);
        assertEquals(1, claims.maxClaims(a), "negative power gives no base claims, bonus claims stay");
    }

    @Test
    void deathsPoolModeCountsClaimsFromFullPower() {
        fx.set("power.dtrMode", true);
        Faction a = fx.faction("Alpha");
        fx.member(a, "Two", Rank.MEMBER);
        power.setPower(lead(a), -5);
        assertEquals(40, claims.maxClaims(a), "two members at the 20 power maximum");
    }

    // ---- costs and refunds ---------------------------------------------------

    @Test
    void claimCostsGrowAndRefundsReturnTheLastPrice() {
        FakeEconomy eco = new FakeEconomy();
        fx.ctx.register(EconomyService.class, eco);
        fx.set("eco.costClaim", 10.0).set("eco.costClaimGrowthPercent", 30.0).set("eco.refundUnclaimPercent", 70.0);
        Faction a = fx.faction("Alpha");
        a.setBank(100);
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(a), W, c(1, 0)));
        Result third = claims.claim(lead(a), W, c(2, 0));
        ok(third);
        assertTrue(third.message().contains("16.00c"), third.message());
        assertEquals(100 - 10 - 13 - 16, a.bank(), 1e-9);
        Result back = claims.unclaim(lead(a), W, c(2, 0));
        ok(back);
        assertEquals(61 + 11.2, a.bank(), 1e-9);
        assertTrue(back.message().contains("11.20c"), back.message());
        a.setBank(5);
        fails(claims.claim(lead(a), W, c(2, 0)), "costs 16.00c");
        assertEquals(2, claims.claimCount(a), "a refused payment claims nothing");
    }

    // ---- radius and line -------------------------------------------------------

    @Test
    void radiusLimitsAndPartialResults() {
        fx.set("land.maxRadiusClaim", 2);
        Faction a = fx.faction("Alpha");
        fails(claims.claimRadius(lead(a), W, c(0, 0), -1), "cannot be negative");
        fails(claims.claimRadius(lead(a), W, c(0, 0), 3), "largest radius you can claim is 2");
        ok(claims.claimRadius(lead(a), W, c(0, 0), 0));
        assertEquals(1, claims.claimCount(a));
        Result nine = claims.claimRadius(lead(a), W, c(0, 0), 1);
        ok(nine);
        assertEquals("Claimed 8 of 8 chunks.", nine.message());
        fails(claims.claimRadius(lead(a), W, c(0, 0), 1), "already owns all of that land");
        Result big = claims.claimRadius(lead(a), W, c(0, 0), 2);
        ok(big);
        assertTrue(big.message().startsWith("Claimed 1 of 16 chunks."), big.message());
        assertTrue(big.message().contains("no claim power left"), big.message());
        assertEquals(10, claims.claimCount(a));
    }

    @Test
    void lineLimitsAndDirection() {
        fx.set("land.maxLineClaim", 4);
        Faction a = fx.faction("Alpha");
        fails(claims.claimLine(lead(a), W, c(0, 0), 1, 0, 0), "at least one chunk");
        fails(claims.claimLine(lead(a), W, c(0, 0), 1, 0, 5), "longest line you can claim is 4");
        fails(claims.claimLine(lead(a), W, c(0, 0), 0, 0, 2), "Pick a direction");
        ok(claims.claimLine(lead(a), W, c(0, 0), 5, 0, 4));
        for (int x = 0; x < 4; x++) {
            assertEquals(a.id(), claims.ownerAt(W, c(x, 0)).id());
        }
        assertNull(claims.ownerAt(W, c(4, 0)));
        ok(claims.claimLine(lead(a), W, c(0, -1), 0, -3, 2));
        assertNotNull(claims.ownerAt(W, c(0, -2)));
    }

    // ---- unclaiming ------------------------------------------------------------

    @Test
    void unclaimRules() {
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Bravo");
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(b), W, c(5, 0)));
        fails(claims.unclaim(lead(a), W, c(5, 0)), "does not own");
        UUID member = fx.member(a, "Mem", Rank.MEMBER);
        fails(claims.unclaim(member, W, c(0, 0)), "rank cannot unclaim");
        a.setHome(new Location(W, 3, 64, 3, 0, 0));
        ok(claims.unclaim(lead(a), W, c(0, 0)));
        assertNull(a.home(), "the home goes with its chunk");
        fails(claims.unclaimRadius(lead(a), W, c(0, 0), 1), "does not own any land");
        fails(claims.unclaimRadius(lead(a), W, c(0, 0), -1), "cannot be negative");
    }

    @Test
    void unclaimAllNeedsConfirmAndTheDisbandPermission() {
        Faction a = fx.faction("Alpha");
        fails(claims.unclaimAll(lead(a), true), "does not own any land");
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(a), "other", c(0, 0)));
        UUID officer = fx.member(a, "Off", Rank.OFFICER);
        fails(claims.unclaimAll(officer, true), "cannot release all");
        fails(claims.unclaimAll(lead(a), false), "releases all 2 chunks");
        ok(claims.unclaimAll(lead(a), true));
        assertEquals(0, claims.claimCount(a));
    }

    // ---- admin claims and zones ------------------------------------------------

    @Test
    void quietAdminClaimSkipsEventsForFreeLandOnly() {
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Bravo");
        events.clear();
        ok(claims.adminClaim(null, a, W, c(0, 0), true));
        assertTrue(events.stream().noneMatch(e -> e instanceof DominionEvent.ClaimChanged));
        ok(claims.adminClaim(null, b, W, c(0, 0), true));
        assertTrue(events.stream().anyMatch(e -> e instanceof DominionEvent.ClaimChanged cc && a.id().equals(cc.oldFaction())),
                "taking land from a faction is still announced");
        fails(claims.adminClaim(null, b, W, c(0, 0)), "already owns");
        fails(claims.adminClaim(null, null, W, c(0, 0)), "does not exist");
        fails(claims.adminUnclaim(null, W, c(9, 9)), "Nobody owns");
        ok(claims.adminUnclaim(null, W, c(0, 0)));
        assertEquals(0, claims.claimCount(b));
    }

    @Test
    void zoneCreationChecks() {
        fails(claims.createZone(null, FactionType.NORMAL, "X"), "SafeZone or a WarZone");
        fails(claims.createZone(null, FactionType.SAFEZONE, "Two words"), "no spaces");
        fx.faction("Alpha");
        fails(claims.createZone(null, FactionType.SAFEZONE, "alpha"), "already exists");
        Result r = claims.createZone(null, FactionType.WARZONE, "Arena");
        ok(r);
        assertEquals("Created the WarZone Arena.", r.message());
        assertTrue(fx.repo().factionByTag("Arena").isSystem());
    }

    // ---- overclaiming ----------------------------------------------------------

    /** Bravo holds a plus shape around (10, 0) with power for two claims, so it is overextended. */
    Faction overextendedBravo() {
        fx.set("land.bufferChunks", 0).set("raid.minDefendersOnline", 0);
        Faction b = fx.faction("Bravo");
        for (long chunk : new long[] { c(10, 0), c(11, 0), c(9, 0), c(10, 1), c(10, -1) }) {
            ok(claims.claim(lead(b), W, chunk));
        }
        power.setPower(lead(b), 2);
        return b;
    }

    @Test
    void enemiesTakeEdgeChunksOfOverextendedLand() {
        Faction b = overextendedBravo();
        Faction a = fx.faction("Alpha");
        ok(claims.claim(lead(a), W, c(12, 0)));
        assertTrue(claims.raidable(b));
        fails(claims.claim(lead(a), W, c(11, 0)), "Only enemy land can be taken");
        a.setRelation(b.id(), Relation.ENEMY);
        fails(claims.claim(lead(a), W, c(10, 0)), "must touch");
        Result took = claims.claim(lead(a), W, c(11, 0));
        ok(took);
        assertTrue(took.message().startsWith("Took chunk"), took.message());
        assertEquals(a.id(), claims.ownerAt(W, c(11, 0)).id());
        assertEquals(4, claims.claimCount(b));
    }

    @Test
    void interiorChunksCannotBeTaken() {
        Faction b = overextendedBravo();
        Faction a = fx.faction("Alpha");
        a.setRelation(b.id(), Relation.ENEMY);
        fx.set("land.mustBeConnected", false);
        fails(claims.claim(lead(a), W, c(10, 0)), "edge of Bravo land");
    }

    @Test
    void homeChunkFallsLast() {
        Faction b = overextendedBravo();
        b.setHome(new Location(W, 11 * Keys.chunkSize() + 1, 64, 1, 0, 0));
        Faction a = fx.faction("Alpha");
        a.setRelation(b.id(), Relation.ENEMY);
        ok(claims.claim(lead(a), W, c(12, 0)));
        fails(claims.claim(lead(a), W, c(11, 0)), "home chunk of Bravo can only be taken last");
        fx.set("raid.protectHomeChunkUntilLast", false);
        ok(claims.claim(lead(a), W, c(11, 0)));
        assertNull(b.home(), "a taken home chunk clears the home");
    }

    @Test
    void protectedFactionsCannotBeTaken() {
        Faction b = overextendedBravo();
        Faction a = fx.faction("Alpha");
        a.setRelation(b.id(), Relation.ENEMY);
        ok(claims.claim(lead(a), W, c(12, 0)));
        fx.set("raid.minDefendersOnline", 1);
        fails(claims.claim(lead(a), W, c(11, 0)), "needs 1 members online");
        claims.land().online = id -> id.equals(lead(b));
        fx.set("grace.active", true).set("grace.endsAt", 0L);
        fails(claims.claim(lead(a), W, c(11, 0)), "grace period");
        fx.set("grace.endsAt", System.currentTimeMillis() - 1);
        ok(claims.claim(lead(a), W, c(11, 0)));
    }

    @Test
    void strongerEnemiesTakeLandByDominanceWithACooldown() {
        fx.set("land.bufferChunks", 0).set("raid.minDefendersOnline", 0).set("raid.modeOverextended", false);
        Faction b = fx.faction("Bravo");
        ok(claims.claim(lead(b), W, c(0, 0)));
        ok(claims.claim(lead(b), W, c(1, 0)));
        ok(claims.claim(lead(b), W, c(2, 0)));
        Faction a = fx.faction("Alpha");
        a.setRelation(b.id(), Relation.ENEMY);
        ok(claims.claim(lead(a), W, c(4, 0)));
        ok(claims.claim(lead(a), W, c(3, 0)));
        // Equal power at 10 each is not a lead.
        fails(claims.claim(lead(a), W, c(2, 0)), "10 percent more power");
        power.setPower(lead(a), 11.0);
        fails(claims.claim(lead(a), W, c(2, 0)), "10 percent more power");
        power.setPower(lead(a), 11.01);
        ok(claims.claim(lead(a), W, c(2, 0)));
        fails(claims.claim(lead(a), W, c(1, 0)), "again in");
        fx.set("raid.dominanceCooldownMinutes", 0);
        ok(claims.claim(lead(a), W, c(1, 0)));
    }

    @Test
    void dominanceMarginHandlesNegativeAndZeroPower() {
        assertFalse(Raids.dominates(100, 100, 10));
        assertFalse(Raids.dominates(110, 100, 10));
        assertTrue(Raids.dominates(110.01, 100, 10));
        assertFalse(Raids.dominates(0, 0, 10));
        assertTrue(Raids.dominates(0.01, 0, 10));
        // A defender at minus 10 needs the attacker above minus 9, not above minus 11.
        assertFalse(Raids.dominates(-10.5, -10, 10));
        assertFalse(Raids.dominates(-9, -10, 10));
        assertTrue(Raids.dominates(-8.9, -10, 10));
        assertTrue(Raids.dominates(1, 0.5, -50), "a negative margin counts as none");
    }

    @Test
    void raidableModesCombineByAnyOrAll() {
        fx.set("raid.minDefendersOnline", 0).set("raid.modeLowRatio", true).set("raid.lowRatioPercent", 25.0);
        Faction b = fx.faction("Bravo");
        power.setPower(lead(b), 4);
        assertTrue(claims.raidable(b), "a ratio of 20 percent is below 25 and any mode is enough");
        fx.set("raid.combineAny", false);
        assertFalse(claims.raidable(b), "all modes must hold and the land is not overextended");
        fx.set("raid.combineAny", true);
        power.setPower(lead(b), 5);
        assertFalse(claims.raidable(b), "exactly 25 percent is not below the threshold");
        fx.set("raid.modeOverextended", false).set("raid.modeLowRatio", false);
        power.setPower(lead(b), 0);
        assertFalse(claims.raidable(b), "no mode enabled means never raidable");
        fx.set("raid.modeLowRatio", true);
        assertTrue(claims.raidable(b));
        fx.set("raid.enabled", false);
        power.setPower(lead(b), 0);
        assertFalse(claims.raidable(b));
    }

    @Test
    void deathsPoolModeIsRaidableAtAnEmptyPool() {
        fx.set("power.dtrMode", true).set("raid.minDefendersOnline", 0);
        Faction b = fx.faction("Bravo");
        assertFalse(claims.raidable(b));
        for (int i = 0; i < 3; i++) {
            power.onDeath(lead(b), W, false);
        }
        assertEquals(0, power.factionPower(b), 1e-9);
        assertTrue(claims.raidable(b));
    }

    @Test
    void breachesExpireAndBelongToOneAttacker() {
        UUID attacker = UUID.randomUUID();
        long now = System.currentTimeMillis();
        claims.setBreached(W, c(0, 0), attacker, now + 60_000);
        assertTrue(claims.breached(W, c(0, 0), attacker));
        assertFalse(claims.breached(W, c(0, 0), UUID.randomUUID()));
        assertFalse(claims.breached(W, c(1, 0), attacker));
        assertFalse(claims.breached(null, c(0, 0), attacker));
        claims.setBreached(W, c(0, 0), attacker, now - 1);
        assertFalse(claims.breached(W, c(0, 0), attacker));
    }

    @Test
    void reclaimingReleasedLandEarnsNoMissionCredit() {
        List<String> credited = new ArrayList<>();
        ProgressService progress = (ProgressService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ProgressService.class }, (proxy, method, args) -> {
                    if (method.getName().equals("advance")) {
                        credited.add((String) args[1]);
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
        fx.ctx.register(ProgressService.class, progress);
        Faction a = fx.faction("Alpha");
        ok(claims.claim(lead(a), W, c(0, 0)));
        ok(claims.claim(lead(a), W, c(1, 0)));
        assertEquals(2, credited.size());
        for (int i = 0; i < 3; i++) {
            ok(claims.unclaim(lead(a), W, c(1, 0)));
            ok(claims.claim(lead(a), W, c(1, 0)));
        }
        assertEquals(2, credited.size(), "unclaim and reclaim of the same chunk counts once");
        ok(claims.claim(lead(a), W, c(2, 0)));
        assertEquals(3, credited.size(), "new land still counts");
        ok(claims.unclaimAll(lead(a), true));
        ok(claims.claimRadius(lead(a), W, c(0, 0), 1));
        assertEquals(10, credited.size(), "only the seven chunks never held before count");
        Faction b = fx.faction("Bravo");
        fx.set("land.mustBeConnected", false).set("land.bufferChunks", 0);
        ok(claims.claim(lead(b), W, c(2, 0)));
        assertEquals(11, credited.size(), "a chunk another faction released is new land for this one");
    }

    @Test
    void tradedOrTakenLandEarnsCreditOncePerFactionAcrossRestarts() {
        List<String> credited = new ArrayList<>();
        ProgressService progress = (ProgressService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ProgressService.class }, (proxy, method, args) -> {
                    if (method.getName().equals("advance")) {
                        credited.add((String) args[1]);
                    }
                    return null;
                });
        fx.ctx.register(ProgressService.class, progress);
        fx.set("land.mustBeConnected", false).set("land.bufferChunks", 0);
        Faction a = fx.faction("Alpha");
        Faction b = fx.faction("Bravo");
        long x = c(0, 0);
        ok(claims.claim(lead(a), W, x));
        ok(claims.unclaim(lead(a), W, x));
        ok(claims.claim(lead(b), W, x));
        ok(claims.unclaim(lead(b), W, x));
        ok(claims.claim(lead(a), W, x));
        ok(claims.unclaim(lead(a), W, x));
        ok(claims.claim(lead(b), W, x));
        assertEquals(2, credited.size(), "two cooperating factions earn one credit each");

        // Overclaim, the loser is marked too.
        fx.set("raid.minDefendersOnline", 0);
        power.setPower(lead(b), -5);
        a.setRelation(b.id(), Relation.ENEMY);
        ok(claims.claim(lead(a), W, x));
        assertTrue(b.releasedWithin(W, x, System.currentTimeMillis(), ClaimServiceImpl.MISSION_WINDOW_MILLIS));
        assertEquals(2, credited.size(), "Alpha held this chunk within the window");

        // A restart keeps the marks because they live in the faction file.
        ok(claims.unclaim(lead(a), W, x));
        Faction reloaded = Faction.fromDocument(a.toDocument());
        assertTrue(reloaded.releasedWithin(W, x, System.currentTimeMillis(), ClaimServiceImpl.MISSION_WINDOW_MILLIS));
        ClaimServiceImpl restarted = new ClaimServiceImpl(fx.ctx);
        ok(restarted.claim(lead(a), W, x));
        assertEquals(2, credited.size());
    }

    @Test
    void leavingTurnsAutoClaimOff() {
        Faction a = fx.faction("Alpha");
        UUID m = fx.member(a, "Mem", Rank.MEMBER);
        fx.ctx.player(m).setAutoClaim(true);
        claims.onEvent(new DominionEvent.MemberLeft(a.id(), m, "leave"));
        assertFalse(fx.ctx.player(m).autoClaim());
    }
}
