package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
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
            return Result.fail(Messages.get(FactionText.NOT_A_RANK));
        }
        if (rank == Rank.LEADER) {
            return Result.fail(Messages.get(FactionText.USE_TRANSFER));
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
            return Result.fail(Messages.get(FactionText.RANKS_BELOW_OWN));
        }
        Rank old = f.rankOf(target);
        if (old == rank) {
            return Result.fail(Messages.get(FactionText.ALREADY_RANK, "name", s.nameOf(target), "rank", article(f, rank)));
        }
        apply(f, actor, target, rank);
        String moved = rank.ordinal() > old.ordinal() ? FactionText.PROMOTED : FactionText.DEMOTED;
        return Result.pass(Messages.get(moved, "name", s.nameOf(target), "rank", f.rankName(rank)));
    }

    Result promote(UUID actor, UUID target) {
        Faction f = s.factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        Rank current = f.rankOf(target);
        if (current == null) {
            return Result.fail(Messages.get(FactionText.NOT_IN_YOUR_FACTION));
        }
        Rank next = current.next();
        if (next == current || next == Rank.LEADER) {
            return Result.fail(Messages.get(FactionText.NO_FURTHER_PROMOTION, "name", s.nameOf(target)));
        }
        return setRank(actor, target, next);
    }

    Result demote(UUID actor, UUID target) {
        Faction f = s.factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        Rank current = f.rankOf(target);
        if (current == null) {
            return Result.fail(Messages.get(FactionText.NOT_IN_YOUR_FACTION));
        }
        if (current == Rank.LEADER) {
            return Result.fail(Messages.get(FactionText.LEADER_NO_DEMOTE));
        }
        Rank prev = current.previous();
        if (prev == current) {
            return Result.fail(Messages.get(FactionText.ALREADY_LOWEST, "name", s.nameOf(target)));
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
        s.audit(f, actor, "rank", Messages.get(FactionText.AUDIT_RANK_SET, "name", name, "rank", f.rankName(rank)));
        s.toFaction(f, Msg.info(Messages.get(FactionText.NOW_RANK, "name", name, "rank", article(f, rank))), false);
        s.changed(f);
    }

    Result transferLeader(UUID actor, UUID target, boolean confirmed) {
        Faction f = s.factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!actor.equals(f.leader())) {
            return Result.fail(Messages.get(FactionText.ONLY_LEADER_TRANSFER));
        }
        if (actor.equals(target)) {
            return Result.fail(Messages.get(FactionText.ALREADY_LEAD));
        }
        if (!f.isMember(target)) {
            return Result.fail(Messages.get(FactionText.NOT_IN_YOUR_FACTION));
        }
        String name = s.nameOf(target);
        if (!confirmed) {
            return Result.fail(Messages.get(FactionText.CONFIRM_TRANSFER, "name", name));
        }
        synchronized (s.lock) {
            if (!actor.equals(f.leader()) || !f.isMember(target) || s.ctx.repo().faction(f.id()) == null) {
                return Result.fail(Messages.get(FactionText.NOT_IN_YOUR_FACTION));
            }
            handOver(f, actor, target);
        }
        s.toFaction(f, Msg.info(Messages.get(FactionText.NOW_LEADER_OF, "name", name, "faction", f.tag())), false);
        return Result.pass(Messages.get(FactionText.NOW_THE_LEADER, "name", name));
    }

    /** Moves leadership, demoting the old leader to officer. Caller holds the lock. */
    void handOver(Faction f, UUID oldLeader, UUID newLeader) {
        moveLeadership(f, newLeader);
        s.audit(f, oldLeader, "rank", Messages.get(FactionText.AUDIT_TRANSFERRED, "name", s.nameOf(newLeader)));
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
        String problem = TagRules.text(clean, TagRules.TITLE_MAX, Messages.get(FactionText.LABEL_TITLES));
        if (problem != null) {
            return Result.fail(problem);
        }
        synchronized (f) {
            Member m = f.member(target);
            if (m == null) {
                return Result.fail(Messages.get(FactionText.NOT_IN_YOUR_FACTION));
            }
            m.setTitle(clean);
            f.putMember(m);
        }
        String name = s.nameOf(target);
        s.audit(f, actor, "rank", clean.isEmpty() ? Messages.get(FactionText.AUDIT_TITLE_CLEARED, "name", name) : Messages.get(FactionText.AUDIT_TITLE_SET, "name", name, "title", clean));
        s.changed(f);
        return Result.pass(Messages.get(clean.isEmpty() ? FactionText.TITLE_CLEARED : FactionText.TITLE_SET));
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
            return Result.fail(Messages.get(FactionText.ROSTER_NO_LEADER));
        }
        Faction f = g.faction();
        if (!actor.equals(f.leader()) && r.ordinal() >= f.rankOf(actor).ordinal()) {
            return Result.fail(Messages.get(FactionText.RANKS_BELOW_OWN));
        }
        f.rosterPut(name, r);
        s.audit(f, actor, "member", Messages.get(FactionText.ROSTER_ADDED, "name", name, "rank", f.rankName(r)));
        return Result.pass(Messages.get(FactionText.ROSTER_ADDED, "name", name, "rank", f.rankName(r)));
    }

    Result rosterRemove(UUID actor, String name) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        if (name == null || name.isBlank()) {
            return Result.fail(Messages.get(FactionText.PLAYER_NAME_INVALID));
        }
        Faction f = g.faction();
        if (!f.rosterRemove(name)) {
            return Result.fail(Messages.get(FactionText.NOT_ON_ROSTER, "name", name));
        }
        s.audit(f, actor, "member", Messages.get(FactionText.ROSTER_REMOVED, "name", name));
        return Result.pass(Messages.get(FactionText.ROSTER_REMOVED, "name", name));
    }

    private static String article(Faction f, Rank rank) {
        String n = f.rankName(rank);
        boolean vowel = !n.isEmpty() && "aeiouAEIOU".indexOf(n.charAt(0)) >= 0;
        return Messages.get(vowel ? FactionText.RANK_ARTICLE_AN : FactionText.RANK_ARTICLE_A, "rank", n);
    }
}
