package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.Locale;
import java.util.UUID;

/** Invites, joining, join requests, leaving, kicks and bans. */
final class Membership {
    private final FactionSupport s;

    Membership(FactionSupport s) {
        this.s = s;
    }

    Result invite(UUID actor, UUID target, String targetName) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (target.equals(actor)) {
            return Result.fail(Messages.get(FactionText.INVITE_SELF));
        }
        if (f.isMember(target)) {
            return Result.fail(Messages.get(FactionText.ALREADY_MEMBER, "name", targetName));
        }
        if (f.isBanned(target)) {
            return Result.fail(Messages.get(FactionText.INVITE_BANNED, "name", targetName));
        }
        if (s.full(f)) {
            return Result.fail(Messages.get(FactionText.FULL));
        }
        long hours = Math.max(1, s.settings().integer("faction.inviteHours"));
        // Renewing an invite that is still open is not a new invite, so counters cannot be farmed with it.
        boolean fresh = !f.hasValidInvite(target, Time.now());
        f.invite(target, Time.now() + hours * FactionSupport.HOUR);
        s.audit(f, actor, "member", Messages.get(FactionText.AUDIT_INVITED, "name", targetName));
        if (fresh) {
            s.publish(new DominionEvent.MemberInvited(f.id(), actor, target));
        }
        s.tell(target, Msg.info(Messages.get(hours == 1 ? FactionText.INVITED_YOU_HOUR : FactionText.INVITED_YOU_HOURS,
                "inviter", s.nameOf(actor), "faction", f.tag(), "hours", hours)));
        return Result.pass(Messages.get(FactionText.INVITED, "name", targetName, "faction", f.tag()));
    }

    Result uninvite(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!f.uninvite(target)) {
            return Result.fail(Messages.get(FactionText.NO_INVITE));
        }
        s.audit(f, actor, "member", Messages.get(FactionText.AUDIT_INVITE_REVOKED, "name", s.nameOf(target)));
        return Result.pass(Messages.get(FactionText.INVITE_REVOKED));
    }

    Result join(UUID player, String playerName, String tag) {
        synchronized (s.lock) {
            if (s.factionOf(player) != null) {
                return Result.fail(Messages.get(FactionText.ALREADY_IN_FACTION));
            }
            Faction f = s.ctx.repo().factionByTag(tag);
            if (f == null || f.isSystem()) {
                return Result.fail(Messages.get(FactionText.NO_FACTION_WITH_TAG));
            }
            if (f.isBanned(player)) {
                return Result.fail(Messages.get(FactionText.YOU_ARE_BANNED, "faction", f.tag()));
            }
            Rank rosterRank = f.rosterRank(playerName);
            if (s.full(f) && !(rosterRank != null && rotate(f, playerName))) {
                return Result.fail(Messages.get(FactionText.OTHER_FULL, "faction", f.tag()));
            }
            String how;
            Rank rank = Rank.RECRUIT;
            if (rosterRank != null) {
                rank = rosterRank == Rank.LEADER ? Rank.OFFICER : rosterRank;
                how = Messages.get(FactionText.JOIN_HOW_ROSTER);
            } else if (f.hasValidInvite(player, Time.now())) {
                how = Messages.get(FactionText.JOIN_HOW_INVITE);
            } else if (f.setting("open", false)) {
                how = Messages.get(FactionText.JOIN_HOW_OPEN);
            } else {
                return Result.fail(Messages.get(FactionText.INVITE_ONLY, "faction", f.tag()));
            }
            s.addMember(f, player, playerName, rank, null, how);
            if (rosterRank != null) {
                f.rosterBind(player, playerName);
            }
            return Result.pass(Messages.get(FactionText.YOU_JOINED, "faction", f.tag()));
        }
    }

    Result requestJoin(UUID player, String playerName, String tag) {
        Faction f = s.ctx.repo().factionByTag(tag);
        if (f == null || f.isSystem()) {
            return Result.fail(Messages.get(FactionText.NO_FACTION_WITH_TAG));
        }
        if (s.factionOf(player) != null) {
            return Result.fail(Messages.get(FactionText.ALREADY_IN_FACTION));
        }
        if (f.isBanned(player)) {
            return Result.fail(Messages.get(FactionText.YOU_ARE_BANNED, "faction", f.tag()));
        }
        boolean direct = f.setting("open", false) || f.hasValidInvite(player, Time.now())
                || f.rosterRank(playerName) != null;
        if (direct) {
            return join(player, playerName, tag);
        }
        if (f.requests().contains(player)) {
            return Result.fail(Messages.get(FactionText.ALREADY_ASKED, "faction", f.tag()));
        }
        f.addRequest(player);
        s.audit(f, player, playerName, "member", Messages.get(FactionText.AUDIT_ASKED, "name", playerName));
        s.toFaction(f, Msg.info(Messages.get(FactionText.ASKED_NOTICE, "name", playerName)), true);
        return Result.pass(Messages.get(FactionText.ASKED, "faction", f.tag()));
    }

    Result acceptRequest(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        synchronized (s.lock) {
            if (!f.requests().contains(target)) {
                return Result.fail(Messages.get(FactionText.NOT_ASKED));
            }
            if (s.factionOf(target) != null) {
                f.removeRequest(target);
                return Result.fail(Messages.get(FactionText.JOINED_ELSEWHERE));
            }
            if (f.isBanned(target)) {
                f.removeRequest(target);
                return Result.fail(Messages.get(FactionText.PLAYER_BANNED));
            }
            if (s.full(f)) {
                return Result.fail(Messages.get(FactionText.FULL));
            }
            s.addMember(f, target, s.nameOf(target), Rank.RECRUIT, actor, Messages.get(FactionText.JOIN_HOW_ACCEPTED, "name", s.nameOf(actor)));
        }
        s.tell(target, Msg.good(Messages.get(FactionText.REQUEST_ACCEPTED_YOU, "faction", f.tag())));
        return Result.pass(Messages.get(FactionText.ACCEPTED, "name", s.nameOf(target)));
    }

    Result declineRequest(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!f.removeRequest(target)) {
            return Result.fail(Messages.get(FactionText.NOT_ASKED));
        }
        s.audit(f, actor, "member", Messages.get(FactionText.AUDIT_DECLINED, "name", s.nameOf(target)));
        s.tell(target, Msg.warn(Messages.get(FactionText.REQUEST_DECLINED_YOU, "faction", f.tag())));
        return Result.pass(Messages.get(FactionText.DECLINED, "name", s.nameOf(target)));
    }

    Result leave(UUID player) {
        synchronized (s.lock) {
            Faction f = s.factionOf(player);
            if (f == null) {
                return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
            }
            if (player.equals(f.leader())) {
                return Result.fail(Messages.get(FactionText.LEADER_CANNOT_LEAVE));
            }
            String name = s.nameOf(player);
            String listed = memberName(f, player);
            s.removeMember(f, player, player, "leave", Messages.get(FactionText.AUDIT_LEFT, "name", name));
            dropFromRoster(f, player, listed, player);
            s.toFaction(f, Msg.warn(Messages.get(FactionText.LEFT_NOTICE, "name", name)), false);
            return Result.pass(Messages.get(FactionText.YOU_LEFT, "faction", f.tag()));
        }
    }

    Result kick(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.KICK);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        String name = s.nameOf(target);
        synchronized (s.lock) {
            Result outranks = outranks(f, actor, target);
            if (outranks != null) {
                return outranks;
            }
            String listed = memberName(f, target);
            s.removeMember(f, target, actor, "kick", Messages.get(FactionText.KICKED, "name", name));
            dropFromRoster(f, target, listed, actor);
        }
        s.toFaction(f, Msg.warn(Messages.get(FactionText.KICKED_NOTICE, "name", name, "actor", s.nameOf(actor))), false);
        s.tell(target, Msg.bad(Messages.get(FactionText.KICKED_YOU, "faction", f.tag())));
        return Result.pass(Messages.get(FactionText.KICKED, "name", name));
    }

    Result ban(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.KICK);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (target.equals(actor)) {
            return Result.fail(Messages.get(FactionText.BAN_SELF));
        }
        if (f.isBanned(target)) {
            return Result.fail(Messages.get(FactionText.ALREADY_BANNED));
        }
        String name = s.nameOf(target);
        synchronized (s.lock) {
            if (f.isMember(target)) {
                Result outranks = outranks(f, actor, target);
                if (outranks != null) {
                    return outranks;
                }
                String listed = memberName(f, target);
                s.removeMember(f, target, actor, "ban", Messages.get(FactionText.AUDIT_KICKED_BAN, "name", name));
                dropFromRoster(f, target, listed, actor);
                s.tell(target, Msg.bad(Messages.get(FactionText.BANNED_YOU, "faction", f.tag())));
            }
            f.ban(target);
            f.uninvite(target);
            f.removeRequest(target);
        }
        s.audit(f, actor, "member", Messages.get(FactionText.BANNED, "name", name));
        return Result.pass(Messages.get(FactionText.BANNED, "name", name));
    }

    Result unban(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.KICK);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!f.unban(target)) {
            return Result.fail(Messages.get(FactionText.NOT_BANNED));
        }
        s.audit(f, actor, "member", Messages.get(FactionText.UNBANNED, "name", s.nameOf(target)));
        return Result.pass(Messages.get(FactionText.UNBANNED, "name", s.nameOf(target)));
    }

    /** Joins the first faction whose roster lists this name. Used on login. */
    void rosterJoin(UUID player, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        for (Faction f : s.ctx.repo().factions()) {
            if (!f.isSystem() && f.rosterRank(name) != null && !f.isBanned(player)
                    && (!s.full(f) || s.settings().bool("faction.rosterRotate"))) {
                Result r = join(player, name, f.tag());
                if (r.success()) {
                    s.tell(player, Msg.good(Messages.get(FactionText.ROSTER_ADDED_YOU, "faction", f.tag())));
                }
                return;
            }
        }
    }

    /**
     * Makes room for a roster player in a full faction by removing the member who has been offline
     * the longest, never an officer or the leader. Returns false when rotation is off or nobody
     * qualifies. Caller holds the lock.
     */
    private boolean rotate(Faction f, String incoming) {
        if (!s.settings().bool("faction.rosterRotate")) {
            return false;
        }
        Member pick = null;
        for (Member m : f.members()) {
            Rank r = f.rankOf(m.uuid());
            if (r == null || r.atLeast(Rank.OFFICER) || m.uuid().equals(f.leader()) || s.isOnline(m.uuid())) {
                continue;
            }
            if (pick == null || m.lastSeen() < pick.lastSeen()) {
                pick = m;
            }
        }
        if (pick == null) {
            return false;
        }
        UUID out = pick.uuid();
        String name = s.nameOf(out);
        s.removeMember(f, out, null, Messages.get(FactionText.SERVER_NAME), "rotation",
                Messages.get(FactionText.AUDIT_ROTATED, "name", name, "incoming", incoming));
        s.toFaction(f, Msg.warn(Messages.get(FactionText.ROTATED_NOTICE, "name", name, "incoming", incoming)), false);
        mailNote(out, Messages.get(FactionText.ROTATED_MAIL, "faction", f.tag()));
        return true;
    }

    /** Leaves a note in the market mailbox that the player sees at the next login. Skipped without a market. */
    private void mailNote(UUID player, String text) {
        MarketService market = s.ctx.serviceOrNull(MarketService.class);
        if (market == null) {
            return;
        }
        try {
            market.mailNote(player, text);
        } catch (RuntimeException e) {
            s.ctx.logger().at(java.util.logging.Level.WARNING).withCause(e).log("Dominion could not leave a mailbox note");
        }
    }

    /** The name the member record holds, which is the name a roster entry would use. */
    private String memberName(Faction f, UUID player) {
        Member m = f.member(player);
        return m == null || m.name().isBlank() ? s.nameOf(player) : m.name();
    }

    /**
     * Takes a player who left or was kicked off the roster. A roster entry joins its player again at
     * every login and lets them use join at any time, so without this a kick or a leave would not last.
     * The entry they joined through is found by uuid, so a rename since then does not keep it alive.
     */
    private void dropFromRoster(Faction f, UUID player, String listed, UUID actor) {
        for (String name : f.rosterRemovePlayer(player, listed, s.nameOf(player))) {
            s.audit(f, actor, "member", Messages.get(FactionText.ROSTER_REMOVED, "name", name));
        }
    }

    /** Null when the actor is the target's superior in the same faction. */
    static Result outranks(Faction f, UUID actor, UUID target) {
        if (target.equals(actor)) {
            return Result.fail(Messages.get(FactionText.NOT_SELF));
        }
        Rank targetRank = f.rankOf(target);
        if (targetRank == null) {
            return Result.fail(Messages.get(FactionText.NOT_IN_YOUR_FACTION));
        }
        Rank actorRank = f.rankOf(actor);
        if (actorRank == null || targetRank.ordinal() >= actorRank.ordinal()) {
            return Result.fail(Messages.get(FactionText.BELOW_RANK));
        }
        return null;
    }
}
