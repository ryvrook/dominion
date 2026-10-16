package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.api.DominionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns Dominion bus events into per player counter credits. Pure apart from the {@link Facts} it is given, so
 * the mapping is tested without a server. It reads Dominion's own events only and never a service internal.
 */
public final class StatMapper {
    private StatMapper() {}

    /**
     * A faction created this long ago or less counts as founded now. Imported factions are older.
     * The default of integrations.foundedWindowSeconds, which ContextFacts reads.
     */
    static final long FOUNDED_WINDOW_MILLIS = 60_000L;

    /** One counter credit. */
    public record Credit(UUID player, String key, long amount) {}

    /** What the mapper needs to know beyond the event. Answers from memory. */
    public interface Facts {
        /** True when the player is a member of the faction. */
        boolean isMember(UUID factionId, UUID player);

        /** Every member of the faction, empty when it is unknown. */
        List<UUID> members(UUID factionId);

        /** True when the two player factions are enemies. */
        boolean atWar(UUID factionA, UUID factionB);

        /** The leader of a player faction created within the founding window, else null. */
        UUID founder(UUID factionId);

        /**
         * True the first time this faction invites this player, so revoking and inviting again counts once. The
         * default answers true for facts that keep no memory.
         */
        default boolean firstInvite(UUID factionId, UUID target) {
            return true;
        }

        /**
         * The whole coins a deposit adds to the player's net deposits into this faction's bank today, deposits
         * minus withdrawals and never below zero, less what was already credited today. A deposit and withdraw
         * loop credits nothing after the first round. The default credits the whole coins of the deposit.
         */
        default long depositCredit(UUID factionId, UUID player, double amount) {
            return (long) Math.floor(amount + 1e-9);
        }

        /** Takes a withdrawal off the player's net deposits today. */
        default void withdrew(UUID factionId, UUID player, double amount) {
        }

        /**
         * How much of an upgrade level is new to the player, the level less the highest level the player was credited
         * for in any faction, and zero when this level is not higher. Remembers the level. The default credits the
         * whole level.
         */
        default long upgradeLevelCredit(UUID player, int level) {
            return level;
        }

        /**
         * True the first time the player enters land of this faction, false for a faction already entered, a system
         * faction such as the safe zone, and once the memory is full. The default answers true.
         */
        default boolean firstVisit(UUID player, UUID factionId) {
            return true;
        }

        /** The player's power maximum. The default is infinite, so facts without the power service credit nothing. */
        default double maxPower(UUID player) {
            return Double.POSITIVE_INFINITY;
        }

        /** True the first time the player's power is reported at the maximum. The default answers true. */
        default boolean firstPowerMax(UUID player) {
            return true;
        }

        /**
         * True when this breach is new. The siege service publishes one BreachChanged per breached chunk, so a breach
         * of the same attacker against the same defender within a few seconds is the same breach. The default answers
         * true.
         */
        default boolean newBreach(UUID attacker, UUID defender) {
            return true;
        }
    }

    public static List<Credit> credits(DominionEvent event, Facts facts) {
        List<Credit> out = new ArrayList<>(2);
        if (event == null || facts == null) {
            return out;
        }
        switch (event) {
            case DominionEvent.ClaimChanged c -> {
                // Only a change the land module marks as counting, made by a member of the faction concerned.
                // Admin moves, decay and a chunk the faction released within the mission window credit nobody.
                if (c.counts() && c.actor() != null) {
                    if (c.newFaction() != null && facts.isMember(c.newFaction(), c.actor())) {
                        out.add(new Credit(c.actor(), StatKeys.CLAIMS, 1));
                    } else if (c.newFaction() == null && c.oldFaction() != null && facts.isMember(c.oldFaction(), c.actor())) {
                        out.add(new Credit(c.actor(), StatKeys.UNCLAIMS, 1));
                    }
                }
            }
            case DominionEvent.PlayerDied d -> died(d, facts, out);
            case DominionEvent.SiegePhase p -> siege(p, facts, out);
            case DominionEvent.MissionCompleted m -> {
                for (UUID member : facts.members(m.factionId())) {
                    out.add(new Credit(member, StatKeys.MISSIONS, 1));
                }
            }
            case DominionEvent.BankDeposited b -> {
                long coins = b.player() == null || b.amount() <= 0 ? 0 : facts.depositCredit(b.factionId(), b.player(), b.amount());
                if (coins > 0) {
                    out.add(new Credit(b.player(), StatKeys.BANK_DEPOSITED, coins));
                }
            }
            case DominionEvent.BankWithdrawn w -> {
                if (w.player() != null && w.amount() > 0) {
                    facts.withdrew(w.factionId(), w.player(), w.amount());
                }
            }
            case DominionEvent.FactionCreated c -> {
                UUID founder = facts.founder(c.factionId());
                if (founder != null) {
                    out.add(new Credit(founder, StatKeys.FACTIONS_FOUNDED, 1));
                }
            }
            case DominionEvent.MemberInvited i -> {
                if (i.actor() != null && (i.target() == null || facts.firstInvite(i.factionId(), i.target()))) {
                    out.add(new Credit(i.actor(), StatKeys.INVITES, 1));
                }
            }
            case DominionEvent.MemberJoined j -> {
                if (j.player() != null) {
                    out.add(new Credit(j.player(), StatKeys.JOINED, 1));
                }
            }
            case DominionEvent.MemberLeft l -> {
                // A kick by a member or an admin, with or without a ban. Leaving, inactivity, a disband and the
                // roster rotation are not kicks.
                if (l.player() != null && ("kick".equals(l.reason()) || "ban".equals(l.reason()))) {
                    out.add(new Credit(l.player(), StatKeys.KICKED, 1));
                }
            }
            case DominionEvent.MemberRankChanged r -> {
                // The member whose rank moved, once per change. A change the member made to themselves, or one with
                // no actor (the server), credits nobody.
                if (r.member() != null && r.actor() != null && !r.member().equals(r.actor()) && r.fromRank() != r.toRank()) {
                    out.add(new Credit(r.member(), StatKeys.RANK_CHANGES, 1));
                }
            }
            case DominionEvent.RelationChanged r -> relation(r, facts, out);
            case DominionEvent.UpgradeBought u -> {
                for (UUID member : facts.members(u.factionId())) {
                    out.add(new Credit(member, StatKeys.UPGRADES, 1));
                    long raise = u.level() <= 0 ? 0 : facts.upgradeLevelCredit(member, u.level());
                    if (raise > 0) {
                        out.add(new Credit(member, StatKeys.UPGRADE_LEVEL_MAX, raise));
                    }
                }
            }
            case DominionEvent.MarketTrade t -> {
                if (t.buyer() != null) {
                    out.add(new Credit(t.buyer(), StatKeys.MARKET_TRADES, 1));
                }
                if (t.seller() != null && !t.seller().equals(t.buyer())) {
                    out.add(new Credit(t.seller(), StatKeys.MARKET_TRADES, 1));
                }
            }
            case DominionEvent.PlayerEnteredTerritory e -> {
                // Land of another faction, once per faction. Wilderness and the player's own land are not entries.
                if (e.player() != null && e.toFaction() != null && !facts.isMember(e.toFaction(), e.player())
                        && facts.firstVisit(e.player(), e.toFaction())) {
                    out.add(new Credit(e.player(), StatKeys.FOREIGN_ENTRIES, 1));
                }
            }
            case DominionEvent.BreachChanged b -> {
                if (b.breached() && b.attacker() != null && facts.newBreach(b.attacker(), b.defender())) {
                    for (UUID member : facts.members(b.attacker())) {
                        out.add(new Credit(member, StatKeys.BREACHES, 1));
                    }
                }
            }
            case DominionEvent.GameEvent g -> game(g, facts, out);
            case DominionEvent.PowerChanged p -> {
                if (p.player() != null && p.power() + 1e-9 >= facts.maxPower(p.player()) && facts.firstPowerMax(p.player())) {
                    out.add(new Credit(p.player(), StatKeys.POWER_MAX, 1));
                }
            }
            default -> { }
        }
        return out;
    }

    /**
     * A war credits the members of the faction that declared it, which the relation service publishes first. An
     * alliance credits both sides, since both agreed. Truce and neutral credit nobody.
     */
    private static void relation(DominionEvent.RelationChanged r, Facts facts, List<Credit> out) {
        if ("ENEMY".equals(r.relation())) {
            for (UUID member : facts.members(r.a())) {
                out.add(new Credit(member, StatKeys.WARS_DECLARED, 1));
            }
        } else if ("ALLY".equals(r.relation())) {
            for (UUID member : facts.members(r.a())) {
                out.add(new Credit(member, StatKeys.ALLIANCES, 1));
            }
            if (r.b() != null && !r.b().equals(r.a())) {
                for (UUID member : facts.members(r.b())) {
                    out.add(new Credit(member, StatKeys.ALLIANCES, 1));
                }
            }
        }
    }

    /**
     * The events service publishes a koth event with phase captured and the capturing faction when a hill is won,
     * and an outpost event with phase captured and the winner when an outpost changes hands. Started, stopped,
     * envoy, bounty and season events name no winning faction and credit nobody.
     */
    private static void game(DominionEvent.GameEvent g, Facts facts, List<Credit> out) {
        if (g.faction() == null || !"captured".equals(g.phase())) {
            return;
        }
        String key;
        if ("koth".equals(g.kind())) {
            key = StatKeys.HILL_WINS;
        } else if ("outpost".equals(g.kind())) {
            key = StatKeys.OUTPOSTS_HELD;
        } else {
            return;
        }
        for (UUID member : facts.members(g.faction())) {
            out.add(new Credit(member, key, 1));
        }
    }

    private static void died(DominionEvent.PlayerDied d, Facts facts, List<Credit> out) {
        UUID vf = d.victimFaction();
        UUID kf = d.killerFaction();
        if (d.killer() != null && !d.killer().equals(d.victim()) && vf != null && kf != null && !vf.equals(kf)
                && facts.atWar(kf, vf)) {
            out.add(new Credit(d.killer(), StatKeys.ENEMY_KILLS, 1));
        }
        UUID owner = d.landOwner();
        if (d.victim() != null && vf != null && owner != null && !owner.equals(vf) && facts.atWar(vf, owner)) {
            out.add(new Credit(d.victim(), StatKeys.ENEMY_LAND_DEATHS, 1));
        }
    }

    /**
     * A siege that expired after a breach is won by the attackers, one the defenders held off is won by the
     * defenders, who also get a defence. A failed or cancelled siege credits nobody.
     */
    private static void siege(DominionEvent.SiegePhase p, Facts facts, List<Credit> out) {
        UUID winner;
        UUID loser;
        if ("EXPIRED".equals(p.phase())) {
            winner = p.attacker();
            loser = p.defender();
        } else if ("DEFENDED".equals(p.phase())) {
            winner = p.defender();
            loser = p.attacker();
        } else {
            return;
        }
        if (winner != null) {
            for (UUID member : facts.members(winner)) {
                out.add(new Credit(member, StatKeys.SIEGES_WON, 1));
            }
        }
        if (loser != null) {
            for (UUID member : facts.members(loser)) {
                out.add(new Credit(member, StatKeys.SIEGES_LOST, 1));
            }
        }
        if (winner != null && winner.equals(p.defender())) {
            for (UUID member : facts.members(winner)) {
                out.add(new Credit(member, StatKeys.SIEGES_DEFENDED, 1));
            }
        }
    }
}
