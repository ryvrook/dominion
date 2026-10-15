package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Optional links to TrackerAPI, Feats and Atlas. Dominion works the same without any of them. At start each
 * sibling is looked up in the plugin manager, the setting decides, and only then is its bridge class loaded by
 * name. Chronicle Stats needs nothing here, it reads the map snapshot.
 */
public final class IntegrationModule implements DominionModule {
    private static final int WATCH_SECONDS = 5;

    private final List<Binding> bindings = new ArrayList<>();
    private StatFanout stats;
    private volatile boolean stopped;
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
        for (Binding b : bindings) {
            String line = b.poll();
            if (line != null) {
                ctx.logger().at(Level.WARNING).log("%s", line);
            }
        }
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
