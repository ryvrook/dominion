package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Optional links to TrackerAPI, Feats, Atlas and Coinage. Dominion works the same without any of them. At start each
 * sibling is looked up in the plugin manager, the setting decides, and only then is its bridge class loaded by
 * name. Chronicle Stats needs nothing here, it reads the map snapshot.
 */
public final class IntegrationModule implements DominionModule {
    private static final int WATCH_SECONDS = 5;

    private final List<Binding> bindings = new ArrayList<>();
    private StatFanout stats;
    private volatile boolean stopped;
    private volatile boolean reloading;
    private int ticks;

    public IntegrationModule() {}

    @Override
    public String name() {
        return "integration";
    }

    @Override
    public void init(DominionContext ctx) {
        ctx.register(Integrations.class, new Integrations(bindings));
    }

    @Override
    public void start(DominionContext ctx) {
        stats = new StatFanout(new ContextFacts(ctx));
        ctx.bus().subscribe(stats);
        // integrations.enabled off counts as never for every sibling, so /fa integrations still lists them as off.
        boolean all = ctx.settings().bool("integrations.enabled");
        for (Sibling sibling : Sibling.values()) {
            Mode mode = all ? Mode.parse(ctx.settings().text(sibling.settingKey())) : Mode.NEVER;
            Choice choice = Choice.decide(sibling, mode, Presence.ENGINE.find(sibling));
            Binding binding = new Binding(choice, Presence.ENGINE);
            bindings.add(binding);
            ctx.logger().at(choice.warn() ? Level.WARNING : Level.INFO).log("%s", choice.startLine());
            Host host = new Host(ctx, stats, choice);
            binding.start(name -> load(name, host), host);
        }
        ctx.everySecond(() -> watch(ctx));
    }

    /** Loads a bridge by name and calls its static install(Host). */
    static Link load(String className, Host host) {
        try {
            Class<?> type = Class.forName(className, true, IntegrationModule.class.getClassLoader());
            return (Link) type.getMethod("install", Host.class).invoke(null, host);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void watch(DominionContext ctx) {
        if (stopped || ++ticks < WATCH_SECONDS) {
            return;
        }
        ticks = 0;
        boolean relink = false;
        for (Binding b : bindings) {
            String line = b.poll();
            if (line != null) {
                ctx.logger().at(b.relink() ? Level.INFO : Level.WARNING).log("%s", line);
            }
            relink |= b.relink();
        }
        if (relink && !reloading) {
            reloading = true;
            reloadSelf(ctx);
        }
    }

    /**
     * Reloads Dominion through the plugin manager a few seconds later on a thread of its own, so the sibling finishes
     * its start first and the reload never runs inside one of Dominion's own timers it is about to stop. The reload
     * waits for a TrackerAPI or Coinage pass that is reloading the same plugins and is dropped when that pass already
     * reloaded Dominion, see {@link SelfReload}.
     */
    private static void reloadSelf(DominionContext ctx) {
        // Everything the thread needs is built here. A TrackerAPI or Coinage pass can reload Dominion while the thread
        // sleeps, and the old plugin loader then refuses to load a class it has not loaded yet.
        var plugin = ctx.plugin();
        var id = plugin.getIdentifier();
        var manager = com.hypixel.hytale.server.core.plugin.PluginManager.get();
        var lock = SelfReload.sharedLock();
        var skipped = SelfReload.Outcome.SKIPPED;
        var failed = SelfReload.Outcome.FAILED;
        java.util.function.Supplier<Object> current = () -> manager.getPlugin(id);
        java.util.function.BooleanSupplier reload = () -> manager.reload(id);
        var log = ctx.logger();
        String retry = Binding.RELOAD;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000);
                SelfReload.Outcome done = manager == null ? failed : SelfReload.run(current, plugin, reload, lock);
                if (done == skipped) {
                    log.at(Level.INFO).log("Dominion was already reloaded by another plugin, so it skips its own reload");
                } else if (done == failed) {
                    log.at(Level.WARNING).log("Dominion could not reload itself, run %s", retry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | LinkageError e) {
                log.at(Level.WARNING).withCause(e).log("Dominion could not reload itself, run %s", retry);
            }
        }, "Dominion-relink");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void shutdown(DominionContext ctx) {
        stopped = true;
        if (stats != null) {
            ctx.bus().unsubscribe(stats);
        }
        for (Binding b : bindings) {
            b.stop();
        }
    }
}
