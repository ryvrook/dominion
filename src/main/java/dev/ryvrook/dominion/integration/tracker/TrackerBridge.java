package dev.ryvrook.dominion.integration.tracker;

import dev.ryvrook.dominion.integration.Host;
import dev.ryvrook.dominion.integration.Link;
import dev.ryvrook.dominion.integration.StatKeys;
import dev.ryvrook.dominion.integration.StatSink;
import dev.ryvrook.dominion.progress.GatherFeed;
import dev.ryvrook.trackerapi.api.Delivery;
import dev.ryvrook.trackerapi.api.EventKind;
import dev.ryvrook.trackerapi.api.Subscription;
import dev.ryvrook.trackerapi.api.TrackerApi;
import dev.ryvrook.trackerapi.api.TrackerEvent;
import dev.ryvrook.trackerapi.api.event.BlockBroken;
import dev.ryvrook.trackerapi.api.event.BlockPlaced;
import dev.ryvrook.trackerapi.api.event.ItemDropped;
import dev.ryvrook.trackerapi.api.event.ItemPickedUp;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Publishes Dominion's faction counters to TrackerAPI and takes the block and item events the gather missions
 * count from it. The only class of Dominion that uses TrackerAPI types, loaded by name from the integration module
 * once the plugin manager listed TrackerAPI.
 *
 * <p>{@link #install} adds the TrackerAPI enable and disable hooks. The enable hook runs at once when TrackerAPI is
 * already up and again whenever it starts, and asks for the leaderboards. Each credit is one
 * {@code Counters.add}, a memory write that is safe from any thread, so the world thread that published the
 * Dominion event does nothing more. {@link #stop} sets the stopped flag first and removes both hooks, so
 * TrackerAPI never calls into a closed Dominion loader.
 *
 * <p>Gather missions. When TrackerAPI tracks block breaks, block places, item pickups and item drops, the bridge
 * subscribes to those four kinds and hands them to {@link GatherFeed}, which then turns Dominion's own four systems
 * off. A kind turned off in TrackerAPI's config keeps the own systems on. Deaths, kills and the land protection
 * stay on Dominion's own systems, see docs/DEVELOPMENT.md.
 */
public final class TrackerBridge implements Link, StatSink {
    private final Host host;
    private final Consumer<TrackerApi> enableHook = this::bind;
    private final Runnable disableHook = this::unbind;
    private volatile TrackerApi api;
    private volatile String version;
    private volatile boolean stopped;
    private volatile boolean warned;
    private volatile Subscription gather;

    /** The kinds the gather missions read. All four must be tracked, or the own systems keep counting. */
    static final Set<EventKind> GATHER = EnumSet.of(EventKind.BLOCK_BROKEN, EventKind.BLOCK_PLACED,
            EventKind.ITEM_PICKED_UP, EventKind.ITEM_DROPPED);

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
        boolean feeds = subscribeGather(live);
        host.info("Dominion is bound to TrackerAPI " + version + " and publishes " + StatKeys.ALL.size()
                + " faction counters under " + StatKeys.PREFIX
                + (feeds ? ", the gather missions count from its events" : ""));
    }

    /** Subscribes the gather missions when all four kinds are tracked. True when TrackerAPI now feeds them. */
    private boolean subscribeGather(TrackerApi live) {
        GatherFeed feed = host.ctx().serviceOrNull(GatherFeed.class);
        if (feed == null) {
            return false;
        }
        try {
            for (EventKind k : GATHER) {
                if (!live.isTracked(k)) {
                    host.info("TrackerAPI does not track " + k.id() + ", so Dominion counts the gather missions itself");
                    return false;
                }
            }
            gather = live.subscribeKinds("Dominion", GATHER, Delivery.ASYNC, e -> gather(feed, e));
            feed.useTracker(true);
            return true;
        } catch (RuntimeException | LinkageError e) {
            host.warn(e, "Dominion could not subscribe to TrackerAPI, so it counts the gather missions itself");
            return false;
        }
    }

    /** One event on the TrackerAPI dispatch thread. */
    private void gather(GatherFeed feed, TrackerEvent event) {
        if (stopped) {
            return;
        }
        UUID player = event.header().player();
        String world = event.header().world();
        long at = event.header().at();
        switch (event) {
            case BlockPlaced e -> feed.placed(world, e.blockId() != null && !e.blockId().isEmpty() ? e.blockId() : e.itemId(),
                    e.x(), e.y(), e.z(), at);
            case BlockBroken e -> feed.broken(player, world, e.blockId(), e.x(), e.y(), e.z(), true, at);
            case ItemPickedUp e -> feed.pickedUp(player, e.itemId(), clamp(e.quantity()), at);
            // Items spilled on death are not a drop Dominion's own system sees, so they are left out here too.
            case ItemDropped e when !e.onDeath() -> feed.dropped(player, e.itemId(), clamp(e.quantity()), at);
            default -> { }
        }
    }

    /**
     * Every 5 seconds. Hands the gather missions back to Dominion's own systems while TrackerAPI holds the listener
     * suspended for running over its budget, or while one of the four kinds is turned off in its config, and takes
     * them again once both are over.
     */
    @Override
    public void poll() {
        Subscription sub = gather;
        TrackerApi a = api;
        GatherFeed feed = host.ctx().serviceOrNull(GatherFeed.class);
        if (sub == null || a == null || feed == null || stopped) {
            return;
        }
        boolean live;
        try {
            live = sub.isActive() && !sub.isSuspended();
            for (EventKind k : GATHER) {
                live &= a.isTracked(k);
            }
        } catch (RuntimeException | LinkageError e) {
            live = false;
        }
        if (live != feed.external()) {
            feed.useTracker(live);
            host.info(live ? "Dominion counts the gather missions from TrackerAPI again"
                    : "TrackerAPI paused the Dominion gather listener or stopped tracking one of its kinds, so Dominion counts them itself");
        }
    }

    private static int clamp(long quantity) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, quantity));
    }

    /** Closes the gather subscription and turns Dominion's own systems back on. */
    private void dropGather() {
        Subscription sub = gather;
        gather = null;
        GatherFeed feed = host.ctx().serviceOrNull(GatherFeed.class);
        if (feed != null) {
            feed.useTracker(false);
        }
        if (sub != null) {
            try {
                sub.close();
            } catch (RuntimeException | LinkageError ignored) {
                // TrackerAPI closes every subscription itself when it stops
            }
        }
    }

    private synchronized void unbind() {
        dropGather();
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
        dropGather();
        try {
            TrackerApi.removeHooks(enableHook, disableHook);
        } catch (RuntimeException | LinkageError ignored) {
            // TrackerAPI is already gone
        }
        api = null;
    }
}
