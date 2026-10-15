package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.text.Messages;
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
            return Result.fail(Messages.get(FactionText.PICK_RANK_PERM));
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (rank == Rank.LEADER) {
            return Result.fail(Messages.get(FactionText.LEADER_ALL_PERMS));
        }
        if (!action.rankScoped()) {
            return Result.fail(Messages.get(FactionText.PERM_NOT_RANK));
        }
        boolean leader = actor.equals(f.leader());
        Rank own = f.rankOf(actor);
        if (!leader && (own == null || rank.ordinal() >= own.ordinal())) {
            return Result.fail(Messages.get(FactionText.PERMS_BELOW_OWN));
        }
        if (!leader && allowed && !Perms.can(f, actor, action, Time.now())) {
            return Result.fail(Messages.get(FactionText.PERM_NOT_OWNED));
        }
        if (f.rankCan(rank, action) == allowed) {
            return Result.fail(already(plural(f, rank), allowed, action));
        }
        f.setRankPerm(rank, action, allowed);
        String text = changed(plural(f, rank).toLowerCase(Locale.ROOT), allowed, action);
        s.audit(f, actor, "perms", text);
        s.changed(f);
        return Result.pass(text);
    }

    Result setRelationPerm(UUID actor, Relation relation, FactionAction action, boolean allowed) {
        if (relation == null || action == null) {
            return Result.fail(Messages.get(FactionText.PICK_RELATION_PERM));
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.PERMS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (!relation.isDiplomatic()) {
            return Result.fail(Messages.get(FactionText.USE_RANK_PERMS));
        }
        if (!action.relationScoped()) {
            return Result.fail(Messages.get(FactionText.PERM_NOT_RELATION));
        }
        if (f.relationCan(relation, action) == allowed) {
            return Result.fail(already(Messages.get(FactionText.RELATION_FACTIONS, "relation", relation.displayName()), allowed, action));
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
        String text = changed(Messages.get(FactionText.RELATION_FACTIONS, "relation", relation.displayName().toLowerCase(Locale.ROOT)), allowed, action);
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
        s.audit(f, actor, "perms", Messages.get(FactionText.AUDIT_PERMS_RESET));
        s.changed(f);
        return Result.pass(Messages.get(FactionText.PERMS_RESET));
    }

    private static String plural(Faction f, Rank rank) {
        String n = f.rankName(rank);
        return n.endsWith("s") ? n : n + "s";
    }

    private static String already(String who, boolean allowed, FactionAction action) {
        return Messages.get(allowed ? FactionText.PERM_ALREADY_CAN : FactionText.PERM_ALREADY_CANNOT, "who", who, "action", verb(action));
    }

    private static String changed(String who, boolean allowed, FactionAction action) {
        return Messages.get(allowed ? FactionText.PERM_ALLOWED : FactionText.PERM_STOPPED, "who", who, "action", verb(action));
    }

    private static String verb(FactionAction action) {
        return action.description().toLowerCase(Locale.ROOT);
    }
}
