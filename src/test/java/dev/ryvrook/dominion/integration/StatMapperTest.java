package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.api.DominionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Every Dominion event that feeds a counter, and the ones that must not. */
class StatMapperTest {
    final UUID wolf = UUID.randomUUID();
    final UUID bear = UUID.randomUUID();
    final UUID fox = UUID.randomUUID();
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final UUID carl = UUID.randomUUID();
    final UUID admin = UUID.randomUUID();

    final Map<UUID, List<UUID>> members = new HashMap<>();
    final Set<String> wars = new HashSet<>();
    UUID claimer;
    UUID founder;

    final StatMapper.Facts facts = new StatMapper.Facts() {
        @Override public UUID claimedBy(String world, long chunkIndex) { return claimer; }
        @Override public boolean isMember(UUID f, UUID p) { return members.getOrDefault(f, List.of()).contains(p); }
        @Override public List<UUID> members(UUID f) { return members.getOrDefault(f, List.of()); }
        @Override public boolean atWar(UUID a, UUID b) { return wars.contains(a + ">" + b) || wars.contains(b + ">" + a); }
        @Override public UUID founder(UUID f) { return founder; }
    };

    StatMapperTest() {
        members.put(wolf, new ArrayList<>(List.of(alice, carl)));
        members.put(bear, new ArrayList<>(List.of(bob)));
        members.put(fox, new ArrayList<>());
        wars.add(wolf + ">" + bear);
    }

    List<StatMapper.Credit> map(DominionEvent e) {
        return StatMapper.credits(e, facts);
    }

    static StatMapper.Credit credit(UUID p, String key, long n) {
        return new StatMapper.Credit(p, key, n);
    }

    @Test
    void keysArePrefixedAndBoardsAreKnownKeys() {
        for (String k : StatKeys.ALL) {
            assertTrue(k.startsWith("dominion:") && k.equals(k.toLowerCase()) && !k.contains(" "), k);
        }
        assertTrue(StatKeys.ALL.containsAll(StatKeys.BOARDS));
        assertEquals(10, StatKeys.ALL.size());
    }

    @Test
    void claimsGoToTheMemberWhoClaimed() {
        claimer = alice;
        assertEquals(List.of(credit(alice, StatKeys.CLAIMS, 1)),
                map(new DominionEvent.ClaimChanged("w", 5L, null, wolf)));
        assertEquals(List.of(credit(alice, StatKeys.CLAIMS, 1)),
                map(new DominionEvent.ClaimChanged("w", 5L, bear, wolf)), "a taken chunk is a claim");
        claimer = admin;
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, null, wolf)).isEmpty(), "an admin claim credits nobody");
        claimer = null;
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, null, wolf)).isEmpty());
        claimer = alice;
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, wolf, null)).isEmpty(), "a release is not a claim");
    }

    @Test
    void unclaimsComeFromTheLandAudit() {
        assertEquals(List.of(credit(alice, StatKeys.UNCLAIMS, 1)),
                map(new DominionEvent.AuditLogged(wolf, alice, "land", "Released chunk 3, 4 in default")));
        assertTrue(map(new DominionEvent.AuditLogged(wolf, null, "land", "Lost chunk 3, 4 in default after 9 days")).isEmpty());
        assertTrue(map(new DominionEvent.AuditLogged(wolf, admin, "land", "An admin released chunk 3, 4 in default")).isEmpty());
        assertTrue(map(new DominionEvent.AuditLogged(wolf, alice, "bank", "Released chunk")).isEmpty());
        assertTrue(map(new DominionEvent.AuditLogged(wolf, alice, "land", "Claimed chunk 3, 4 in default")).isEmpty());
    }

    @Test
    void enemyKillsNeedAWar() {
        assertEquals(List.of(credit(alice, StatKeys.ENEMY_KILLS, 1)),
                map(new DominionEvent.PlayerDied(bob, alice, bear, wolf, "w", 1L, null)));
        assertTrue(map(new DominionEvent.PlayerDied(bob, alice, fox, wolf, "w", 1L, null)).isEmpty(), "neutral victim");
        assertTrue(map(new DominionEvent.PlayerDied(carl, alice, wolf, wolf, "w", 1L, null)).isEmpty(), "team kill");
        assertTrue(map(new DominionEvent.PlayerDied(bob, alice, null, wolf, "w", 1L, null)).isEmpty(), "factionless victim");
        assertTrue(map(new DominionEvent.PlayerDied(bob, null, bear, null, "w", 1L, null)).isEmpty(), "no killer");
    }

    @Test
    void deathsInEnemyLand() {
        assertEquals(List.of(credit(bob, StatKeys.ENEMY_LAND_DEATHS, 1)),
                map(new DominionEvent.PlayerDied(bob, null, bear, null, "w", 1L, wolf)));
        assertTrue(map(new DominionEvent.PlayerDied(bob, null, bear, null, "w", 1L, bear)).isEmpty(), "own land");
        assertTrue(map(new DominionEvent.PlayerDied(bob, null, bear, null, "w", 1L, fox)).isEmpty(), "neutral land");
        assertTrue(map(new DominionEvent.PlayerDied(bob, null, bear, null, "w", 1L, null)).isEmpty(), "wilderness");
        // Killed by an enemy on the enemy's own land counts both ways.
        List<StatMapper.Credit> both = map(new DominionEvent.PlayerDied(bob, alice, bear, wolf, "w", 1L, wolf));
        assertEquals(List.of(credit(alice, StatKeys.ENEMY_KILLS, 1), credit(bob, StatKeys.ENEMY_LAND_DEATHS, 1)), both);
    }

    @Test
    void siegesCreditEveryMemberOfBothSides() {
        List<StatMapper.Credit> expired = map(new DominionEvent.SiegePhase(UUID.randomUUID(), "EXPIRED", wolf, bear, "w", 1L));
        assertEquals(List.of(credit(alice, StatKeys.SIEGES_WON, 1), credit(carl, StatKeys.SIEGES_WON, 1),
                credit(bob, StatKeys.SIEGES_LOST, 1)), expired);
        List<StatMapper.Credit> defended = map(new DominionEvent.SiegePhase(UUID.randomUUID(), "DEFENDED", wolf, bear, "w", 1L));
        assertEquals(List.of(credit(bob, StatKeys.SIEGES_WON, 1), credit(alice, StatKeys.SIEGES_LOST, 1),
                credit(carl, StatKeys.SIEGES_LOST, 1)), defended);
        for (String phase : List.of("CAPTURING", "BREACHED", "FAILED")) {
            assertTrue(map(new DominionEvent.SiegePhase(UUID.randomUUID(), phase, wolf, bear, "w", 1L)).isEmpty(), phase);
        }
    }

    @Test
    void missionsDepositsFoundingAndInvites() {
        assertEquals(List.of(credit(alice, StatKeys.MISSIONS, 1), credit(carl, StatKeys.MISSIONS, 1)),
                map(new DominionEvent.MissionCompleted(wolf, "m1")));
        assertEquals(List.of(credit(alice, StatKeys.BANK_DEPOSITED, 250)),
                map(new DominionEvent.BankDeposited(wolf, alice, 250.75)));
        assertTrue(map(new DominionEvent.BankDeposited(wolf, alice, 0.5)).isEmpty(), "under one coin");
        founder = alice;
        assertEquals(List.of(credit(alice, StatKeys.FACTIONS_FOUNDED, 1)), map(new DominionEvent.FactionCreated(wolf)));
        founder = null;
        assertTrue(map(new DominionEvent.FactionCreated(wolf)).isEmpty(), "an imported or system faction");
        assertEquals(List.of(credit(alice, StatKeys.INVITES, 1)), map(new DominionEvent.MemberInvited(wolf, alice, bob)));
    }

    @Test
    void otherEventsCreditNothing() {
        assertTrue(map(new DominionEvent.FactionChanged(wolf)).isEmpty());
        assertTrue(map(new DominionEvent.PowerChanged(alice, 3)).isEmpty());
        assertTrue(map(new DominionEvent.MemberJoined(wolf, bob)).isEmpty());
        assertTrue(StatMapper.credits(null, facts).isEmpty());
    }

    @Test
    void theFanoutSkipsMappingWithoutSinksAndSurvivesABadSink() {
        StatFanout fanout = new StatFanout(facts);
        fanout.accept(new DominionEvent.MemberInvited(wolf, alice, bob));
        List<String> got = new ArrayList<>();
        fanout.add((p, k, n) -> {
            throw new IllegalStateException("broken sibling");
        });
        StatSink good = (p, k, n) -> got.add(k + "=" + n);
        fanout.add(good);
        fanout.add(good);
        assertEquals(2, fanout.sinks());
        fanout.accept(new DominionEvent.MemberInvited(wolf, alice, bob));
        assertEquals(List.of(StatKeys.INVITES + "=1"), got);
        fanout.remove(good);
        fanout.accept(new DominionEvent.MemberInvited(wolf, alice, bob));
        assertEquals(1, got.size());
    }
}
