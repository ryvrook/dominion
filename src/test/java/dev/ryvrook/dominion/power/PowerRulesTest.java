package dev.ryvrook.dominion.power;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.land.Outage;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Rank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PowerRulesTest {
    Fixtures fx;
    PowerServiceImpl power;
    final Set<UUID> online = new HashSet<>();
    final List<DominionEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Outage.boot();
        fx = Fixtures.create();
        power = new PowerServiceImpl(fx.ctx);
        power.online = online::contains;
        fx.ctx.bus().subscribe(events::add);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void powerIsClampedBetweenMinimumAndMaximum() {
        UUID p = fx.player("Pat");
        assertEquals(10, power.powerOf(p));
        power.setPower(p, 99);
        assertEquals(20, power.powerOf(p));
        power.addPower(p, -100);
        assertEquals(-5, power.powerOf(p));
        assertEquals(20, power.maxPowerOf(p));
    }

    @Test
    void maxPowerUpgradeRaisesTheCapForMembersOnly() {
        Faction f = fx.faction("Alpha");
        f.setUpgrade("maxpower", 2);
        assertEquals(24, power.maxPowerOf(f.leader()));
        power.setPower(f.leader(), 30);
        assertEquals(24, power.powerOf(f.leader()));
        assertEquals(20, power.maxPowerOf(fx.player("Out")));
        assertEquals(24 + 0, power.factionMaxPower(f));
        f.setPowerBoost(3);
        assertEquals(27, power.factionMaxPower(f));
        assertEquals(27, power.factionPower(f));
        assertEquals(1.0, power.factionRatio(f));
    }

    @Test
    void ratioIsClampedAndSafeForEmptyFactions() {
        Faction f = fx.faction("Alpha");
        power.setPower(f.leader(), -5);
        assertEquals(0, power.factionRatio(f));
        assertEquals(0, power.factionPower(null));
        assertEquals(0, power.factionRatio(null));
    }

    @Test
    void deathCostsPowerUnlessWarzoneOrGrace() {
        UUID p = fx.player("Pat");
        power.onDeath(p, "w", false);
        assertEquals(6, power.powerOf(p));
        power.onDeath(p, "w", true);
        assertEquals(6, power.powerOf(p), "no loss in a WarZone by default");
        fx.set("power.lossInWarzone", true);
        power.onDeath(p, "w", true);
        assertEquals(2, power.powerOf(p));
        fx.set("grace.active", true).set("grace.endsAt", 0L);
        power.onDeath(p, "w", false);
        assertEquals(2, power.powerOf(p), "grace with no end protects");
        fx.set("grace.endsAt", System.currentTimeMillis() - 1);
        power.onDeath(p, "w", false);
        assertEquals(-2, power.powerOf(p), "an ended grace no longer protects");
        fx.set("grace.endsAt", 0L).set("grace.noPowerLoss", false);
        power.onDeath(p, "w", false);
        assertEquals(-5, power.powerOf(p), "loss stops at the minimum");
    }

    @Test
    void regenOnlyForOnlinePlayersAndUpToTheCap() {
        Faction f = fx.faction("Alpha");
        UUID on = f.leader();
        UUID off = fx.member(f, "Off", Rank.MEMBER);
        online.add(on);
        power.setPower(on, 19.95);
        power.setPower(off, 5);
        power.tick();
        assertEquals(20, power.powerOf(on), 1e-9, "regen stops at the maximum");
        assertEquals(5, power.powerOf(off), 1e-9);
        power.setPower(on, 10);
        f.setUpgrade("regen", 2);
        power.tick();
        assertEquals(10.15, power.powerOf(on), 1e-9, "two regen levels add half the base rate");
    }

    @Test
    void regenPullsPowerAboveALoweredMaximumDown() {
        Faction f = fx.faction("Alpha");
        f.setUpgrade("maxpower", 2);
        power.setPower(f.leader(), 24);
        online.add(f.leader());
        f.setUpgrade("maxpower", 0);
        power.tick();
        assertEquals(20, power.powerOf(f.leader()), 1e-9);
    }

    @Test
    void offlineDecayStartsAfterTheGraceDays() {
        Faction f = fx.faction("Alpha");
        UUID recent = fx.member(f, "Recent", Rank.MEMBER);
        UUID gone = fx.member(f, "Gone", Rank.MEMBER);
        long now = System.currentTimeMillis();
        fx.ctx.player(recent).setLastSeen(now - 2 * 86_400_000L);
        // No member record predates the boot, so no downtime is credited and four days count in full.
        fx.ctx.player(gone).setLastSeen(now - 4 * 86_400_000L);
        power.tick();
        assertEquals(10, power.powerOf(recent), 1e-9);
        assertTrue(power.powerOf(gone) < 10, "four days away with decay after three");
        assertEquals(10 - 1.0 / 1440, power.powerOf(gone), 1e-9);
    }

    @Test
    void smallChangesStillPublishOnceTheyAddUp() {
        UUID p = fx.player("Pat");
        events.clear();
        power.addPower(p, 0.004);
        power.addPower(p, 0.004);
        assertTrue(events.isEmpty());
        power.addPower(p, 0.004);
        assertEquals(1, events.size());
        assertInstanceOf(DominionEvent.PowerChanged.class, events.get(0));
    }

    // ---- deaths pool mode ------------------------------------------------------

    @Test
    void poolStartsAtTwoPlusMembersCappedAtTen() {
        fx.set("power.dtrMode", true);
        Faction f = fx.faction("Alpha");
        assertEquals(3, power.factionPower(f));
        assertEquals(3, power.factionMaxPower(f));
        for (int i = 0; i < 12; i++) {
            fx.member(f, "M" + i, Rank.MEMBER);
        }
        assertEquals(10, Dtr.max(f));
    }

    @Test
    void poolLosesOnePerMemberDeathDownToMinusThree() {
        fx.set("power.dtrMode", true);
        Faction f = fx.faction("Alpha");
        UUID p = f.leader();
        for (int i = 0; i < 3; i++) {
            power.onDeath(p, "w", false);
        }
        assertEquals(0, power.factionPower(f), 1e-9, "a stored zero pool stays zero");
        for (int i = 0; i < 5; i++) {
            power.onDeath(p, "w", false);
        }
        assertEquals(-3, power.factionPower(f), 1e-9);
        assertEquals(10, power.powerOf(p), "player power is untouched in this mode");
        UUID loner = fx.player("Loner");
        power.onDeath(loner, "w", false);
        assertEquals(10, power.powerOf(loner), "a factionless death costs nothing in this mode");
    }

    @Test
    void poolRegenScalesWithOnlineMembersUpToATenth() {
        fx.set("power.dtrMode", true);
        Faction f = fx.faction("Alpha");
        UUID a = fx.member(f, "A", Rank.MEMBER);
        UUID b = fx.member(f, "B", Rank.MEMBER);
        for (int i = 0; i < 5; i++) {
            power.onDeath(f.leader(), "w", false);
        }
        double start = power.factionPower(f);
        assertEquals(0, start, 1e-9);
        power.tick();
        assertEquals(0, power.factionPower(f), 1e-9, "nobody online, no regen");
        online.add(a);
        power.tick();
        assertEquals(0.05, power.factionPower(f), 1e-9);
        online.add(b);
        online.add(f.leader());
        power.tick();
        assertEquals(0.15, power.factionPower(f), 1e-9, "three online still regen only 0.1");
    }

    @Test
    void poolShrinksWhenMembersLeave() {
        fx.set("power.dtrMode", true);
        Faction f = fx.faction("Alpha");
        UUID m = fx.member(f, "M", Rank.MEMBER);
        assertEquals(4, power.factionPower(f), 1e-9);
        f.removeMember(m);
        power.tick();
        assertEquals(3, power.factionPower(f), 1e-9);
    }
}
