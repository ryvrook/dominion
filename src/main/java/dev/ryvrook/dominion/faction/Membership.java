package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
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
            return Result.fail("You cannot invite yourself");
        }
        if (f.isMember(target)) {
            return Result.fail(targetName + " is already a member");
        }
        if (f.isBanned(target)) {
            return Result.fail(targetName + " is banned from your faction. Unban them first");
        }
        if (s.full(f)) {
            return Result.fail("Your faction is full");
        }
        long hours = Math.max(1, s.settings().integer("faction.inviteHours"));
        f.invite(target, Time.now() + hours * FactionSupport.HOUR);
        s.audit(f, actor, "member", "Invited " + targetName);
        s.tell(target, Msg.info(s.nameOf(actor) + " invited you to join " + f.tag() + ". Use /f join " + f.tag()
                + " within " + hours + (hours == 1 ? " hour" : " hours")));
        return Result.pass("Invited " + targetName + " to " + f.tag());
    }

    Result uninvite(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!f.uninvite(target)) {
            return Result.fail("That player has no invite");
        }
        s.audit(f, actor, "member", "Revoked the invite for " + s.nameOf(target));
        return Result.pass("Invite revoked");
    }

    Result join(UUID player, String playerName, String tag) {
        synchronized (s.lock) {
            if (s.factionOf(player) != null) {
                return Result.fail("You are already in a faction");
            }
            Faction f = s.ctx.repo().factionByTag(tag);
            if (f == null || f.isSystem()) {
                return Result.fail("No faction has that tag");
            }
            if (f.isBanned(player)) {
                return Result.fail("You are banned from " + f.tag());
            }
            Rank rosterRank = playerName == null ? null : f.roster().get(playerName.toLowerCase(Locale.ROOT));
            if (s.full(f) && !(rosterRank != null && rotate(f, playerName))) {
                return Result.fail(f.tag() + " is full");
            }
            String how;
            Rank rank = Rank.RECRUIT;
            if (rosterRank != null) {
                rank = rosterRank == Rank.LEADER ? Rank.OFFICER : rosterRank;
                how = "from the roster";
            } else if (f.hasValidInvite(player, Time.now())) {
                how = "by invite";
            } else if (f.setting("open", false)) {
                how = "as an open faction";
            } else {
                return Result.fail(f.tag() + " is invite only. Ask for an invite or send a join request");
            }
            s.addMember(f, player, playerName, rank, null, how);
            return Result.pass("You joined " + f.tag());
        }
    }

    Result requestJoin(UUID player, String playerName, String tag) {
        Faction f = s.ctx.repo().factionByTag(tag);
        if (f == null || f.isSystem()) {
            return Result.fail("No faction has that tag");
        }
        if (s.factionOf(player) != null) {
            return Result.fail("You are already in a faction");
        }
        if (f.isBanned(player)) {
            return Result.fail("You are banned from " + f.tag());
        }
        boolean direct = f.setting("open", false) || f.hasValidInvite(player, Time.now())
                || (playerName != null && f.roster().containsKey(playerName.toLowerCase(Locale.ROOT)));
        if (direct) {
            return join(player, playerName, tag);
        }
        if (f.requests().contains(player)) {
            return Result.fail("You already asked to join " + f.tag());
        }
        f.addRequest(player);
        s.audit(f, player, playerName, "member", playerName + " asked to join");
        s.toFaction(f, Msg.info(playerName + " asked to join the faction. Use /f accept " + playerName), true);
        return Result.pass("Asked to join " + f.tag());
    }

    Result acceptRequest(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        synchronized (s.lock) {
            if (!f.requests().contains(target)) {
                return Result.fail("That player has not asked to join");
            }
            if (s.factionOf(target) != null) {
                f.removeRequest(target);
                return Result.fail("That player already joined another faction");
            }
            if (f.isBanned(target)) {
                f.removeRequest(target);
                return Result.fail("That player is banned from your faction");
            }
            if (s.full(f)) {
                return Result.fail("Your faction is full");
            }
            s.addMember(f, target, s.nameOf(target), Rank.RECRUIT, actor, "after a request accepted by " + s.nameOf(actor));
        }
        s.tell(target, Msg.good("Your request to join " + f.tag() + " was accepted"));
        return Result.pass("Accepted " + s.nameOf(target));
    }

    Result declineRequest(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.INVITE);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!f.removeRequest(target)) {
            return Result.fail("That player has not asked to join");
        }
        s.audit(f, actor, "member", "Declined the request from " + s.nameOf(target));
        s.tell(target, Msg.warn("Your request to join " + f.tag() + " was declined"));
        return Result.pass("Declined " + s.nameOf(target));
    }

    Result leave(UUID player) {
        synchronized (s.lock) {
            Faction f = s.factionOf(player);
            if (f == null) {
                return Result.fail("You are not in a faction");
            }
            if (player.equals(f.leader())) {
                return Result.fail("Leaders must transfer leadership or disband first");
            }
            String name = s.nameOf(player);
            s.removeMember(f, player, player, "leave", name + " left");
            s.toFaction(f, Msg.warn(name + " left the faction"), false);
            return Result.pass("You left " + f.tag());
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
            s.removeMember(f, target, actor, "kick", "Kicked " + name);
        }
        s.toFaction(f, Msg.warn(name + " was kicked by " + s.nameOf(actor)), false);
        s.tell(target, Msg.bad("You were kicked from " + f.tag()));
        return Result.pass("Kicked " + name);
    }

    Result ban(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.KICK);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (target.equals(actor)) {
            return Result.fail("You cannot ban yourself");
        }
        if (f.isBanned(target)) {
            return Result.fail("That player is already banned");
        }
        String name = s.nameOf(target);
        synchronized (s.lock) {
            if (f.isMember(target)) {
                Result outranks = outranks(f, actor, target);
                if (outranks != null) {
                    return outranks;
                }
                s.removeMember(f, target, actor, "ban", "Kicked " + name + " with a ban");
                s.tell(target, Msg.bad("You were banned from " + f.tag()));
            }
            f.ban(target);
            f.uninvite(target);
            f.removeRequest(target);
        }
        s.audit(f, actor, "member", "Banned " + name);
        return Result.pass("Banned " + name);
    }

    Result unban(UUID actor, UUID target) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.KICK);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!f.unban(target)) {
            return Result.fail("That player is not banned");
        }
        s.audit(f, actor, "member", "Unbanned " + s.nameOf(target));
        return Result.pass("Unbanned " + s.nameOf(target));
    }

    /** Joins the first faction whose roster lists this name. Used on login. */
    void rosterJoin(UUID player, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        for (Faction f : s.ctx.repo().factions()) {
            if (!f.isSystem() && f.roster().containsKey(key) && !f.isBanned(player)
                    && (!s.full(f) || s.settings().bool("faction.rosterRotate"))) {
                Result r = join(player, name, f.tag());
                if (r.success()) {
                    s.tell(player, Msg.good("You were added to " + f.tag() + " from its roster"));
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
            if (r == null || r.atLeast(Rank.OFFICER) || m.uuid().equals(f.leader()) || Players.isOnline(m.uuid())) {
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
        s.removeMember(f, out, null, "Server", "rotation",
                name + " was rotated out to make room for " + incoming + " from the roster");
        s.toFaction(f, Msg.warn(name + " was rotated out to make room for " + incoming), false);
        mailNote(out, "You were rotated out of " + f.tag() + " to make room for a roster member");
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

    /** Null when the actor is the target's superior in the same faction. */
    static Result outranks(Faction f, UUID actor, UUID target) {
        if (target.equals(actor)) {
            return Result.fail("You cannot do that to yourself");
        }
        Rank targetRank = f.rankOf(target);
        if (targetRank == null) {
            return Result.fail("That player is not in your faction");
        }
        Rank actorRank = f.rankOf(actor);
        if (actorRank == null || targetRank.ordinal() >= actorRank.ordinal()) {
            return Result.fail("You can only manage members below your rank");
        }
        return null;
    }
}
