package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

/** Rank permission checks including the new member hold. */
final class Perms {
    private Perms() {}

    /** Actions a member on hold may only use as if they were a recruit. */
    static final EnumSet<FactionAction> HELD = EnumSet.of(FactionAction.CLAIM, FactionAction.UNCLAIM,
            FactionAction.WITHDRAW, FactionAction.VAULT, FactionAction.RELATIONS, FactionAction.DISBAND);

    static boolean can(Faction f, UUID actor, FactionAction action, long now) {
        if (f == null || actor == null) {
            return false;
        }
        Member m = f.member(actor);
        if (m == null) {
            return false;
        }
        if (actor.equals(f.leader())) {
            return true;
        }
        Rank rank = f.rankOf(actor);
        if (held(f, m, action, now)) {
            rank = Rank.RECRUIT;
        }
        return f.rankCan(rank, action);
    }

    static boolean held(Faction f, Member m, FactionAction action, long now) {
        boolean onHold;
        synchronized (f) {
            onHold = m.onHold(now);
        }
        return onHold && HELD.contains(action);
    }

    /** Player facing reason for a failed permission check. */
    static String denial(Faction f, UUID actor, FactionAction action, long now) {
        Member m = f.member(actor);
        if (m != null && held(f, m, action, now) && f.rankCan(f.rankOf(actor), action)) {
            return "New members cannot do that until their hold ends";
        }
        return "Your rank does not allow you to " + action.description().toLowerCase(Locale.ROOT);
    }
}
