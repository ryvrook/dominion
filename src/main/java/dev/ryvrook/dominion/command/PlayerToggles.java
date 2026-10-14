package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.model.PlayerData;

/** Personal toggles shared by the commands and the panel so both say the same thing. */
public final class PlayerToggles {
    private PlayerToggles() {}

    /**
     * Hides or shows the player on the web map and returns the confirmation line. The snapshot
     * never writes the position of a hidden player and marks the row hidden for the map.
     */
    public static String webMap(PlayerData player, boolean hidden) {
        player.setMapHidden(hidden);
        return hidden ? "You are hidden from the web map. Your position is never shared there, even when the server shares player positions."
                : "You are visible on the web map again. Your position shows there only while the server shares player positions.";
    }
}
