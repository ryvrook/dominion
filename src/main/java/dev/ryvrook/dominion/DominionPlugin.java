package dev.ryvrook.dominion;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.config.Defaults;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.storage.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Dominion, factions for Hytale. The plugin owns storage, settings, the module list and the
 * schedulers. Everything else lives in modules.
 */
public final class DominionPlugin extends JavaPlugin {
    private static DominionPlugin instance;

    private DominionContext ctx;
    private List<DominionModule> modules = new ArrayList<>();
    private final Set<UUID> ready = new HashSet<>();
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
    /** Each module's state after setup, ok or the step that failed, in load order. */
    private final Map<String, String> moduleState = new LinkedHashMap<>();
    private final AtomicBoolean saving = new AtomicBoolean();
    private ExecutorService io;

    public DominionPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static DominionPlugin get() {
        return instance;
    }

    public DominionContext context() {
        return ctx;
    }

    @Override
    protected void setup() {
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Dominion-IO");
            t.setDaemon(true);
            return t;
        });
        JsonFiles files = new JsonFiles(getLogger());
        Settings settings = new Settings(files, getDataDirectory().resolve("config.json"));
        for (String problem : settings.startReport().problems()) {
            getLogger().at(Level.WARNING).log("Dominion config.json %s", problem);
        }
        Repository repo = new Repository(files, getDataDirectory(), getLogger());
        ctx = new DominionContext(this, getLogger(), settings, files, repo, io);
        repo.loadAll(ctx::newPlayer);

        modules = Wiring.load(getLogger());
        moduleState.clear();
        for (DominionModule m : modules) {
            DominionContext.HookMark mark = ctx.mark();
            try {
                m.init(ctx);
                moduleState.put(m.name(), "ok");
            } catch (Exception | LinkageError e) {
                moduleState.put(m.name(), "failed to init");
                dropHooks(m, mark);
                getLogger().at(Level.SEVERE).withCause(e).log("Dominion module %s failed to init", m.name());
            }
        }
        for (DominionModule m : modules) {
            if (!"ok".equals(moduleState.get(m.name()))) {
                continue;
            }
            DominionContext.HookMark mark = ctx.mark();
            try {
                m.start(ctx);
            } catch (Exception | LinkageError e) {
                moduleState.put(m.name(), "failed to start");
                dropHooks(m, mark);
                getLogger().at(Level.SEVERE).withCause(e).log("Dominion module %s failed to start", m.name());
            }
        }

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        long failed = moduleState.values().stream().filter(v -> !"ok".equals(v)).count();
        if (failed == 0) {
            getLogger().at(Level.INFO).log("Dominion started with %d modules", modules.size());
        } else {
            getLogger().at(Level.WARNING).log("Dominion started with %d modules and %d of them failed, see the errors above",
                    modules.size(), failed);
        }
    }

    /**
     * Drops the timers and player hooks a failing module added in the step that failed, so half set
     * up code never runs on a timer. Commands, systems and bus listeners it already registered with
     * the engine cannot be taken back and are left as they are.
     */
    private void dropHooks(DominionModule m, DominionContext.HookMark mark) {
        int dropped = ctx.rollback(mark);
        if (dropped > 0) {
            getLogger().at(Level.WARNING).log("Dominion dropped %d timers and hooks of the failed module %s", dropped, m.name());
        }
    }

    /** Module name to ok, failed to init or failed to start, in load order. For /fa diag. */
    public Map<String, String> moduleStates() {
        return new LinkedHashMap<>(moduleState);
    }

    @Override
    protected void start() {
        var exec = HytaleServer.SCHEDULED_EXECUTOR;
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.halfSecondTasks()), 500, 500, TimeUnit.MILLISECONDS));
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.secondTasks()), 1, 1, TimeUnit.SECONDS));
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.minuteTasks()), 60, 60, TimeUnit.SECONDS));
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.dailyTasks()), 1, 24, TimeUnit.HOURS));
        int autosave = Math.max(10, ctx.settings().integer("storage.autosaveSeconds"));
        // The timer only queues the save. The file writes run on the Dominion-IO thread.
        tasks.add(exec.scheduleAtFixedRate(() -> {
            if (saving.compareAndSet(false, true)) {
                ctx.runIo("autosave", () -> {
                    try {
                        autosave();
                    } finally {
                        saving.set(false);
                    }
                });
            }
        }, autosave, autosave, TimeUnit.SECONDS));
        adoptOnlinePlayers();
    }

    /**
     * After /plugin reload the players already online never send a ready event again. They are
     * treated as ready now so their records stay loaded and the HUD and other join work runs.
     */
    private void adoptOnlinePlayers() {
        Collection<PlayerRef> online;
        try {
            Universe u = Universe.get();
            online = u == null ? List.of() : new ArrayList<>(u.getPlayers());
        } catch (RuntimeException e) {
            return;
        }
        for (PlayerRef p : online) {
            if (p != null && p.getUuid() != null) {
                markReady(p);
            }
        }
        if (!online.isEmpty()) {
            getLogger().at(Level.INFO).log("Dominion picked up %d players who were already online", online.size());
        }
    }

    @Override
    protected void shutdown() {
        for (ScheduledFuture<?> t : tasks) {
            t.cancel(false);
        }
        for (DominionModule m : modules) {
            if ("failed to init".equals(moduleState.get(m.name()))) {
                // Never set up, so there is nothing of it to shut down.
                continue;
            }
            try {
                m.shutdown(ctx);
            } catch (Throwable e) {
                getLogger().at(Level.WARNING).withCause(e).log("Dominion module %s failed to shut down", m.name());
            }
        }
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                    getLogger().at(Level.WARNING).log("Dominion IO tasks were still running at shutdown");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ctx != null) {
            ctx.repo().saveAll();
        }
    }

    private void run(List<Runnable> tasks) {
        for (Runnable r : tasks) {
            try {
                r.run();
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Dominion scheduled task failed");
            }
        }
    }

    private void autosave() {
        try {
            ctx.repo().saveDirty();
            evictOfflinePlayers();
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Dominion autosave failed");
        }
    }

    /** Player records loaded for offline work (decay, leaderboards) are saved and dropped again. */
    private void evictOfflinePlayers() {
        List<UUID> offline = new ArrayList<>();
        synchronized (ready) {
            for (dev.ryvrook.dominion.model.PlayerData p : ctx.repo().loadedPlayers()) {
                if (!ready.contains(p.uuid())) {
                    offline.add(p.uuid());
                }
            }
        }
        for (UUID uuid : offline) {
            ctx.repo().unloadPlayer(uuid);
        }
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        markReady(playerRef);
    }

    private void markReady(PlayerRef playerRef) {
        boolean first;
        synchronized (ready) {
            first = ready.add(playerRef.getUuid());
        }
        ctx.player(playerRef.getUuid()).setName(playerRef.getUsername());
        if (!first) {
            return;
        }
        for (Consumer<PlayerRef> l : ctx.readyListeners()) {
            try {
                l.accept(playerRef);
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Dominion ready listener failed");
            }
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        synchronized (ready) {
            ready.remove(uuid);
        }
        for (Consumer<UUID> l : ctx.quitListeners()) {
            try {
                l.accept(uuid);
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Dominion quit listener failed");
            }
        }
        ctx.runIo("player save", () -> {
            boolean back;
            synchronized (ready) {
                back = ready.contains(uuid);
            }
            // A quick rejoin keeps the record loaded.
            if (!back) {
                ctx.repo().unloadPlayer(uuid);
            }
        });
    }

    /**
     * Applies a named preset. Used by the admin module and the singleplayer detection. Returns
     * false and changes nothing for a name that is not a preset.
     */
    public boolean applyPreset(String name) {
        if (!Defaults.isPreset(name)) {
            return false;
        }
        List<String> refused = ctx.settings().applyPreset(Defaults.preset(name));
        if (!refused.isEmpty()) {
            getLogger().at(Level.WARNING).log("Dominion preset %s skipped %s", name, String.join(", ", refused));
        }
        return true;
    }
}
