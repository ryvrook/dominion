package dev.ryvrook.dominion.api;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds the live API instance and the enable and disable hooks of other mods. The export module sets the
 * instance during setup, the plugin runs the enable hooks once every module started and the disable hooks first
 * thing on shutdown.
 */
public final class ApiHolder {
    private static final Logger LOG = Logger.getLogger("Dominion");
    private static final List<Consumer<DominionApi>> ENABLE = new CopyOnWriteArrayList<>();
    private static final List<Runnable> DISABLE = new CopyOnWriteArrayList<>();
    private static volatile DominionApi instance;
    private static volatile DominionApi enabled;

    private ApiHolder() {}

    public static DominionApi get() {
        DominionApi api = instance;
        if (api == null) {
            throw new IllegalStateException("Dominion is not started");
        }
        return api;
    }

    /** The instance, or null before the export module started and after it stopped. */
    public static DominionApi find() {
        return instance;
    }

    public static void set(DominionApi api) {
        instance = api;
    }

    static void onEnable(Consumer<DominionApi> hook) {
        Objects.requireNonNull(hook, "hook");
        ENABLE.add(hook);
        DominionApi api = enabled;
        if (api != null) {
            run(() -> hook.accept(api));
        }
    }

    static void onDisable(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        DISABLE.add(hook);
    }

    static void removeHooks(Consumer<DominionApi> enable, Runnable disable) {
        if (enable != null) {
            ENABLE.remove(enable);
        }
        if (disable != null) {
            DISABLE.remove(disable);
        }
    }

    /** Dominion internal. Runs the enable hooks with the instance the export module set, nothing when it has none. */
    public static void enable() {
        DominionApi api = instance;
        if (api == null || enabled == api) {
            return;
        }
        enabled = api;
        for (Consumer<DominionApi> hook : ENABLE) {
            run(() -> hook.accept(api));
        }
    }

    /** Dominion internal. Runs the disable hooks once after an enable. */
    public static void disable() {
        if (enabled == null) {
            return;
        }
        enabled = null;
        for (Runnable hook : DISABLE) {
            run(hook);
        }
    }

    /** True between enable and disable. */
    public static boolean enabled() {
        return enabled != null;
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError e) {
            LOG.log(Level.WARNING, "A Dominion lifecycle hook failed", e);
        }
    }
}
