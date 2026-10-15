package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.text.Messages;

/** The feed lines and map labels the web map shows, editable in messages.json. */
public final class ExportText {
    private ExportText() {}

    public static final String A_FACTION = Messages.define("export.aFaction", "A faction");
    public static final String SOMEONE = Messages.define("export.someone", "Someone");
    public static final String ITEMS = Messages.define("export.items", "items");
    public static final String COINS = Messages.define("export.coins", "{amount} coins");
    public static final String FOUNDED = Messages.define("export.founded", "The faction {faction} was founded.");
    public static final String DISBANDED = Messages.define("export.disbanded", "The faction {faction} was disbanded.");
    public static final String JOINED = Messages.define("export.joined", "{player} joined {faction}.");
    public static final String LEFT = Messages.define("export.left", "{player} left {faction}.");
    public static final String KICKED = Messages.define("export.kicked", "{player} was kicked from {faction}.");
    public static final String BANNED = Messages.define("export.banned", "{player} was banned from {faction}.");
    public static final String TRADE = Messages.define("export.trade", "{player} bought {count} {item} for {amount}.");
    public static final String UPGRADE = Messages.define("export.upgrade", "{faction} raised its {upgrade} upgrade to level {level}.");
    public static final String MISSION = Messages.define("export.mission", "{faction} completed the mission {mission}.");
    public static final String GRACE_BEGUN = Messages.define("export.graceBegun", "The grace period has begun.");
    public static final String GRACE_ENDED = Messages.define("export.graceEnded", "The grace period has ended.");
    public static final String CLAIMED = Messages.define("export.claimed", "{faction} claimed land in {world} at chunk {chunk}.");
    public static final String TOOK_LAND = Messages.define("export.tookLand", "{faction} took land from {other} in {world} at chunk {chunk}.");
    public static final String GAVE_UP = Messages.define("export.gaveUp", "{faction} gave up land in {world} at chunk {chunk}.");
    public static final String ALLIES = Messages.define("export.allies", "{a} and {b} are now allies.");
    public static final String TRUCE = Messages.define("export.truce", "{a} and {b} agreed to a truce.");
    public static final String ENEMY = Messages.define("export.enemy", "{a} declared {b} an enemy.");
    public static final String NEUTRAL = Messages.define("export.neutral", "{a} and {b} are now neutral.");
    public static final String SIEGE_BEGAN = Messages.define("export.siegeBegan", "{attacker} began a siege on {defender}.");
    public static final String SIEGE_BREACHED = Messages.define("export.siegeBreached", "{attacker} breached the defences of {defender}.");
    public static final String SIEGE_FAILED = Messages.define("export.siegeFailed", "The siege by {attacker} on {defender} failed.");
    public static final String SIEGE_DEFENDED = Messages.define("export.siegeDefended", "{defender} held off the siege by {attacker}.");
    public static final String SIEGE_EXPIRED = Messages.define("export.siegeExpired", "The siege by {attacker} on {defender} came to an end.");
    public static final String SIEGE_CHANGED = Messages.define("export.siegeChanged", "The siege by {attacker} on {defender} changed.");
    public static final String HOME_LABEL = Messages.define("export.homeLabel", "{faction} home");
    public static final String SIEGE_LABEL = Messages.define("export.siegeLabel", "{attacker} besieging {defender}");
    public static final String UNKNOWN_ATTACKER = Messages.define("export.unknownAttacker", "Unknown");
    public static final String UNKNOWN_DEFENDER = Messages.define("export.unknownDefender", "unknown");
}
