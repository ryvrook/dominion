package dev.ryvrook.dominion.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginState;

/**
 * What the engine's plugin manager knows about a sibling. Nothing here touches a sibling class, so it runs on a
 * server without any of them. The decision is made from the plugin manager rather than from the sibling APIs,
 * since those are only published in the sibling's own start.
 */
public final class Presence {
    private Presence() {}

    /**
     * One lookup. {@code plugin} is the live plugin object, used only for its identity, or null when the plugin is
     * not loaded. {@code running} is false when the engine marked it failed, disabled or shut down.
     */
    public record Found(Object plugin, String version, boolean running) {
        public static final Found MISSING = new Found(null, null, false);

        public boolean present() {
            return plugin != null;
        }
    }

    /** Looks a sibling up. The engine lookup in production, a fake in tests. */
    @FunctionalInterface
    public interface Lookup {
        Found find(Sibling sibling);
    }

    /** The engine lookup. Safe from any thread, a failed lookup counts as missing. */
    public static final Lookup ENGINE = Presence::findInEngine;

    private static Found findInEngine(Sibling sibling) {
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return Found.MISSING;
            }
            PluginBase plugin = manager.getPlugin(new PluginIdentifier(Sibling.GROUP, sibling.pluginName()));
            if (plugin == null) {
                return Found.MISSING;
            }
            String version = plugin.getManifest() == null || plugin.getManifest().getVersion() == null
                    ? "0.0.0" : plugin.getManifest().getVersion().toString();
            PluginState state = plugin.getState();
            boolean running = state != PluginState.FAILED && state != PluginState.DISABLED
                    && state != PluginState.SHUTDOWN;
            return new Found(plugin, version, running);
        } catch (RuntimeException | LinkageError e) {
            return Found.MISSING;
        }
    }
}
