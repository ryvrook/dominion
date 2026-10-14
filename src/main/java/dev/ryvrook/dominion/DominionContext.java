package dev.ryvrook.dominion;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.api.EventBus;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.storage.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Shared state and service registry handed to every module. */
public final class DominionContext {
    private final DominionPlugin plugin;
    private final HytaleLogger logger;
    private final Settings settings;
    private final JsonFiles files;
    private final Repository repo;
    private final EventBus bus = new EventBus();
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final List<Consumer<PlayerRef>> readyListeners = new ArrayList<>();
    private final List<Consumer<UUID>> quitListeners = new ArrayList<>();
    private final List<Runnable> halfSecond = new ArrayList<>();
    private final List<Runnable> second = new ArrayList<>();
    private final List<Runnable> minute = new ArrayList<>();
    private final List<Runnable> daily = new ArrayList<>();
    private final ExecutorService io;

    DominionContext(DominionPlugin plugin, HytaleLogger logger, Settings settings, JsonFiles files, Repository repo,
                    ExecutorService io) {
        this.plugin = plugin;
        this.logger = logger;
        this.settings = settings;
        this.files = files;
        this.repo = repo;
        this.io = io;
    }

    public DominionPlugin plugin() { return plugin; }
    public HytaleLogger logger() { return logger; }
    public Settings settings() { return settings; }
    public JsonFiles files() { return files; }
    public Repository repo() { return repo; }
    public EventBus bus() { return bus; }

    /**
     * Dominion's own single file IO thread. Autosave, backups and the map snapshot run here so the
     * engine's shared scheduler only ever runs timers. Tasks run one at a time in submission order.
     */
    public ExecutorService io() { return io; }

    /** Runs a task on the IO thread, logging instead of throwing when it fails or the thread is gone. */
    public void runIo(String what, Runnable task) {
        try {
            io.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logger.at(java.util.logging.Level.WARNING).withCause(e).log("Dominion %s failed", what);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Shutting down. The final save covers it.
        }
    }

    public <T> void register(Class<T> type, T impl) {
        services.put(type, impl);
    }

    /** Throws if the service is missing so wiring mistakes surface at start, not at first use. */
    public <T> T service(Class<T> type) {
        Object s = services.get(type);
        if (s == null) {
            throw new IllegalStateException("Dominion service not registered " + type.getSimpleName());
        }
        return type.cast(s);
    }

    public <T> T serviceOrNull(Class<T> type) {
        Object s = services.get(type);
        return s == null ? null : type.cast(s);
    }

    public boolean has(Class<?> type) {
        return services.containsKey(type);
    }

    /** Player data, loading or creating it. Never null. */
    public PlayerData player(UUID uuid) {
        return repo.player(uuid, this::newPlayer);
    }

    /** A fresh record for a player Dominion has never seen, using the current defaults. */
    PlayerData newPlayer(UUID id) {
        PlayerData p = new PlayerData(id, "", settings.decimal("power.start"), settings.decimal("eco.startingBalance"));
        p.setHud(settings.bool("hud.enabledDefault"));
        return p;
    }

    public void onPlayerReady(Consumer<PlayerRef> listener) { readyListeners.add(listener); }
    public void onPlayerQuit(Consumer<UUID> listener) { quitListeners.add(listener); }
    public void everyHalfSecond(Runnable task) { halfSecond.add(task); }
    public void everySecond(Runnable task) { second.add(task); }
    public void everyMinute(Runnable task) { minute.add(task); }
    public void daily(Runnable task) { daily.add(task); }

    /** Sizes of every hook list, taken before a module runs so its hooks can be dropped if it fails. */
    record HookMark(int ready, int quit, int halfSecond, int second, int minute, int daily) {}

    HookMark mark() {
        return new HookMark(readyListeners.size(), quitListeners.size(), halfSecond.size(), second.size(), minute.size(), daily.size());
    }

    /**
     * Drops every timer and player hook added since the mark. Modules run one at a time during
     * setup, so these are exactly the hooks of the module that failed. Returns how many were dropped.
     */
    int rollback(HookMark m) {
        return cut(readyListeners, m.ready()) + cut(quitListeners, m.quit()) + cut(halfSecond, m.halfSecond())
                + cut(second, m.second()) + cut(minute, m.minute()) + cut(daily, m.daily());
    }

    private static int cut(List<?> list, int size) {
        int removed = 0;
        while (list.size() > size) {
            list.remove(list.size() - 1);
            removed++;
        }
        return removed;
    }

    List<Consumer<PlayerRef>> readyListeners() { return readyListeners; }
    List<Consumer<UUID>> quitListeners() { return quitListeners; }
    List<Runnable> halfSecondTasks() { return halfSecond; }
    List<Runnable> secondTasks() { return second; }
    List<Runnable> minuteTasks() { return minute; }
    List<Runnable> dailyTasks() { return daily; }
}
