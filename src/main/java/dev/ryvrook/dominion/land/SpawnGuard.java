package dev.ryvrook.dominion.land;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Spawn protection. The spawn point comes from the world spawn provider and is cached per world
 * for a few minutes because the lookup is asynchronous. When the point is not known yet the
 * check passes rather than blocking the caller.
 */
final class SpawnGuard {
    private static final long CACHE_MILLIS = 5 * 60_000L;

    private record Entry(CompletableFuture<Transform> spawn, long at) {}

    private final Land land;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    SpawnGuard(Land land) {
        this.land = land;
    }

    boolean near(String world, long chunk, UUID who) {
        int radius = land.settings().integer("land.spawnProtectRadius");
        if (radius <= 0) {
            return false;
        }
        World w = land.world(world);
        if (w == null) {
            return false;
        }
        Vector3d p = spawn(w, who);
        if (p == null) {
            return false;
        }
        return within(p.x, p.z, chunk, radius);
    }

    /** True when the nearest point of the chunk lies closer than the radius to the spawn point. */
    static boolean within(double spawnX, double spawnZ, long chunk, int radius) {
        if (radius <= 0) {
            return false;
        }
        int size = Keys.chunkSize();
        int minX = Keys.minBlock(Keys.chunkX(chunk));
        int minZ = Keys.minBlock(Keys.chunkZ(chunk));
        double dx = spawnX - Math.max(minX, Math.min(minX + size, spawnX));
        double dz = spawnZ - Math.max(minZ, Math.min(minZ + size, spawnZ));
        return dx * dx + dz * dz < (double) radius * radius;
    }

    private Vector3d spawn(World w, UUID who) {
        long now = Time.now();
        Entry e = cache.get(w.getName());
        if (e == null || now - e.at() > CACHE_MILLIS || e.spawn().isCompletedExceptionally()) {
            try {
                ISpawnProvider provider = w.getWorldConfig().getSpawnProvider();
                if (provider == null) {
                    return null;
                }
                e = new Entry(lookup(provider, w, who), now);
                cache.put(w.getName(), e);
            } catch (RuntimeException ex) {
                return null;
            }
        }
        Transform t = null;
        try {
            t = e.spawn().getNow(null);
            if (t == null && !w.isInThread()) {
                t = e.spawn().get(250, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            return null;
        }
        return t == null ? null : t.getPosition();
    }

    /**
     * Server 0.7 offers getSpawnPointAsync(World, UUID) and 0.6 offers getSpawnPoint(World, UUID).
     * Both are resolved by name so the plugin compiles and runs against either version.
     */
    @SuppressWarnings("unchecked")
    private static CompletableFuture<Transform> lookup(ISpawnProvider provider, World w, UUID who) {
        try {
            Method async = provider.getClass().getMethod("getSpawnPointAsync", World.class, UUID.class);
            return (CompletableFuture<Transform>) async.invoke(provider, w, who);
        } catch (NoSuchMethodException ignored) {
            // older server, try the synchronous lookup below
        } catch (ReflectiveOperationException | ClassCastException e) {
            return CompletableFuture.failedFuture(e);
        }
        try {
            Method sync = provider.getClass().getMethod("getSpawnPoint", World.class, UUID.class);
            return CompletableFuture.completedFuture((Transform) sync.invoke(provider, w, who));
        } catch (ReflectiveOperationException | ClassCastException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
