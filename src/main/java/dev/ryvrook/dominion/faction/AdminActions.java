package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.util.UUID;

/**
 * Admin force actions. They skip rank checks, bans and size limits but keep the faction data
 * consistent, write every audit line under the admin's name and publish the usual events.
 */
final class AdminActions {
    private final FactionSupport s;
    private final Identity identity;

    AdminActions(FactionSupport s, Identity identity) {
        this.s = s;
        this.identity = identity;
    }

    private static String admin(String adminName) {
        return adminName == null || adminName.isBlank() ? Messages.get(FactionText.AN_ADMIN) : adminName;
    }

    Result forceJoin(UUID admin, String adminName, UUID target, String targetName, Faction faction) {
        if (target == null) {
            return Result.fail(Messages.get(FactionText.PLAYER_NOT_KNOWN));
        }
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        if (faction.isSystem()) {
            return Result.fail(Messages.get(FactionText.ZONES_NO_PLAYERS));
        }
        String who = admin(adminName);
        String name = targetName == null || targetName.isBlank() ? s.nameOf(target) : targetName;
        synchronized (s.lock) {
            if (s.ctx.repo().faction(faction.id()) == null) {
                return Result.fail(Messages.get(FactionText.NO_LONGER_EXISTS));
            }
            Faction old = s.factionOf(target);
            if (old != null && old.id().equals(faction.id())) {
                return Result.fail(Messages.get(FactionText.ADMIN_ALREADY_IN, "name", name, "faction", faction.tag()));
            }
            if (old != null) {
                if (target.equals(old.leader())) {
                    return Result.fail(Messages.get(FactionText.ADMIN_LEADS_OTHER, "name", name, "faction", old.tag()));
                }
                s.removeMember(old, target, admin, who, "admin", Messages.get(FactionText.AUDIT_ADMIN_MOVED, "admin", who, "name", name, "faction", faction.tag()));
                s.toFaction(old, Msg.warn(Messages.get(FactionText.ADMIN_MOVED_NOTICE, "name", name)), false);
            }
            if (faction.isBanned(target)) {
                faction.unban(target);
            }
            s.addMember(faction, target, name, Rank.RECRUIT, admin, who, Messages.get(FactionText.JOIN_HOW_ADMIN, "admin", who));
        }
        s.tell(target, Msg.info(Messages.get(FactionText.ADMIN_ADDED_YOU, "faction", faction.tag())));
        return Result.pass(Messages.get(FactionText.ADMIN_ADDED, "name", name, "faction", faction.tag()));
    }

    Result forceKick(UUID admin, String adminName, UUID target) {
        if (target == null) {
            return Result.fail(Messages.get(FactionText.PLAYER_NOT_KNOWN));
        }
        String who = admin(adminName);
        Faction f;
        String name = s.nameOf(target);
        synchronized (s.lock) {
            f = s.factionOf(target);
            if (f == null) {
                return Result.fail(Messages.get(FactionText.ADMIN_NOT_IN_FACTION, "name", name));
            }
            if (target.equals(f.leader())) {
                Member next = successor(f, target);
                if (next == null) {
                    return Result.fail(Messages.get(FactionText.ADMIN_ONLY_MEMBER, "name", name, "faction", f.tag()));
                }
                Ranks.moveLeadership(f, next.uuid());
                s.audit(f, admin, who, "rank", Messages.get(FactionText.AUDIT_MADE_LEADER, "admin", who, "name", s.nameOf(next.uuid())));
                s.toFaction(f, Msg.info(Messages.get(FactionText.NOW_LEADS_FACTION, "name", s.nameOf(next.uuid()))), false);
            }
            s.removeMember(f, target, admin, who, "kick", Messages.get(FactionText.AUDIT_ADMIN_REMOVED, "admin", who, "name", name));
        }
        s.changed(f);
        s.toFaction(f, Msg.warn(Messages.get(FactionText.ADMIN_REMOVED_NOTICE, "name", name)), false);
        s.tell(target, Msg.bad(Messages.get(FactionText.ADMIN_REMOVED_YOU, "faction", f.tag())));
        return Result.pass(Messages.get(FactionText.ADMIN_REMOVED, "name", name, "faction", f.tag()));
    }

    Result forceLeader(UUID admin, String adminName, Faction faction, UUID target) {
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        if (target == null) {
            return Result.fail(Messages.get(FactionText.PLAYER_NOT_KNOWN));
        }
        String who = admin(adminName);
        String name = s.nameOf(target);
        synchronized (s.lock) {
            if (s.ctx.repo().faction(faction.id()) == null) {
                return Result.fail(Messages.get(FactionText.NO_LONGER_EXISTS));
            }
            if (!faction.isMember(target)) {
                return Result.fail(Messages.get(FactionText.ADMIN_NOT_MEMBER, "name", name, "faction", faction.tag()));
            }
            if (target.equals(faction.leader())) {
                return Result.fail(Messages.get(FactionText.ADMIN_ALREADY_LEADS, "name", name, "faction", faction.tag()));
            }
            Ranks.moveLeadership(faction, target);
            s.audit(faction, admin, who, "rank", Messages.get(FactionText.AUDIT_MADE_LEADER, "admin", who, "name", name));
        }
        s.changed(faction);
        s.toFaction(faction, Msg.info(Messages.get(FactionText.NOW_LEADER_OF, "name", name, "faction", faction.tag())), false);
        return Result.pass(Messages.get(FactionText.ADMIN_NOW_LEADS, "name", name, "faction", faction.tag()));
    }

    Result forceDisband(UUID admin, String adminName, Faction faction) {
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        String who = admin(adminName);
        synchronized (s.lock) {
            if (s.ctx.repo().faction(faction.id()) == null) {
                return Result.fail(Messages.get(FactionText.NO_LONGER_EXISTS));
            }
            // The shared disband drains the bank atomically and pays it to the leader once the faction is removed.
            s.disband(faction, admin, who, Messages.get(FactionText.DISBAND_REASON_BY, "name", who));
        }
        return Result.pass(Messages.get(FactionText.DISBANDED, "faction", faction.tag()));
    }

    Result forceRename(UUID admin, String adminName, Faction faction, String tag, String name) {
        if (faction == null) {
            return Result.fail(Messages.get(CommonText.NO_SUCH_FACTION));
        }
        return identity.applyRename(faction, admin, admin(adminName), tag, name);
    }

    /** Highest ranked other member, the most recently seen first among equals. */
    private static Member successor(Faction f, UUID leaving) {
        Member best = null;
        for (Member m : f.members()) {
            if (m.uuid().equals(leaving)) {
                continue;
            }
            if (best == null) {
                best = m;
                continue;
            }
            int a = f.rankOf(m.uuid()).ordinal();
            int b = f.rankOf(best.uuid()).ordinal();
            if (a > b || (a == b && m.lastSeen() > best.lastSeen())) {
                best = m;
            }
        }
        return best;
    }
}
