package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.model.PlayerData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Personal toggles shared by the commands and the panel so both say the same thing. */
public final class PlayerToggles {
    private static final List<BiConsumer<UUID, Boolean>> WEB_MAP_LISTENERS = new CopyOnWriteArrayList<>();

    private PlayerToggles() {}

    /**
     * Hides or shows the player on the web map and returns the confirmation line. The snapshot
     * never writes the position of a hidden player and marks the row hidden for the map.
     */
    public static String webMap(PlayerData player, boolean hidden) {
        player.setMapHidden(hidden);
        for (BiConsumer<UUID, Boolean> l : WEB_MAP_LISTENERS) {
            try {
                l.accept(player.uuid(), hidden);
            } catch (RuntimeException | LinkageError ignored) {
                // a listener failure must never break the toggle
            }
        }
        return hidden ? "You are hidden from the web map. Your position is never shared there, even when the server shares player positions."
                : "You are visible on the web map again. Your position shows there only while the server shares player positions.";
    }

    /** Tells the listener about every web map toggle, with the player and the new hidden state. */
    public static void onWebMap(BiConsumer<UUID, Boolean> listener) {
        WEB_MAP_LISTENERS.add(listener);
    }

    public static void removeWebMapListener(BiConsumer<UUID, Boolean> listener) {
        WEB_MAP_LISTENERS.remove(listener);
    }
}
