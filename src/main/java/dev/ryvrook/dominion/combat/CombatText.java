package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.text.Messages;

/** Every player facing sentence of the combat package, editable in messages.json. */
public final class CombatText {
    private CombatText() {}

    public static final String STATUS_UNREADABLE = Messages.define("combat.statusUnreadable", "Dominion found CombatLog {version} but could not read its API, using its own combat tag");
    public static final String STATUS_ABSENT = Messages.define("combat.statusAbsent", "Dominion found no CombatLog, using its own combat tag");
    public static final String STATUS_OFF = Messages.define("combat.statusOff", "Dominion found CombatLog {version} but {key} is off, using its own combat tag");
    public static final String STATUS_USED = Messages.define("combat.statusUsed", "Dominion uses CombatLog {version} for combat tags");
    public static final String UNKNOWN_VERSION = Messages.define("combat.unknownVersion", "of an unknown version");
    public static final String RESPAWNED_HOME = Messages.define("combat.respawnedHome", "You respawned at your faction home.");
    public static final String SLAIN = Messages.define("combat.slain", "{victim} was slain by {killer}.");
    public static final String KILL_REWARD_REASON = Messages.define("combat.killRewardReason", "the death of {victim} at the hands of {killer}");
    public static final String KILL_REWARD = Messages.define("combat.killReward", "{killer} took {amount} from the bank of {victim}.");
    public static final String PVP_FROM_SAFEZONE = Messages.define("combat.pvpFromSafezone", "You cannot fight from inside the SafeZone.");
    public static final String PVP_IN_SAFEZONE = Messages.define("combat.pvpInSafezone", "You cannot fight in the SafeZone.");
    public static final String PVP_OWN_IN_WARZONE = Messages.define("combat.pvpOwnInWarzone", "You cannot hurt your own faction in a WarZone.");
    public static final String PVP_FACTIONLESS_OFF = Messages.define("combat.pvpFactionlessOff", "Fighting players without a faction is turned off.");
    public static final String FRIENDLY_FIRE_OFF = Messages.define("combat.friendlyFireOff", "Friendly fire is off in your faction.");
    public static final String PVP_ALLY = Messages.define("combat.pvpAlly", "You cannot hurt your allies.");
    public static final String PVP_TRUCE = Messages.define("combat.pvpTruce", "You cannot hurt factions you have a truce with.");
    public static final String PVP_NEUTRAL_OFF = Messages.define("combat.pvpNeutralOff", "Fighting neutral factions is turned off.");
    public static final String DENY_SAFEZONE = Messages.define("combat.denySafezone", "You cannot {action} in the SafeZone {zone}.");
    public static final String DENY_WARZONE = Messages.define("combat.denyWarzone", "You cannot {action} in the WarZone {zone}.");
    public static final String NOT_ON_ROSTER = Messages.define("combat.notOnRoster", "You are not on the roster of {tag} yet.");
    public static final String DENY_RANK_ZONE = Messages.define("combat.denyRankZone", "Your rank cannot {action} in the zone {zone}.");
    public static final String DENY_RANK = Messages.define("combat.denyRank", "Your rank in {tag} cannot {action} here.");
    public static final String DENY_ENEMY = Messages.define("combat.denyEnemy", "{tag} is your enemy and you cannot {action} in its land.");
    public static final String DENY_ZONE = Messages.define("combat.denyZone", "{tag} does not let you {action} in its zone {zone}.");
    public static final String DENY_ALLY_BUILD = Messages.define("combat.denyAllyBuild", "{tag} does not let allies build in its land.");
    public static final String DENY_TRUCE_USE = Messages.define("combat.denyTruceUse", "{tag} does not let truces use things in its land.");
    public static final String DENY_LAND = Messages.define("combat.denyLand", "{tag} does not let you {action} in its land.");
    public static final String VERB_BUILD = Messages.define("combat.verbBuild", "build");
    public static final String VERB_CONTAINER = Messages.define("combat.verbContainer", "open containers");
    public static final String VERB_INTERACT = Messages.define("combat.verbInteract", "use that");
    public static final String VERB_DAMAGE_ENTITY = Messages.define("combat.verbDamageEntity", "hurt creatures");
}
