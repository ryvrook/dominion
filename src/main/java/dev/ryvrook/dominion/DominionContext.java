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

    DominionContext(DominionPlugin plugin, HytaleLogger logger, Settings settings, JsonFiles files, Repository repo) {
        this.plugin = plugin;
        this.logger = logger;
        this.settings = settings;
        this.files = files;
        this.repo = repo;
    }

    public DominionPlugin plugin() { return plugin; }
    public HytaleLogger logger() { return logger; }
    public Settings settings() { return settings; }
    public JsonFiles files() { return files; }
    public Repository repo() { return repo; }
    public EventBus bus() { return bus; }

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
        return repo.player(uuid, id -> new PlayerData(id, "", settings.decimal("power.start"), settings.decimal("eco.startingBalance")));
    }

    public void onPlayerReady(Consumer<PlayerRef> listener) { readyListeners.add(listener); }
    public void onPlayerQuit(Consumer<UUID> listener) { quitListeners.add(listener); }
    public void everyHalfSecond(Runnable task) { halfSecond.add(task); }
    public void everySecond(Runnable task) { second.add(task); }
    public void everyMinute(Runnable task) { minute.add(task); }
    public void daily(Runnable task) { daily.add(task); }

    List<Consumer<PlayerRef>> readyListeners() { return readyListeners; }
    List<Consumer<UUID>> quitListeners() { return quitListeners; }
    List<Runnable> halfSecondTasks() { return halfSecond; }
    List<Runnable> secondTasks() { return second; }
    List<Runnable> minuteTasks() { return minute; }
    List<Runnable> dailyTasks() { return daily; }
}
