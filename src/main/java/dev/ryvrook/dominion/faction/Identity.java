package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Creating, disbanding and editing a faction's identity and settings. */
final class Identity {
    /** Faction toggles players may change, in display order. */
    static final List<String> SETTING_KEYS = List.of("open", "friendlyFire", "allyBuild", "truceInteract", "allyHome", "homeOnDeath", "loginAlerts");

    private final FactionSupport s;

    Identity(FactionSupport s) {
        this.s = s;
    }

    Result create(UUID actor, String actorName, String tag, String name) {
        String tagProblem = TagRules.tag(s.settings(), tag);
        if (tagProblem != null) {
            return Result.fail(tagProblem);
        }
        String nameProblem = TagRules.name(s.settings(), name);
        if (nameProblem != null) {
            return Result.fail(nameProblem);
        }
        synchronized (s.lock) {
            if (s.factionOf(actor) != null) {
                return Result.fail(Messages.get(FactionText.ALREADY_IN_FACTION));
            }
            if (s.ctx.repo().tagTaken(tag)) {
                return Result.fail(Messages.get(FactionText.TAG_TAKEN));
            }
            double cost = s.settings().decimal("eco.costCreate");
            EconomyService eco = s.economy();
            if (cost > 0 && eco != null) {
                if (eco.balance(actor) + 1e-6 < cost) {
                    return Result.fail(Messages.get(FactionText.CREATION_COSTS, "cost", eco.format(cost)));
                }
                Result paid = eco.withdraw(actor, cost, Messages.get(FactionText.LEDGER_CREATION));
                if (paid.failed()) {
                    return paid;
                }
            }
            long now = Time.now();
            Faction f = new Faction(UUID.randomUUID(), tag, name == null || name.isBlank() ? tag : name.strip(), actor, now);
            applyDefaultSettings(f);
            Member leader = new Member(actor, actorName, Rank.LEADER, now);
            f.putMember(leader);
            s.ctx.repo().addFaction(f);
            PlayerData p = s.ctx.player(actor);
            p.setName(actorName);
            p.setFactionId(f.id());
            s.audit(f, actor, actorName, "faction", Messages.get(FactionText.CREATED, "tag", tag));
            s.publish(new DominionEvent.FactionCreated(f.id()));
            s.publish(new DominionEvent.MemberJoined(f.id(), actor));
        }
        return Result.pass(Messages.get(FactionText.CREATED, "tag", tag));
    }

    private void applyDefaultSettings(Faction f) {
        f.setSetting("open", false);
        f.setSetting("friendlyFire", s.settings().bool("combat.friendlyFireDefault"));
        boolean allyBuild = s.settings().bool("relation.allyBuildDefault");
        f.setSetting("allyBuild", allyBuild);
        f.setRelationPerm(Relation.ALLY, FactionAction.BUILD, allyBuild);
        boolean truceInteract = s.settings().bool("relation.truceInteractDefault");
        f.setSetting("truceInteract", truceInteract);
        f.setRelationPerm(Relation.TRUCE, FactionAction.INTERACT, truceInteract);
        f.setSetting("allyHome", true);
        f.setSetting("homeOnDeath", s.settings().bool("home.respawnAtHomeDefault"));
        f.setSetting("loginAlerts", s.settings().bool("chat.loginAlerts"));
    }

    Result disband(UUID actor, boolean confirmed) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.DISBAND);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        if (f.isSystem()) {
            return Result.fail(Messages.get(FactionText.ZONES_NO_DISBAND));
        }
        if (!confirmed) {
            return Result.fail(Messages.get(FactionText.CONFIRM_DISBAND, "faction", f.tag()));
        }
        synchronized (s.lock) {
            if (s.ctx.repo().faction(f.id()) == null) {
                return Result.fail(Messages.get(FactionText.NO_LONGER_EXISTS));
            }
            s.disband(f, actor, Messages.get(FactionText.DISBAND_REASON_BY, "name", s.nameOf(actor)));
        }
        return Result.pass(Messages.get(FactionText.DISBANDED, "faction", f.tag()));
    }

    Result rename(UUID actor, String newTag, String newName) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        return applyRename(g.faction(), actor, s.nameOf(actor), newTag, newName);
    }

    /** Validates and applies a new tag and name without a rank check. Audits under the given name. */
    Result applyRename(Faction f, UUID who, String whoName, String newTag, String newName) {
        boolean tagChange = newTag != null && !newTag.isBlank() && !newTag.equals(f.tag());
        boolean nameChange = newName != null && !newName.isBlank() && !newName.strip().equals(f.name());
        if (!tagChange && !nameChange) {
            return Result.fail(Messages.get(FactionText.NOTHING_TO_CHANGE));
        }
        if (nameChange) {
            String problem = TagRules.name(s.settings(), newName);
            if (problem != null) {
                return Result.fail(problem);
            }
        }
        String oldTag;
        String oldName;
        synchronized (s.lock) {
            if (s.ctx.repo().faction(f.id()) == null) {
                return Result.fail(Messages.get(FactionText.NO_LONGER_EXISTS));
            }
            oldTag = f.tag();
            oldName = f.name();
            if (tagChange) {
                String problem = TagRules.tag(s.settings(), newTag);
                if (problem != null) {
                    return Result.fail(problem);
                }
                boolean sameIgnoringCase = newTag.equalsIgnoreCase(f.tag());
                if (!sameIgnoringCase && s.ctx.repo().tagTaken(newTag)) {
                    return Result.fail(Messages.get(FactionText.TAG_TAKEN));
                }
                String old = f.tag();
                s.ctx.repo().retag(f, newTag);
                s.audit(f, who, whoName, "settings", Messages.get(FactionText.AUDIT_TAG_CHANGED, "old", old, "tag", newTag));
            }
            if (nameChange) {
                f.setName(newName.strip());
                s.audit(f, who, whoName, "settings", Messages.get(FactionText.AUDIT_NAME_CHANGED, "name", newName.strip()));
            }
        }
        s.changed(f);
        s.publish(new DominionEvent.FactionRenamed(f.id(), oldTag, f.tag(), oldName, f.name()));
        return Result.pass(Messages.get(FactionText.IDENTITY_UPDATED));
    }

    Result setDescription(UUID actor, String description) {
        return setText(actor, description, TagRules.DESCRIPTION_MAX, Messages.get(FactionText.LABEL_DESCRIPTIONS), true);
    }

    Result setMotd(UUID actor, String motd) {
        return setText(actor, motd, TagRules.MOTD_MAX, Messages.get(FactionText.LABEL_MOTDS), false);
    }

    private Result setText(UUID actor, String value, int max, String what, boolean description) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        String clean = value == null ? "" : value.strip();
        String problem = TagRules.text(clean, max, what);
        if (problem != null) {
            return Result.fail(problem);
        }
        Faction f = g.faction();
        if (description) {
            f.setDescription(clean);
            s.audit(f, actor, "settings", Messages.get(clean.isEmpty() ? FactionText.AUDIT_DESCRIPTION_CLEARED : FactionText.AUDIT_DESCRIPTION_CHANGED));
        } else {
            f.setMotd(clean);
            s.audit(f, actor, "settings", Messages.get(clean.isEmpty() ? FactionText.AUDIT_MOTD_CLEARED : FactionText.AUDIT_MOTD_CHANGED));
        }
        s.changed(f);
        return Result.pass(Messages.get(description ? FactionText.DESCRIPTION_UPDATED : FactionText.MOTD_UPDATED));
    }

    Result setColor(UUID actor, String color) {
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        String problem = TagRules.color(color);
        if (problem != null) {
            return Result.fail(problem);
        }
        Faction f = g.faction();
        String hex = TagRules.normaliseColor(color);
        f.setColor(hex);
        s.audit(f, actor, "settings", Messages.get(FactionText.AUDIT_COLOUR, "colour", hex));
        s.changed(f);
        return Result.pass(Messages.get(FactionText.COLOUR_UPDATED));
    }

    Result setSetting(UUID actor, String key, boolean value) {
        String canonical = null;
        for (String k : SETTING_KEYS) {
            if (k.equalsIgnoreCase(key)) {
                canonical = k;
            }
        }
        if (canonical == null) {
            return Result.fail(Messages.get(FactionText.UNKNOWN_SETTING, "settings", String.join(", ", SETTING_KEYS)));
        }
        FactionSupport.Gate g = s.gate(actor, FactionAction.SETTINGS);
        if (g.blocked()) {
            return g.failure();
        }
        Faction f = g.faction();
        f.setSetting(canonical, value);
        if (canonical.equals("allyBuild")) {
            // Ally building needs both this toggle and the ally build permission, so keep them in step.
            f.setRelationPerm(Relation.ALLY, FactionAction.BUILD, value);
        }
        if (canonical.equals("truceInteract")) {
            // Truce interaction needs both this toggle and the truce interact permission, so keep them in step.
            f.setRelationPerm(Relation.TRUCE, FactionAction.INTERACT, value);
        }
        String turned = value ? FactionText.SETTING_ON : FactionText.SETTING_OFF;
        String notice = value ? FactionText.SETTING_ON_NOTICE : FactionText.SETTING_OFF_NOTICE;
        s.audit(f, actor, "settings", Messages.get(turned, "setting", canonical));
        s.toFaction(f, Msg.info(Messages.get(notice, "name", s.nameOf(actor), "setting", describe(canonical))), false);
        s.changed(f);
        return Result.pass(Messages.get(turned, "setting", describe(canonical)));
    }

    private static String describe(String key) {
        return switch (key) {
            case "open" -> Messages.get(FactionText.SETTING_OPEN);
            case "friendlyFire" -> Messages.get(FactionText.SETTING_FRIENDLY_FIRE);
            case "allyBuild" -> Messages.get(FactionText.SETTING_ALLY_BUILD);
            case "truceInteract" -> Messages.get(FactionText.SETTING_TRUCE_INTERACT);
            case "allyHome" -> Messages.get(FactionText.SETTING_ALLY_HOME);
            case "homeOnDeath" -> Messages.get(FactionText.SETTING_HOME_ON_DEATH);
            case "loginAlerts" -> Messages.get(FactionText.SETTING_LOGIN_ALERTS);
            default -> key.toLowerCase(Locale.ROOT);
        };
    }
}
