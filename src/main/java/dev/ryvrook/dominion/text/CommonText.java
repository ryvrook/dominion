package dev.ryvrook.dominion.text;

/** Sentences shared by several packages. Package specific text lives in that package's text class. */
public final class CommonText {
    private CommonText() {}

    public static final String NOT_IN_FACTION = Messages.define("common.notInFaction", "You are not in a faction.");
    public static final String POSITION_UNKNOWN = Messages.define("common.positionUnknown", "Your position is not known yet.");
    public static final String NO_SUCH_FACTION = Messages.define("common.noSuchFaction", "That faction does not exist.");
    public static final String NO_SUCH_PLAYER = Messages.define("common.noSuchPlayer", "No player called {name} is known.");
    public static final String WORLD_UNKNOWN = Messages.define("common.worldUnknown", "That world is not known.");
    public static final String FEATURE_OFF = Messages.define("common.featureOff", "That feature is turned off on this server.");
    public static final String NO_PERMISSION = Messages.define("common.noPermission", "You do not have permission to do that.");
    public static final String MESSAGES_RELOADED = Messages.define("common.messagesReloaded", "Messages reloaded from messages.json, {customised} customised.");
}
