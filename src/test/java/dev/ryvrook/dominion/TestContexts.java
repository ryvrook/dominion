package dev.ryvrook.dominion;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.storage.JsonFiles;
import dev.ryvrook.dominion.storage.Repository;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** A Dominion context backed by a temporary folder, with no plugin and no server. */
public final class TestContexts {
    public static final HytaleLogger LOGGER = HytaleLogger.get("DominionTest");

    private TestContexts() {}

    public static DominionContext create(Path dir) {
        JsonFiles files = new JsonFiles(LOGGER);
        Settings settings = new Settings(files, dir.resolve("config.json"));
        Repository repo = new Repository(files, dir, LOGGER);
        DominionContext ctx = new DominionContext(null, LOGGER, settings, files, repo, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Dominion-IO-test");
            t.setDaemon(true);
            return t;
        }));
        repo.loadAll(ctx::newPlayer);
        return ctx;
    }

    /** Stops the context's IO thread. Call from an AfterEach. */
    public static void close(DominionContext ctx) {
        if (ctx != null) {
            ctx.io().shutdownNow();
        }
    }

    /**
     * A stand in for a service interface. Methods named in answers return the function's result for
     * the call arguments, every other method returns null, zero or false.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Function<Object[], Object> f = answers.get(method.getName());
            if (f != null) {
                return f.apply(args == null ? new Object[0] : args);
            }
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == double.class) return 0.0;
            if (r == float.class) return 0f;
            return null;
        });
    }
}
