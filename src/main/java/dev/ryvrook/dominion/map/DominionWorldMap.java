package dev.ryvrook.dominion.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.map.WorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps the world map generator a world already has and tints the chunk images of claimed land
 * after the wrapped generator has drawn them. Settings, points of interest and shutdown pass
 * straight through, so the terrain, zoom and view radius stay exactly as the engine draws them.
 */
public final class DominionWorldMap implements IWorldMap {
    private final IWorldMap wrapped;
    private final MapOverlay overlay;
    private volatile boolean handingBack;

    public DominionWorldMap(IWorldMap wrapped, MapOverlay overlay) {
        this.wrapped = wrapped;
        this.overlay = overlay;
    }

    public IWorldMap wrapped() {
        return wrapped;
    }

    /** Called before the wrapped generator is put back, so shutdown does not stop it. */
    void handBack() {
        handingBack = true;
    }

    @Override
    public WorldMapSettings getWorldMapSettings() {
        return wrapped.getWorldMapSettings();
    }

    @Override
    public CompletableFuture<WorldMap> generate(World world, int width, int height, LongSet chunks) {
        CompletableFuture<WorldMap> future = wrapped.generate(world, width, height, chunks);
        if (future == null) {
            return null;
        }
        return future.thenApply(map -> {
            overlay.decorate(world, map);
            return map;
        });
    }

    @Override
    public CompletableFuture<Map<String, MapMarker>> generatePointsOfInterest(World world) {
        return wrapped.generatePointsOfInterest(world);
    }

    @Override
    public void shutdown() {
        // The manager shuts the old generator down whenever it swaps. When Dominion hands the
        // wrapped generator back it must keep running, otherwise it is a normal shutdown.
        if (!handingBack) {
            wrapped.shutdown();
        }
    }

    @Override
    public String toString() {
        return "DominionWorldMap(" + wrapped + ")";
    }
}
