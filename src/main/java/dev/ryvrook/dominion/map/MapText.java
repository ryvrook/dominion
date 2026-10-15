package dev.ryvrook.dominion.map;

import dev.ryvrook.dominion.text.Messages;

/** Marker names and context menu labels on the in game map, editable in messages.json. */
public final class MapText {
    private MapText() {}

    public static final String MENU_FACTION_INFO = Messages.define("map.menuFactionInfo", "Faction info");
    public static final String MENU_HOLDER_INFO = Messages.define("map.menuHolderInfo", "Holder info");
    public static final String MENU_ATTACKER_INFO = Messages.define("map.menuAttackerInfo", "Attacker info");
    public static final String HOME = Messages.define("map.home", "{faction} home");
    public static final String ALLY_HOME = Messages.define("map.allyHome", "{faction} home is open to you");
    public static final String UNKNOWN = Messages.define("map.unknown", "Unknown");
    public static final String SIEGE_CAPTURING = Messages.define("map.siegeCapturing", "{attacker} sieging {defender}, capturing");
    public static final String SIEGE_BREACHED = Messages.define("map.siegeBreached", "{attacker} sieging {defender}, breached");
}
