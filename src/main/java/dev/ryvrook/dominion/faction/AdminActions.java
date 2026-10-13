package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
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
        return adminName == null || adminName.isBlank() ? "An admin" : adminName;
    }

    Result forceJoin(UUID admin, String adminName, UUID target, String targetName, Faction faction) {
        if (target == null) {
            return Result.fail("That player is not known");
        }
        if (faction == null) {
            return Result.fail("That faction does not exist");
        }
        if (faction.isSystem()) {
            return Result.fail("Players cannot join server zones");
        }
        String who = admin(adminName);
        String name = targetName == null || targetName.isBlank() ? s.nameOf(target) : targetName;
        synchronized (s.lock) {
            if (s.ctx.repo().faction(faction.id()) == null) {
                return Result.fail("That faction no longer exists");
            }
            Faction old = s.factionOf(target);
            if (old != null && old.id().equals(faction.id())) {
                return Result.fail(name + " is already in " + faction.tag());
            }
            if (old != null) {
                if (target.equals(old.leader())) {
                    return Result.fail(name + " leads " + old.tag() + ". Give that faction a new leader or disband it first");
                }
                s.removeMember(old, target, admin, who, "admin", who + " moved " + name + " to " + faction.tag());
                s.toFaction(old, Msg.warn(name + " was moved to another faction by an admin"), false);
            }
            if (faction.isBanned(target)) {
                faction.unban(target);
            }
            s.addMember(faction, target, name, Rank.RECRUIT, admin, who, "through " + who);
        }
        s.tell(target, Msg.info("An admin added you to " + faction.tag()));
        return Result.pass(name + " is now a member of " + faction.tag());
    }

    Result forceKick(UUID admin, String adminName, UUID target) {
        if (target == null) {
            return Result.fail("That player is not known");
        }
        String who = admin(adminName);
        Faction f;
        String name = s.nameOf(target);
        synchronized (s.lock) {
            f = s.factionOf(target);
            if (f == null) {
                return Result.fail(name + " is not in a faction");
            }
            if (target.equals(f.leader())) {
                Member next = successor(f, target);
                if (next == null) {
                    return Result.fail(name + " is the only member of " + f.tag() + ". Disband it instead");
                }
                Ranks.moveLeadership(f, next.uuid());
                s.audit(f, admin, who, "rank", who + " made " + s.nameOf(next.uuid()) + " the leader");
                s.toFaction(f, Msg.info(s.nameOf(next.uuid()) + " now leads the faction"), false);
            }
            s.removeMember(f, target, admin, who, "kick", who + " removed " + name);
        }
        s.changed(f);
        s.toFaction(f, Msg.warn(name + " was removed by an admin"), false);
        s.tell(target, Msg.bad("An admin removed you from " + f.tag()));
        return Result.pass("Removed " + name + " from " + f.tag());
    }

    Result forceLeader(UUID admin, String adminName, Faction faction, UUID target) {
        if (faction == null) {
            return Result.fail("That faction does not exist");
        }
        if (target == null) {
            return Result.fail("That player is not known");
        }
        String who = admin(adminName);
        String name = s.nameOf(target);
        synchronized (s.lock) {
            if (s.ctx.repo().faction(faction.id()) == null) {
                return Result.fail("That faction no longer exists");
            }
            if (!faction.isMember(target)) {
                return Result.fail(name + " is not a member of " + faction.tag());
            }
            if (target.equals(faction.leader())) {
                return Result.fail(name + " already leads " + faction.tag());
            }
            Ranks.moveLeadership(faction, target);
            s.audit(faction, admin, who, "rank", who + " made " + name + " the leader");
        }
        s.changed(faction);
        s.toFaction(faction, Msg.info(name + " is now the leader of " + faction.tag()), false);
        return Result.pass(name + " now leads " + faction.tag());
    }

    Result forceDisband(UUID admin, String adminName, Faction faction) {
        if (faction == null) {
            return Result.fail("That faction does not exist");
        }
        String who = admin(adminName);
        synchronized (s.lock) {
            if (s.ctx.repo().faction(faction.id()) == null) {
                return Result.fail("That faction no longer exists");
            }
            // The shared disband drains the bank atomically and pays it to the leader once the faction is removed.
            s.disband(faction, admin, who, who + " disbanded it");
        }
        return Result.pass("Disbanded " + faction.tag());
    }

    Result forceRename(UUID admin, String adminName, Faction faction, String tag, String name) {
        if (faction == null) {
            return Result.fail("That faction does not exist");
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
