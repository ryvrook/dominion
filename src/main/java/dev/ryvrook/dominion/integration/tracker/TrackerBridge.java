package dev.ryvrook.dominion.integration.tracker;

import dev.ryvrook.dominion.integration.Host;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.integration.StatKeys;
import dev.ryvrook.dominion.integration.StatSink;
import dev.ryvrook.trackerapi.api.TrackerApi;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Publishes Dominion's faction counters to TrackerAPI. The only class of Dominion that uses TrackerAPI types,
 * loaded by name from the integration module once the plugin manager listed TrackerAPI.
 *
 * <p>{@link #install} adds the TrackerAPI enable and disable hooks. The enable hook runs at once when TrackerAPI is
 * already up and again whenever it starts, and asks for the leaderboards. Each credit is one
 * {@code Counters.add}, a memory write that is safe from any thread, so the world thread that published the
 * Dominion event does nothing more. {@link #stop} sets the stopped flag first and removes both hooks, so
 * TrackerAPI never calls into a closed Dominion loader.
 */
public final class TrackerBridge implements Link, StatSink {
    private final Host host;
    private final Consumer<TrackerApi> enableHook = this::bind;
    private final Runnable disableHook = this::unbind;
    private volatile TrackerApi api;
    private volatile String version;
    private volatile boolean stopped;
    private volatile boolean warned;

    private TrackerBridge(Host host) {
        this.host = host;
    }

    /** Called by name from the integration module. */
    public static Link install(Host host) {
        TrackerBridge b = new TrackerBridge(host);
        host.stats().add(b);
        TrackerApi.onDisable(b.disableHook);
        TrackerApi.onEnable(b.enableHook);
        return b;
    }

    private synchronized void bind(TrackerApi live) {
        if (stopped || live == null || live == api) {
            return;
        }
        api = live;
        try {
            version = live.version();
        } catch (RuntimeException | LinkageError e) {
            version = host.foundVersion();
        }
        for (String key : StatKeys.BOARDS) {
            try {
                live.counters().watchBoard(key);
            } catch (RuntimeException | LinkageError ignored) {
                // a board is a bonus, the counters still count
            }
        }
        host.info("Dominion is bound to TrackerAPI " + version + " and publishes " + StatKeys.ALL.size()
                + " faction counters under " + StatKeys.PREFIX);
    }

    private synchronized void unbind() {
        if (api != null) {
            api = null;
            if (!stopped) {
                host.info("Dominion stopped publishing to TrackerAPI because it stopped");
            }
        }
    }

    @Override
    public void add(UUID player, String key, long amount) {
        TrackerApi a = api;
        if (stopped || a == null || player == null || amount <= 0) {
            return;
        }
        try {
            a.counters().add(player, key, amount);
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                host.warn(e, "TrackerAPI refused a Dominion counter, later failures are not logged");
            }
        }
    }

    @Override
    public boolean bound() {
        return !stopped && api != null;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public void stop() {
        stopped = true;
        host.stats().remove(this);
        try {
            TrackerApi.removeHooks(enableHook, disableHook);
        } catch (RuntimeException | LinkageError ignored) {
            // TrackerAPI is already gone
        }
        api = null;
    }
}
