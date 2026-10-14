package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads combat tags from the CombatLog mod by Devify through reflection, so Dominion has no compile
 * time dependency on it. The class and its method handles are resolved once. When the class is
 * missing or its methods have another shape, the bridge is absent and answers false and zero.
 * Every call is guarded, so a future CombatLog that changes or throws never breaks Dominion.
 */
final class CombatLogBridge implements CombatTagSource {
    static final String API_CLASS = "org.combatlog.plugin.api.CombatLogApi";
    static final String PLUGIN_GROUP = "Devify";
    static final String PLUGIN_NAME = "CombatLog";

    private final MethodHandle isAvailable;
    private final MethodHandle isTagged;
    private final MethodHandle remaining;
    private final String version;
    private volatile Throwable lastFailure;
    private volatile long failures;

    /** Resolves the API class in the first of the loaders that knows it. */
    CombatLogBridge(String className, Iterable<ClassLoader> loaders, String version) {
        MethodHandle available = null;
        MethodHandle tagged = null;
        MethodHandle left = null;
        Class<?> api = load(className, loaders);
        if (api != null) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                available = lookup.findStatic(api, "isAvailable", MethodType.methodType(boolean.class));
                tagged = lookup.findStatic(api, "isTagged", MethodType.methodType(boolean.class, UUID.class));
                left = lookup.findStatic(api, "getRemainingMillis", MethodType.methodType(long.class, UUID.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                available = null;
                tagged = null;
                left = null;
                lastFailure = e;
            }
        }
        this.isAvailable = available;
        this.isTagged = tagged;
        this.remaining = left;
        this.version = version;
    }

    /** The bridge for the CombatLog that is loaded on this server, or an absent bridge. */
    static CombatLogBridge discover() {
        return new CombatLogBridge(API_CLASS, loaders(), installedVersion());
    }

    /** True when the API class and all three methods resolved. */
    @Override
    public boolean present() {
        return isTagged != null && remaining != null && isAvailable != null;
    }

    /** The version from the CombatLog manifest, or null when the plugin is not loaded. */
    @Override
    public String version() {
        return version;
    }

    /** True when the plugin is loaded according to the plugin manager, even if its API did not resolve. */
    boolean pluginLoaded() {
        return version != null;
    }

    /** CombatLog's own answer to whether it is running right now. False when absent or on any error. */
    boolean available() {
        if (isAvailable == null) {
            return false;
        }
        try {
            return (boolean) isAvailable.invokeExact();
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    @Override
    public boolean tagged(UUID player) {
        if (isTagged == null || player == null) {
            return false;
        }
        try {
            return (boolean) isTagged.invokeExact(player);
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    @Override
    public long remainingMillis(UUID player) {
        if (remaining == null || player == null) {
            return 0L;
        }
        try {
            long left = (long) remaining.invokeExact(player);
            return Math.max(0L, left);
        } catch (Throwable t) {
            failed(t);
            return 0L;
        }
    }

    /** Number of calls into CombatLog that failed since start. */
    long failures() {
        return failures;
    }

    /** The last error seen while resolving or calling CombatLog, or null. */
    Throwable lastFailure() {
        return lastFailure;
    }

    /** Errors from the other mod are swallowed. Only the virtual machine running out of room is passed on. */
    private void failed(Throwable t) {
        if (t instanceof VirtualMachineError vm) {
            throw vm;
        }
        lastFailure = t;
        failures++;
    }

    private static Class<?> load(String name, Iterable<ClassLoader> loaders) {
        if (name == null || loaders == null) {
            return null;
        }
        for (ClassLoader l : loaders) {
            try {
                return Class.forName(name, true, l);
            } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                // try the next loader
            }
        }
        return null;
    }

    /** Dominion's own loader first, then the thread's loader, then every plugin's loader. */
    private static List<ClassLoader> loaders() {
        Set<ClassLoader> set = new LinkedHashSet<>();
        set.add(CombatLogBridge.class.getClassLoader());
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            set.add(context);
        }
        try {
            PluginManager pm = PluginManager.get();
            if (pm != null) {
                for (PluginBase plugin : pm.getPlugins()) {
                    set.add(plugin.getClass().getClassLoader());
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // plugin manager not ready, the loaders above still get a try
        }
        set.remove(null);
        return new ArrayList<>(set);
    }

    private static String installedVersion() {
        try {
            PluginManager pm = PluginManager.get();
            PluginBase plugin = pm == null ? null : pm.getPlugin(new PluginIdentifier(PLUGIN_GROUP, PLUGIN_NAME));
            if (plugin == null || plugin.getManifest() == null) {
                return null;
            }
            Object v = plugin.getManifest().getVersion();
            return v == null ? "" : v.toString();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
