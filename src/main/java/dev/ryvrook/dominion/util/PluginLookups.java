package dev.ryvrook.dominion.util;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Every plugin manager lookup Dominion makes off a world thread and outside its own setup, start and shutdown goes
 * through here, behind one {@link GatedLookup}. The manager's private {@code lock} field, a ReentrantReadWriteLock in
 * 0.7.1, is read by reflection. When the field is not there the lookups are not gated, as before.
 */
public final class PluginLookups {
    private static volatile Field lockField;
    private static volatile boolean lockFieldMissing;

    public static final GatedLookup ENGINE = new GatedLookup(PluginLookups::managerLock, PluginLookups::shuttingDown);

    private PluginLookups() {}

    /** One plugin, or null. Never waits, the last answer while the manager holds its write lock. */
    public static PluginBase plugin(PluginIdentifier id) {
        return ENGINE.now(id, () -> manager() == null ? null : manager().getPlugin(id), null);
    }

    /** One plugin, or null, waiting for the manager in short tries. For Dominion's own reload threads. */
    public static PluginBase pluginFresh(PluginIdentifier id) {
        return ENGINE.fresh(id, looker(id), null);
    }

    /**
     * The plain engine lookup for one plugin, built now. A reload thread builds it before it sleeps, since the old
     * plugin class loader refuses to link anything new once Dominion was reloaded meanwhile.
     */
    public static java.util.function.Supplier<PluginBase> looker(PluginIdentifier id) {
        return () -> {
            PluginManager m = manager();
            return m == null ? null : m.getPlugin(id);
        };
    }

    /** Every plugin, the last list seen while the manager holds its write lock, empty before the first look. */
    public static List<PluginBase> plugins() {
        return ENGINE.now(PluginLookups.class, () -> {
            PluginManager m = manager();
            return m == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(m.getPlugins()));
        }, List.of());
    }

    private static PluginManager manager() {
        return PluginManager.get();
    }

    private static boolean shuttingDown() {
        HytaleServer server = HytaleServer.get();
        return server != null && server.isShuttingDown();
    }

    static ReadWriteLock managerLock() {
        if (lockFieldMissing) {
            return null;
        }
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return null;
            }
            Field f = lockField;
            if (f == null) {
                f = PluginManager.class.getDeclaredField("lock");
                f.setAccessible(true);
                lockField = f;
            }
            return f.get(manager) instanceof ReadWriteLock rw ? rw : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            lockFieldMissing = true;
            return null;
        }
    }
}
