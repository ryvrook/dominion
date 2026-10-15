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
                return Result.fail("You are already in a faction");
            }
            if (s.ctx.repo().tagTaken(tag)) {
                return Result.fail("That tag is already taken");
            }
            double cost = s.settings().decimal("eco.costCreate");
            EconomyService eco = s.economy();
            if (cost > 0 && eco != null) {
                if (eco.balance(actor) + 1e-6 < cost) {
                    return Result.fail("Creating a faction costs " + eco.format(cost));
                }
                Result paid = eco.withdraw(actor, cost, "Faction creation");
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
            s.audit(f, actor, actorName, "faction", "Created the faction " + tag);
            s.publish(new DominionEvent.FactionCreated(f.id()));
            s.publish(new DominionEvent.MemberJoined(f.id(), actor));
        }
        return Result.pass("Created the faction " + tag);
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
            return Result.fail("Server zones cannot be disbanded this way");
        }
        if (!confirmed) {
            return Result.fail("Please confirm that you want to disband " + f.tag());
        }
        synchronized (s.lock) {
            if (s.ctx.repo().faction(f.id()) == null) {
                return Result.fail("That faction no longer exists");
            }
            s.disband(f, actor, s.nameOf(actor) + " disbanded it");
        }
        return Result.pass("Disbanded " + f.tag());
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
            return Result.fail("Nothing to change");
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
                return Result.fail("That faction no longer exists");
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
                    return Result.fail("That tag is already taken");
                }
                String old = f.tag();
                s.ctx.repo().retag(f, newTag);
                s.audit(f, who, whoName, "settings", "Changed the tag from " + old + " to " + newTag);
            }
            if (nameChange) {
                f.setName(newName.strip());
                s.audit(f, who, whoName, "settings", "Changed the name to " + newName.strip());
            }
        }
        s.changed(f);
        s.publish(new DominionEvent.FactionRenamed(f.id(), oldTag, f.tag(), oldName, f.name()));
        return Result.pass("Faction identity updated");
    }

    Result setDescription(UUID actor, String description) {
        return setText(actor, description, TagRules.DESCRIPTION_MAX, "Descriptions", true);
    }

    Result setMotd(UUID actor, String motd) {
        return setText(actor, motd, TagRules.MOTD_MAX, "Messages of the day", false);
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
            s.audit(f, actor, "settings", clean.isEmpty() ? "Cleared the description" : "Changed the description");
        } else {
            f.setMotd(clean);
            s.audit(f, actor, "settings", clean.isEmpty() ? "Cleared the message of the day" : "Changed the message of the day");
        }
        s.changed(f);
        return Result.pass(description ? "Description updated" : "Message of the day updated");
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
        s.audit(f, actor, "settings", "Changed the colour to " + hex);
        s.changed(f);
        return Result.pass("Colour updated");
    }

    Result setSetting(UUID actor, String key, boolean value) {
        String canonical = null;
        for (String k : SETTING_KEYS) {
            if (k.equalsIgnoreCase(key)) {
                canonical = k;
            }
        }
        if (canonical == null) {
            return Result.fail("Unknown setting. Try " + String.join(", ", SETTING_KEYS));
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
        String state = value ? "on" : "off";
        s.audit(f, actor, "settings", "Turned " + canonical + " " + state);
        s.toFaction(f, Msg.info(s.nameOf(actor) + " turned " + describe(canonical) + " " + state), false);
        s.changed(f);
        return Result.pass("Turned " + describe(canonical) + " " + state);
    }

    private static String describe(String key) {
        return switch (key) {
            case "open" -> "open joining";
            case "friendlyFire" -> "friendly fire";
            case "allyBuild" -> "ally building";
            case "truceInteract" -> "truce interaction";
            case "allyHome" -> "ally home access";
            case "homeOnDeath" -> "respawn at home";
            case "loginAlerts" -> "login alerts";
            default -> key.toLowerCase(Locale.ROOT);
        };
    }
}
