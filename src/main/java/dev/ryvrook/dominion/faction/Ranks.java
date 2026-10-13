package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.text.Msg;

import java.util.UUID;

/** Ranks, leadership, titles and the roster. */
final class Ranks {
    private final FactionSupport s;

    Ranks(FactionSupport s) {
        this.s = s;
    }

    Result setRank(UUID actor, UUID target, Rank rank) {
        if (rank == null) {
            return Result.fail("That is not a rank");
        }
        if (rank == Rank.LEADER) {
            return Result.fail("Use transfer to hand over leadership");
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.PROMOTE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        Result outranks = Membership.outranks(f, actor, target);
        if (outranks != null) {
            return outranks;
        }
        Rank actorRank = f.rankOf(actor);
        if (rank.ordinal() >= actorRank.ordinal()) {
            return Result.fail("You can only give ranks below your own");
        }
        Rank old = f.rankOf(target);
        if (old == rank) {
            return Result.fail(s.nameOf(target) + " is already " + article(f, rank));
        }
        apply(f, actor, target, rank);
        String verb = rank.ordinal() > old.ordinal() ? "promoted" : "demoted";
        return Result.pass(s.nameOf(target) + " was " + verb + " to " + f.rankName(rank));
    }

    Result promote(UUID actor, UUID target) {
        Faction f = s.factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction");
        }
        Rank current = f.rankOf(target);
        if (current == null) {
            return Result.fail("That player is not in your faction");
        }
        Rank next = current.next();
        if (next == current || next == Rank.LEADER) {
            return Result.fail(s.nameOf(target) + " cannot be promoted further");
        }
        return setRank(actor, target, next);
    }

    Result demote(UUID actor, UUID target) {
        Faction f = s.factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction");
        }
        Rank current = f.rankOf(target);
        if (current == null) {
            return Result.fail("That player is not in your faction");
        }
        if (current == Rank.LEADER) {
            return Result.fail("The leader cannot be demoted");
        }
        Rank prev = current.previous();
        if (prev == current) {
            return Result.fail(s.nameOf(target) + " is already the lowest rank");
        }
        return setRank(actor, target, prev);
    }

    private void apply(Faction f, UUID actor, UUID target, Rank rank) {
        synchronized (f) {
            Member m = f.member(target);
            if (m == null) {
                return;
            }
            m.setRank(rank);
            f.putMember(m);
        }
        String name = s.nameOf(target);
        s.audit(f, actor, "rank", "Set " + name + " to " + f.rankName(rank));
        s.toFaction(f, Msg.info(name + " is now " + article(f, rank)), false);
        s.changed(f);
    }

    Result transferLeader(UUID actor, UUID target, boolean confirmed) {
        Faction f = s.factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction");
        }
        if (!actor.equals(f.leader())) {
            return Result.fail("Only the leader can transfer leadership");
        }
        if (actor.equals(target)) {
            return Result.fail("You already lead the faction");
        }
        if (!f.isMember(target)) {
            return Result.fail("That player is not in your faction");
        }
        String name = s.nameOf(target);
        if (!confirmed) {
            return Result.fail("Please confirm that you want to make " + name + " the leader");
        }
        synchronized (s.lock) {
            if (!actor.equals(f.leader()) || !f.isMember(target) || s.ctx.repo().faction(f.id()) == null) {
                return Result.fail("That player is not in your faction");
            }
            handOver(f, actor, target);
        }
        s.toFaction(f, Msg.info(name + " is now the leader of " + f.tag()), false);
        return Result.pass(name + " is now the leader");
    }

    /** Moves leadership, demoting the old leader to officer. Caller holds the lock. */
    void handOver(Faction f, UUID oldLeader, UUID newLeader) {
        moveLeadership(f, newLeader);
        s.audit(f, oldLeader, "rank", "Transferred leadership to " + s.nameOf(newLeader));
        s.changed(f);
    }

    /** Makes the member the leader and demotes the previous leader to officer. No audit. */
    static void moveLeadership(Faction f, UUID newLeader) {
        synchronized (f) {
            UUID oldLeader = f.leader();
            Member old = oldLeader == null ? null : f.member(oldLeader);
            if (old != null) {
                old.setRank(Rank.OFFICER);
                f.putMember(old);
            }
            Member next = f.member(newLeader);
            next.setRank(Rank.LEADER);
            next.setHoldUntil(0);
            f.putMember(next);
            f.setLeader(newLeader);
        }
    }

    Result setTitle(UUID actor, UUID target, String title) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.PROMOTE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!actor.equals(target)) {
            Result outranks = Membership.outranks(f, actor, target);
            if (outranks != null) {
                return outranks;
            }
        }
        String clean = title == null ? "" : title.strip();
        String problem = TagRules.text(clean, TagRules.TITLE_MAX, "Titles");
        if (problem != null) {
            return Result.fail(problem);
        }
        synchronized (f) {
            Member m = f.member(target);
            if (m == null) {
                return Result.fail("That player is not in your faction");
            }
            m.setTitle(clean);
            f.putMember(m);
        }
        String name = s.nameOf(target);
        s.audit(f, actor, "rank", clean.isEmpty() ? "Cleared the title of " + name : "Set the title of " + name + " to " + clean);
        s.changed(f);
        return Result.pass(clean.isEmpty() ? "Title cleared" : "Title set");
    }

    Result rosterAdd(UUID actor, String name, Rank rank) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        String problem = TagRules.playerName(name);
        if (problem != null) {
            return Result.fail(problem);
        }
        Rank r = rank == null ? Rank.RECRUIT : rank;
        if (r == Rank.LEADER) {
            return Result.fail("The roster cannot hand out leadership");
        }
        Faction f = g.faction();
        if (!actor.equals(f.leader()) && r.ordinal() >= f.rankOf(actor).ordinal()) {
            return Result.fail("You can only give ranks below your own");
        }
        f.rosterPut(name, r);
        s.audit(f, actor, "member", "Added " + name + " to the roster as " + f.rankName(r));
        return Result.pass("Added " + name + " to the roster as " + f.rankName(r));
    }

    Result rosterRemove(UUID actor, String name) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        if (name == null || name.isBlank()) {
            return Result.fail("That is not a valid player name");
        }
        Faction f = g.faction();
        if (!f.rosterRemove(name)) {
            return Result.fail(name + " is not on the roster");
        }
        s.audit(f, actor, "member", "Removed " + name + " from the roster");
        return Result.pass("Removed " + name + " from the roster");
    }

    private static String article(Faction f, Rank rank) {
        String n = f.rankName(rank);
        boolean vowel = !n.isEmpty() && "aeiouAEIOU".indexOf(n.charAt(0)) >= 0;
        return (vowel ? "an " : "a ") + n;
    }
}
