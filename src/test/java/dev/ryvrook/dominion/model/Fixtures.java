package dev.ryvrook.dominion.model;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.storage.Repository;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

/**
 * A real context over a temporary data folder, with no server behind it. Settings and the
 * repository are the production classes, so tests see the real defaults and storage rules.
 * Services are registered by each test as it needs them.
 */
public final class Fixtures implements AutoCloseable {
    /** Runs IO tasks on the calling thread so tests stay deterministic. */
    private static final ExecutorService DIRECT = new java.util.concurrent.AbstractExecutorService() {
        private volatile boolean down;
        @Override public void shutdown() { down = true; }
        @Override public java.util.List<Runnable> shutdownNow() { down = true; return java.util.List.of(); }
        @Override public boolean isShutdown() { return down; }
        @Override public boolean isTerminated() { return down; }
        @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        @Override public void execute(Runnable r) { r.run(); }
    };

    public final Path dir;
    public final DominionContext ctx;

    private Fixtures(Path dir, DominionContext ctx) {
        this.dir = dir;
        this.ctx = ctx;
    }

    public static Fixtures create() {
        try {
            Path dir = Files.createTempDirectory("dominion-test");
            HytaleLogger logger = HytaleLogger.get("DominionTest");
            JsonFiles files = new JsonFiles(logger);
            Settings settings = new Settings(files, dir.resolve("config.json"));
            Repository repo = new Repository(files, dir, logger);
            // The constructor is package private and may grow, so arguments are matched by type.
            Constructor<?> c = DominionContext.class.getDeclaredConstructors()[0];
            Class<?>[] types = c.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                args[i] = t == HytaleLogger.class ? logger : t == Settings.class ? settings : t == JsonFiles.class ? files
                        : t == Repository.class ? repo : t == ExecutorService.class ? DIRECT : null;
            }
            c.setAccessible(true);
            return new Fixtures(dir, (DominionContext) c.newInstance(args));
        } catch (IOException | ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public Settings settings() {
        return ctx.settings();
    }

    public Repository repo() {
        return ctx.repo();
    }

    public Fixtures set(String key, Object value) {
        if (!ctx.settings().set(key, value)) {
            throw new IllegalArgumentException("Unknown setting " + key);
        }
        return this;
    }

    /** A normal faction led by a new player, stored in the repository and linked from player data. */
    public Faction faction(String tag) {
        return faction(tag, UUID.randomUUID());
    }

    public Faction faction(String tag, UUID leader) {
        Faction f = new Faction(UUID.randomUUID(), tag, tag, leader, System.currentTimeMillis());
        f.putMember(new Member(leader, tag + "Lead", Rank.LEADER, System.currentTimeMillis()));
        repo().addFaction(f);
        PlayerData p = ctx.player(leader);
        p.setName(tag + "Lead");
        p.setFactionId(f.id());
        return f;
    }

    /** Adds a member with no new member hold. */
    public UUID member(Faction f, String name, Rank rank) {
        UUID id = UUID.randomUUID();
        Member m = new Member(id, name, rank, System.currentTimeMillis());
        f.putMember(m);
        PlayerData p = ctx.player(id);
        p.setName(name);
        p.setFactionId(f.id());
        return id;
    }

    /** A known player with a name and no faction. */
    public UUID player(String name) {
        UUID id = UUID.randomUUID();
        ctx.player(id).setName(name);
        return id;
    }

    @Override
    public void close() {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp folder, best effort
        }
    }
}
