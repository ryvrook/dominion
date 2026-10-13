package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.util.Time;

import java.util.Locale;
import java.util.UUID;

/** Editing rank and relation permissions. Every change needs the PERMS permission. */
final class PermEdits {
    private final FactionSupport s;

    PermEdits(FactionSupport s) {
        this.s = s;
    }

    Result setRankPerm(UUID actor, Rank rank, FactionAction action, boolean allowed) {
        if (rank == null || action == null) {
            return Result.fail("Pick a rank and a permission");
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (rank == Rank.LEADER) {
            return Result.fail("The leader always has every permission");
        }
        if (!action.rankScoped()) {
            return Result.fail("That permission cannot be given to a rank");
        }
        boolean leader = actor.equals(f.leader());
        Rank own = f.rankOf(actor);
        if (!leader && (own == null || rank.ordinal() >= own.ordinal())) {
            return Result.fail("You can only change permissions for ranks below your own");
        }
        if (!leader && allowed && !Perms.can(f, actor, action, Time.now())) {
            return Result.fail("You cannot grant a permission you do not have yourself");
        }
        if (f.rankCan(rank, action) == allowed) {
            return Result.fail(plural(f, rank) + (allowed ? " can already " : " already cannot ") + verb(action));
        }
        f.setRankPerm(rank, action, allowed);
        String text = (allowed ? "Allowed " : "Stopped ") + plural(f, rank).toLowerCase(Locale.ROOT)
                + (allowed ? " to " : " from being able to ") + verb(action);
        s.audit(f, actor, "perms", text);
        s.changed(f);
        return Result.pass(text);
    }

    Result setRelationPerm(UUID actor, Relation relation, FactionAction action, boolean allowed) {
        if (relation == null || action == null) {
            return Result.fail("Pick a relation and a permission");
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!relation.isDiplomatic()) {
            return Result.fail("Use rank permissions for your own members");
        }
        if (!action.relationScoped()) {
            return Result.fail("That permission cannot be given to other factions");
        }
        if (f.relationCan(relation, action) == allowed) {
            return Result.fail(relation.displayName() + " factions" + (allowed ? " can already " : " already cannot ") + verb(action));
        }
        f.setRelationPerm(relation, action, allowed);
        if (relation == Relation.ALLY && action == FactionAction.BUILD) {
            // Ally building needs both the toggle and this permission, so keep the toggle in step.
            f.setSetting("allyBuild", allowed);
        }
        if (relation == Relation.TRUCE && action == FactionAction.INTERACT) {
            // Truce interaction needs both the toggle and this permission, so keep the toggle in step.
            f.setSetting("truceInteract", allowed);
        }
        String text = (allowed ? "Allowed " : "Stopped ") + relation.displayName().toLowerCase(Locale.ROOT) + " factions"
                + (allowed ? " to " : " from being able to ") + verb(action);
        s.audit(f, actor, "perms", text);
        s.changed(f);
        return Result.pass(text);
    }

    Result resetPerms(UUID actor) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        f.applyDefaultPerms();
        boolean allyBuild = f.setting("allyBuild", s.settings().bool("relation.allyBuildDefault"));
        f.setRelationPerm(Relation.ALLY, FactionAction.BUILD, allyBuild);
        boolean truceInteract = f.setting("truceInteract", s.settings().bool("relation.truceInteractDefault"));
        f.setRelationPerm(Relation.TRUCE, FactionAction.INTERACT, truceInteract);
        s.audit(f, actor, "perms", "Reset every permission to the defaults");
        s.changed(f);
        return Result.pass("Permissions are back to the defaults");
    }

    private static String plural(Faction f, Rank rank) {
        String n = f.rankName(rank);
        return n.endsWith("s") ? n : n + "s";
    }

    private static String verb(FactionAction action) {
        return action.description().toLowerCase(Locale.ROOT);
    }
}
