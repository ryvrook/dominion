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

    /** A faction created this long ago or less counts as founded now. Imported factions are older. */
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
                long coins = (long) Math.floor(b.amount() + 1e-9);
                if (b.player() != null && coins > 0) {
                    out.add(new Credit(b.player(), StatKeys.BANK_DEPOSITED, coins));
                }
            }
            case DominionEvent.FactionCreated c -> {
                UUID founder = facts.founder(c.factionId());
                if (founder != null) {
                    out.add(new Credit(founder, StatKeys.FACTIONS_FOUNDED, 1));
                }
            }
            case DominionEvent.MemberInvited i -> {
                if (i.actor() != null) {
                    out.add(new Credit(i.actor(), StatKeys.INVITES, 1));
                }
            }
            default -> { }
        }
        return out;
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
     * defenders. A failed or cancelled siege credits nobody.
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
    }
}
