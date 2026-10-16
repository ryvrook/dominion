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
    UUID founder;
    final Map<UUID, Long> upgradeLevel = new HashMap<>();
    final Set<String> visited = new HashSet<>();
    final Set<UUID> atMax = new HashSet<>();
    final Set<String> breaches = new HashSet<>();
    double maxPower = 10;

    /** Facts with the memories the context facts keep, so the once only rules are checked here too. */
    final StatMapper.Facts facts = new StatMapper.Facts() {
        @Override public boolean isMember(UUID f, UUID p) { return members.getOrDefault(f, List.of()).contains(p); }
        @Override public List<UUID> members(UUID f) { return members.getOrDefault(f, List.of()); }
        @Override public boolean atWar(UUID a, UUID b) { return wars.contains(a + ">" + b) || wars.contains(b + ">" + a); }
        @Override public UUID founder(UUID f) { return founder; }
        @Override public long upgradeLevelCredit(UUID p, int level) {
            long raise = level - upgradeLevel.getOrDefault(p, 0L);
            if (raise > 0) {
                upgradeLevel.put(p, (long) level);
            }
            return Math.max(0, raise);
        }
        @Override public boolean firstVisit(UUID p, UUID f) { return visited.add(p + ">" + f); }
        @Override public double maxPower(UUID p) { return maxPower; }
        @Override public boolean firstPowerMax(UUID p) { return atMax.add(p); }
        @Override public boolean newBreach(UUID a, UUID d) { return breaches.add(a + ">" + d); }
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
        assertEquals(25, StatKeys.ALL.size());
        assertEquals(25, new HashSet<>(StatKeys.ALL).size(), "no key twice");
        assertEquals(10, StatKeys.ORIGINAL.size());
        assertEquals(StatKeys.INVITES, StatKeys.ORIGINAL.get(9));
        assertFalse(StatKeys.BOARDS.contains(StatKeys.POWER_MAX));
        assertFalse(StatKeys.BOARDS.contains(StatKeys.TUTORIAL_DONE));
        assertEquals(23, StatKeys.BOARDS.size());
    }

    @Test
    void claimsGoToTheMemberWhoClaimed() {
        assertEquals(List.of(credit(alice, StatKeys.CLAIMS, 1)),
                map(new DominionEvent.ClaimChanged("w", 5L, null, wolf, alice, true)));
        assertEquals(List.of(credit(alice, StatKeys.CLAIMS, 1)),
                map(new DominionEvent.ClaimChanged("w", 5L, bear, wolf, alice, true)), "a taken chunk is a claim");
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, null, wolf, admin, true)).isEmpty(), "an outsider credits nobody");
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, null, wolf, null, true)).isEmpty(), "the server credits nobody");
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, null, wolf, alice, false)).isEmpty(), "a reclaim does not count");
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, null, wolf)).isEmpty(), "the short form names no actor");
    }

    @Test
    void unclaimsGoToTheMemberWhoReleased() {
        assertEquals(List.of(credit(alice, StatKeys.UNCLAIMS, 1)),
                map(new DominionEvent.ClaimChanged("w", 5L, wolf, null, alice, true)));
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, wolf, null, alice, false)).isEmpty(), "a repeat release does not count");
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, wolf, null, admin, true)).isEmpty(), "an admin release credits nobody");
        assertTrue(map(new DominionEvent.ClaimChanged("w", 5L, wolf, null, null, false)).isEmpty(), "decay credits nobody");
        assertTrue(map(new DominionEvent.AuditLogged(wolf, alice, "land", "Released chunk 3, 4 in default")).isEmpty(),
                "the audit text is no longer read");
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
                credit(carl, StatKeys.SIEGES_LOST, 1), credit(bob, StatKeys.SIEGES_DEFENDED, 1)), defended);
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
    void joinedGoesToThePlayerWhoJoined() {
        assertEquals(List.of(credit(bob, StatKeys.JOINED, 1)), map(new DominionEvent.MemberJoined(wolf, bob)));
        assertEquals(List.of(credit(bob, StatKeys.JOINED, 1)), map(new DominionEvent.MemberJoined(wolf, bob)), "every join counts");
        assertTrue(map(new DominionEvent.MemberJoined(wolf, null)).isEmpty());
    }

    @Test
    void kickedGoesToThePlayerKicked() {
        assertEquals(List.of(credit(bob, StatKeys.KICKED, 1)), map(new DominionEvent.MemberLeft(bear, bob, "kick")));
        assertEquals(List.of(credit(bob, StatKeys.KICKED, 1)), map(new DominionEvent.MemberLeft(bear, bob, "ban")), "a kick with a ban");
        for (String reason : List.of("leave", "disband", "inactive", "rotation", "admin")) {
            assertTrue(map(new DominionEvent.MemberLeft(bear, bob, reason)).isEmpty(), reason);
        }
        assertTrue(map(new DominionEvent.MemberLeft(bear, bob, null)).isEmpty());
    }

    @Test
    void warsDeclaredGoToTheDeclaringSide() {
        assertEquals(List.of(credit(alice, StatKeys.WARS_DECLARED, 1), credit(carl, StatKeys.WARS_DECLARED, 1)),
                map(new DominionEvent.RelationChanged(wolf, bear, "ENEMY")));
        assertEquals(List.of(credit(bob, StatKeys.WARS_DECLARED, 1)),
                map(new DominionEvent.RelationChanged(bear, wolf, "ENEMY")), "the first faction is the one that declared");
        assertTrue(map(new DominionEvent.RelationChanged(wolf, bear, "NEUTRAL")).isEmpty(), "peace is not a war");
        assertTrue(map(new DominionEvent.RelationChanged(wolf, bear, "TRUCE")).isEmpty());
    }

    @Test
    void alliancesGoToBothSides() {
        assertEquals(List.of(credit(alice, StatKeys.ALLIANCES, 1), credit(carl, StatKeys.ALLIANCES, 1),
                credit(bob, StatKeys.ALLIANCES, 1)), map(new DominionEvent.RelationChanged(wolf, bear, "ALLY")));
        assertEquals(List.of(credit(bob, StatKeys.ALLIANCES, 1)),
                map(new DominionEvent.RelationChanged(bear, fox, "ALLY")), "an empty faction adds nobody");
    }

    @Test
    void upgradesGoToEveryMember() {
        List<StatMapper.Credit> got = map(new DominionEvent.UpgradeBought(wolf, "maxpower", 2));
        assertTrue(got.contains(credit(alice, StatKeys.UPGRADES, 1)) && got.contains(credit(carl, StatKeys.UPGRADES, 1)));
        assertTrue(map(new DominionEvent.UpgradeBought(fox, "maxpower", 1)).isEmpty(), "no members, no credit");
    }

    @Test
    void upgradeLevelMaxSendsOnlyTheRaise() {
        assertEquals(List.of(credit(alice, StatKeys.UPGRADES, 1), credit(alice, StatKeys.UPGRADE_LEVEL_MAX, 2),
                credit(carl, StatKeys.UPGRADES, 1), credit(carl, StatKeys.UPGRADE_LEVEL_MAX, 2)),
                map(new DominionEvent.UpgradeBought(wolf, "maxpower", 2)));
        assertEquals(List.of(credit(alice, StatKeys.UPGRADES, 1), credit(alice, StatKeys.UPGRADE_LEVEL_MAX, 1),
                credit(carl, StatKeys.UPGRADES, 1), credit(carl, StatKeys.UPGRADE_LEVEL_MAX, 1)),
                map(new DominionEvent.UpgradeBought(wolf, "maxpower", 3)), "level 3 after level 2 raises by one");
        assertEquals(List.of(credit(alice, StatKeys.UPGRADES, 1), credit(carl, StatKeys.UPGRADES, 1)),
                map(new DominionEvent.UpgradeBought(wolf, "claims", 1)), "a lower level of another upgrade raises nothing");
        // Alice moves to Bear, whose level 2 is no higher than the level 3 she was credited for.
        members.put(bear, new ArrayList<>(List.of(bob, alice)));
        assertEquals(List.of(credit(bob, StatKeys.UPGRADES, 1), credit(bob, StatKeys.UPGRADE_LEVEL_MAX, 2),
                credit(alice, StatKeys.UPGRADES, 1)), map(new DominionEvent.UpgradeBought(bear, "maxpower", 2)));
    }

    @Test
    void marketTradesGoToBuyerAndSeller() {
        UUID listing = UUID.randomUUID();
        assertEquals(List.of(credit(alice, StatKeys.MARKET_TRADES, 1), credit(bob, StatKeys.MARKET_TRADES, 1)),
                map(new DominionEvent.MarketTrade(listing, "Ore_Iron", 4, 20, alice, bob)));
        assertEquals(List.of(credit(alice, StatKeys.MARKET_TRADES, 1)),
                map(new DominionEvent.MarketTrade(listing, "Ore_Iron", 4, 20, alice, null)), "a shop nobody stocked");
        assertEquals(List.of(credit(alice, StatKeys.MARKET_TRADES, 1)),
                map(new DominionEvent.MarketTrade(listing, "Ore_Iron", 4, 20, alice, alice)), "buying your own stock counts once");
    }

    @Test
    void foreignEntriesCountEachOtherFactionOnce() {
        assertEquals(List.of(credit(alice, StatKeys.FOREIGN_ENTRIES, 1)),
                map(new DominionEvent.PlayerEnteredTerritory(alice, "w", null, bear)));
        assertTrue(map(new DominionEvent.PlayerEnteredTerritory(alice, "w", null, bear)).isEmpty(), "the same faction again");
        assertTrue(map(new DominionEvent.PlayerEnteredTerritory(alice, "w", bear, wolf)).isEmpty(), "own land");
        assertTrue(map(new DominionEvent.PlayerEnteredTerritory(alice, "w", bear, null)).isEmpty(), "wilderness");
        assertEquals(List.of(credit(alice, StatKeys.FOREIGN_ENTRIES, 1)),
                map(new DominionEvent.PlayerEnteredTerritory(alice, "w", null, fox)), "a third faction is new");
        assertEquals(List.of(credit(bob, StatKeys.FOREIGN_ENTRIES, 1)),
                map(new DominionEvent.PlayerEnteredTerritory(bob, "w", null, wolf)), "memory is per player");
    }

    @Test
    void siegesDefendedGoToTheDefenders() {
        List<StatMapper.Credit> defended = map(new DominionEvent.SiegePhase(UUID.randomUUID(), "DEFENDED", wolf, bear, "w", 1L));
        assertTrue(defended.contains(credit(bob, StatKeys.SIEGES_DEFENDED, 1)));
        assertFalse(defended.contains(credit(alice, StatKeys.SIEGES_DEFENDED, 1)));
        List<StatMapper.Credit> expired = map(new DominionEvent.SiegePhase(UUID.randomUUID(), "EXPIRED", wolf, bear, "w", 1L));
        assertTrue(expired.stream().noneMatch(c -> c.key().equals(StatKeys.SIEGES_DEFENDED)), "a lost siege is no defence");
    }

    @Test
    void breachesGoToTheAttackersOncePerBreach() {
        assertEquals(List.of(credit(alice, StatKeys.BREACHES, 1), credit(carl, StatKeys.BREACHES, 1)),
                map(new DominionEvent.BreachChanged("w", 1L, wolf, bear, true)));
        assertTrue(map(new DominionEvent.BreachChanged("w", 2L, wolf, bear, true)).isEmpty(), "the next chunk of the same breach");
        assertTrue(map(new DominionEvent.BreachChanged("w", 1L, wolf, bear, false)).isEmpty(), "closing credits nobody");
        assertEquals(List.of(credit(bob, StatKeys.BREACHES, 1)),
                map(new DominionEvent.BreachChanged("w", 1L, bear, wolf, true)), "the other way round is another breach");
    }

    @Test
    void hillWinsGoToTheCapturingFaction() {
        assertEquals(List.of(credit(alice, StatKeys.HILL_WINS, 1), credit(carl, StatKeys.HILL_WINS, 1)),
                map(new DominionEvent.GameEvent("koth", "North", "captured", "w", wolf, "Wolf took North")));
        assertTrue(map(new DominionEvent.GameEvent("koth", "North", "started", "w", null, "North is open")).isEmpty());
        assertTrue(map(new DominionEvent.GameEvent("koth", "North", "stopped", "w", null, "North closed")).isEmpty());
        assertTrue(map(new DominionEvent.GameEvent("bounty", "Bob", "claimed", null, wolf, "Bounty claimed")).isEmpty(), "a bounty is not a hill");
    }

    @Test
    void outpostsHeldGoToTheWinner() {
        assertEquals(List.of(credit(bob, StatKeys.OUTPOSTS_HELD, 1)),
                map(new DominionEvent.GameEvent("outpost", "Mine", "captured", "w", bear, "Bear took Mine")));
        assertTrue(map(new DominionEvent.GameEvent("outpost", "Mine", "captured", "w", null, "reset")).isEmpty(), "no winner");
        assertTrue(map(new DominionEvent.GameEvent("outpost", "Mine", "reset", "w", bear, "reset")).isEmpty());
    }

    @Test
    void powerMaxOncePerPlayer() {
        assertTrue(map(new DominionEvent.PowerChanged(alice, 9.5)).isEmpty(), "below the maximum");
        assertEquals(List.of(credit(alice, StatKeys.POWER_MAX, 1)), map(new DominionEvent.PowerChanged(alice, 10)));
        assertTrue(map(new DominionEvent.PowerChanged(alice, 10)).isEmpty(), "only once");
        maxPower = Double.POSITIVE_INFINITY;
        assertTrue(map(new DominionEvent.PowerChanged(bob, 10)).isEmpty(), "no maximum known, no credit");
    }

    @Test
    void rankChangesAreNotMapped() {
        // The rank audit line names the member in text only and the event carries the actor, so there is nobody
        // to credit without guessing. The key stays listed for Feats and is never sent.
        assertTrue(map(new DominionEvent.AuditLogged(wolf, alice, "rank", "Alice set Carl to Officer")).isEmpty());
        assertTrue(StatKeys.ALL.contains(StatKeys.RANK_CHANGES));
    }

    @Test
    void tutorialDoneGoesThroughTheFanoutOnce() {
        StatFanout fanout = new StatFanout(facts);
        List<String> got = new ArrayList<>();
        fanout.add((p, k, n) -> got.add(p + " " + k + "=" + n));
        dev.ryvrook.dominion.model.PlayerData data = new dev.ryvrook.dominion.model.PlayerData(alice, "Alice", 5, 0);
        assertTrue(data.setTutorialFinished(true));
        assertFalse(data.setTutorialFinished(true), "already finished");
        fanout.credit(alice, StatKeys.TUTORIAL_DONE, 1);
        assertEquals(List.of(alice + " " + StatKeys.TUTORIAL_DONE + "=1"), got);
        fanout.credit(null, StatKeys.TUTORIAL_DONE, 1);
        fanout.credit(alice, StatKeys.TUTORIAL_DONE, 0);
        assertEquals(1, got.size(), "no player or no amount sends nothing");
    }

    @Test
    void otherEventsCreditNothing() {
        assertTrue(map(new DominionEvent.FactionChanged(wolf)).isEmpty());
        assertTrue(map(new DominionEvent.FactionDisbanded(wolf, "WOLF")).isEmpty());
        assertTrue(map(new DominionEvent.GraceChanged(true, 0L)).isEmpty());
        assertTrue(map(new DominionEvent.AuditLogged(wolf, alice, "member", "Bob joined")).isEmpty());
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
