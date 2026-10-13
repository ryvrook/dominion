package dev.ryvrook.dominion;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.config.Defaults;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.storage.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
        JsonFiles files = new JsonFiles(getLogger());
        Settings settings = new Settings(files, getDataDirectory().resolve("config.json"));
        Repository repo = new Repository(files, getDataDirectory(), getLogger());
        ctx = new DominionContext(this, getLogger(), settings, files, repo);
        repo.loadAll(id -> new dev.ryvrook.dominion.model.PlayerData(id, "",
                settings.decimal("power.start"), settings.decimal("eco.startingBalance")));

        modules = Wiring.load(getLogger());
        for (DominionModule m : modules) {
            try {
                m.init(ctx);
            } catch (Exception e) {
                getLogger().at(Level.SEVERE).withCause(e).log("Dominion module %s failed to init", m.name());
            }
        }
        for (DominionModule m : modules) {
            try {
                m.start(ctx);
            } catch (Exception e) {
                getLogger().at(Level.SEVERE).withCause(e).log("Dominion module %s failed to start", m.name());
            }
        }

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getLogger().at(Level.INFO).log("Dominion started with %d modules", modules.size());
    }

    @Override
    protected void start() {
        var exec = HytaleServer.SCHEDULED_EXECUTOR;
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.halfSecondTasks()), 500, 500, TimeUnit.MILLISECONDS));
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.secondTasks()), 1, 1, TimeUnit.SECONDS));
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.minuteTasks()), 60, 60, TimeUnit.SECONDS));
        tasks.add(exec.scheduleAtFixedRate(() -> run(ctx.dailyTasks()), 1, 24, TimeUnit.HOURS));
        int autosave = Math.max(10, ctx.settings().integer("storage.autosaveSeconds"));
        tasks.add(exec.scheduleAtFixedRate(this::autosave, autosave, autosave, TimeUnit.SECONDS));
    }

    @Override
    protected void shutdown() {
        for (ScheduledFuture<?> t : tasks) {
            t.cancel(false);
        }
        for (DominionModule m : modules) {
            try {
                m.shutdown(ctx);
            } catch (Exception e) {
                getLogger().at(Level.WARNING).withCause(e).log("Dominion module %s failed to shut down", m.name());
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
        ctx.repo().unloadPlayer(uuid);
    }

    /** Applies a named preset. Used by the admin module and the singleplayer detection. */
    public void applyPreset(String name) {
        ctx.settings().applyPreset(Defaults.preset(name));
    }
}
