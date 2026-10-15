package dev.ryvrook.dominion.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.map.WorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Keys;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.logging.Level;

/**
 * The claims overlay on the in game world map. Each claimed chunk image gets a translucent fill in
 * the owner's colour and a darker outline on the edges that face land of another owner, so joined
 * claims read as one region. The overlay wraps each world's map generator with
 * {@link DominionWorldMap} and asks the engine to draw changed chunks again after claim and
 * faction changes.
 */
public final class MapOverlay {
    static final String SAFEZONE_COLOR = Msg.GOLD;
    static final String WARZONE_COLOR = "#e0413c";
    static final String FALLBACK_COLOR = "#7fd6ff";
    /** How many refresh ticks later a cleared batch is cleared once more, for images drawn mid change. */
    private static final int ECHO_TICKS = 2;

    /** What the overlay last drew on a chunk image. */
    record Painted(UUID faction, int rgb) {}

    private final DominionContext ctx;
    private final MapState state;
    /** World name to chunk index to what was drawn there. */
    private final Map<String, Map<Long, Painted>> painted = new ConcurrentHashMap<>();
    private final Set<String> skipped = ConcurrentHashMap.newKeySet();
    private final Deque<Map<String, LongSet>> echoes = new ArrayDeque<>();
    private String lastLook;

    public MapOverlay(DominionContext ctx, MapState state) {
        this.ctx = ctx;
        this.state = state;
    }

    // ---- install and hand back --------------------------------------------

    /** True when the generator is a Dominion wrapper, also one left over from an earlier class loader. */
    static boolean isOurs(IWorldMap generator) {
        return generator != null && generator.getClass().getName().equals(DominionWorldMap.class.getName());
    }

    /** True when the overlay is on and drawing this world's map. */
    public boolean active(World world) {
        if (!ctx.settings().bool("map.overlay")) {
            return false;
        }
        try {
            WorldMapManager manager = world.getWorldMapManager();
            return manager != null && manager.getGenerator() instanceof DominionWorldMap;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Wraps the world's map generator once. Runs the swap on the world thread. */
    public void install(World world) {
        if (world == null || !ctx.settings().bool("map.overlay")) {
            return;
        }
        try {
            WorldMapManager manager = world.getWorldMapManager();
            if (manager == null || !wrappable(world, manager)) {
                return;
            }
            world.execute(() -> swapIn(world));
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not add the claims overlay to %s yet", world.getName());
        }
    }

    private boolean wrappable(World world, WorldMapManager manager) {
        IWorldMap generator = manager.getGenerator();
        if (generator == null || isOurs(generator)) {
            return false;
        }
        WorldMapSettings settings = generator.getWorldMapSettings();
        if (settings == null || settings == WorldMapSettings.DISABLED || !manager.isWorldMapEnabled()) {
            return false;
        }
        // The manager shuts the old generator down when it swaps. Only a generator that does
        // nothing on shutdown, like the engine's chunk map, survives being wrapped. Another
        // plugin's own generator is left alone.
        try {
            if (generator.getClass().getMethod("shutdown").getDeclaringClass() != IWorldMap.class) {
                if (skipped.add(world.getName())) {
                    ctx.logger().at(Level.INFO).log("Dominion left the world map of %s alone because %s draws it",
                            world.getName(), generator.getClass().getName());
                }
                return false;
            }
        } catch (NoSuchMethodException e) {
            return false;
        }
        return true;
    }

    private void swapIn(World world) {
        try {
            WorldMapManager manager = world.getWorldMapManager();
            if (manager == null || !ctx.settings().bool("map.overlay") || !wrappable(world, manager)) {
                return;
            }
            IWorldMap previous = manager.getGenerator();
            manager.setGenerator(new DominionWorldMap(previous, this));
            state.bump(world.getName());
            ctx.logger().at(Level.INFO).log("Dominion claims overlay enabled in %s", world.getName());
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not add the claims overlay to %s", world.getName());
        }
    }

    /** Puts every wrapped generator back. Called when the plugin shuts down. */
    public void uninstallAll() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (World world : universe.getWorlds().values()) {
            try {
                WorldMapManager manager = world.getWorldMapManager();
                if (manager == null || !(manager.getGenerator() instanceof DominionWorldMap) || !world.isAlive()) {
                    continue;
                }
                world.execute(() -> {
                    try {
                        if (manager.getGenerator() instanceof DominionWorldMap ours) {
                            ours.handBack();
                            manager.setGenerator(ours.wrapped());
                        }
                    } catch (Exception e) {
                        ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not restore the world map of %s", world.getName());
                    }
                });
            } catch (Exception e) {
                ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not restore the world map of %s", world.getName());
            }
        }
    }

    // ---- drawing ------------------------------------------------------------

    /** Fill colour of a faction as 0xRRGGBB. Zones use the colours the land banners use. */
    static int rgbOf(Faction f) {
        String hex;
        if (f.type() == FactionType.SAFEZONE) {
            hex = SAFEZONE_COLOR;
        } else if (f.type() == FactionType.WARZONE) {
            hex = WARZONE_COLOR;
        } else {
            String c = f.color();
            hex = c != null && c.matches("#[0-9a-fA-F]{6}") ? c : FALLBACK_COLOR;
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    /** Edges of a chunk that face a chunk with another owner or none, as OverlayPainter bits. */
    static int edgesOf(long index, UUID owner, LongFunction<UUID> ownerAt) {
        int x = Keys.chunkX(index);
        int z = Keys.chunkZ(index);
        int edges = 0;
        if (!owner.equals(ownerAt.apply(Keys.chunk(x, z - 1)))) {
            edges |= OverlayPainter.NORTH;
        }
        if (!owner.equals(ownerAt.apply(Keys.chunk(x + 1, z)))) {
            edges |= OverlayPainter.EAST;
        }
        if (!owner.equals(ownerAt.apply(Keys.chunk(x, z + 1)))) {
            edges |= OverlayPainter.SOUTH;
        }
        if (!owner.equals(ownerAt.apply(Keys.chunk(x - 1, z)))) {
            edges |= OverlayPainter.WEST;
        }
        return edges;
    }

    /** Tints the claimed chunk images of a freshly generated batch. Never throws. */
    void decorate(World world, WorldMap map) {
        try {
            decorate(world.getName(), map.getChunks());
        } catch (Throwable e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion claims overlay failed in %s", world.getName());
        }
    }

    /** Tints the claimed images in place in the given map. Claims are read once per chunk per call. */
    void decorate(String worldName, Long2ObjectMap<MapImage> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        Map<Long, Painted> drawn = painted.computeIfAbsent(worldName, w -> new ConcurrentHashMap<>());
        if (!ctx.settings().bool("map.overlay")) {
            for (long index : chunks.keySet()) {
                drawn.remove(index);
            }
            return;
        }
        int opacity = ctx.settings().integer("map.overlayOpacity");
        int border = ctx.settings().integer("map.overlayBorder");
        Long2ObjectOpenHashMap<UUID> owners = new Long2ObjectOpenHashMap<>();
        UUID none = new UUID(0, 0);
        LongFunction<UUID> ownerAt = index -> {
            UUID known = owners.get(index);
            if (known == null) {
                ClaimEntry e = ctx.repo().claimAt(worldName, index);
                known = e == null || e.factionId() == null ? none : e.factionId();
                owners.put(index, known);
            }
            return known == none ? null : known;
        };
        Map<UUID, Faction> factions = new HashMap<>();
        Long2ObjectOpenHashMap<MapImage> replaced = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<MapImage> entry : chunks.long2ObjectEntrySet()) {
            long index = entry.getLongKey();
            MapImage image = entry.getValue();
            UUID owner = ownerAt.apply(index);
            Faction f = owner == null ? null : factions.computeIfAbsent(owner, id -> ctx.repo().faction(id));
            if (f == null || image == null || image.palette == null || image.packedIndices == null
                    || image.width <= 0 || image.height <= 0) {
                drawn.remove(index);
                continue;
            }
            try {
                int rgb = rgbOf(f);
                int edges = edgesOf(index, owner, ownerAt);
                replaced.put(index, OverlayPainter.paint(image, rgb, opacity,
                        OverlayPainter.borderPixels(border, image.width), edges));
                drawn.put(index, new Painted(owner, rgb));
            } catch (RuntimeException e) {
                ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not tint map chunk %s in %s",
                        Keys.describe(index), worldName);
            }
        }
        chunks.putAll(replaced);
    }

    // ---- refresh ------------------------------------------------------------

    /**
     * Runs once a second. Turns queued claim and faction changes into chunk sets and asks the
     * engine on each world thread to draw them again and send them to the players who see them.
     */
    public void tick() {
        String look = ctx.settings().bool("map.overlay") + "/" + ctx.settings().integer("map.overlayOpacity")
                + "/" + ctx.settings().integer("map.overlayBorder");
        if (lastLook != null && !lastLook.equals(look)) {
            redrawEverything();
            Universe universe = Universe.get();
            if (universe != null) {
                universe.getWorlds().values().forEach(this::install);
            }
        }
        lastLook = look;
        resolveFactions(state.drainFactions());
        Map<String, LongSet> batch = state.drainChunks();
        Map<String, LongSet> due = new HashMap<>();
        merge(due, batch);
        echoes.addLast(batch);
        while (echoes.size() > ECHO_TICKS) {
            merge(due, echoes.removeFirst());
        }
        if (due.isEmpty()) {
            return;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (World world : universe.getWorlds().values()) {
            LongSet chunks = due.get(world.getName());
            if (chunks == null || chunks.isEmpty() || !world.isAlive()) {
                continue;
            }
            world.execute(() -> clear(world, chunks));
        }
    }

    private static void merge(Map<String, LongSet> into, Map<String, LongSet> from) {
        for (Map.Entry<String, LongSet> e : from.entrySet()) {
            into.computeIfAbsent(e.getKey(), w -> new LongOpenHashSet()).addAll(e.getValue());
        }
    }

    /** Queues the chunks of factions whose colour changed, and of disbanded factions with their neighbours. */
    void resolveFactions(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<Long, Painted>> world : painted.entrySet()) {
            LongSet own = new LongOpenHashSet();
            for (Map.Entry<Long, Painted> e : world.getValue().entrySet()) {
                UUID id = e.getValue().faction();
                if (!ids.contains(id)) {
                    continue;
                }
                Faction f = ctx.repo().faction(id);
                if (f == null) {
                    state.markChunk(world.getKey(), e.getKey());
                } else if (rgbOf(f) != e.getValue().rgb()) {
                    own.add(e.getKey().longValue());
                }
            }
            state.markChunks(world.getKey(), own);
        }
    }

    /** Queues every claimed and every painted chunk, after the look of the overlay changed. */
    private void redrawEverything() {
        Set<String> worlds = new HashSet<>(painted.keySet());
        worlds.addAll(ctx.repo().claimWorlds());
        for (String world : worlds) {
            LongSet all = new LongOpenHashSet(ctx.repo().claimsIn(world).keySet());
            Map<Long, Painted> drawn = painted.get(world);
            if (drawn != null) {
                for (Long index : drawn.keySet()) {
                    all.add(index.longValue());
                }
            }
            state.markChunks(world, all);
        }
    }

    /** On the world thread. Drops the cached images and makes each viewer load them again. */
    private void clear(World world, LongSet chunks) {
        try {
            WorldMapManager manager = world.getWorldMapManager();
            if (manager == null) {
                return;
            }
            manager.clearImagesInChunks(chunks);
            var store = world.getEntityStore().getStore();
            for (PlayerRef ref : world.getPlayerRefs()) {
                var entity = ref.getReference();
                Player player = entity == null || !entity.isValid() ? null : store.getComponent(entity, Player.getComponentType());
                WorldMapTracker tracker = player == null ? null : player.getWorldMapTracker();
                if (tracker == null) {
                    continue;
                }
                int radius = tracker.getEffectiveViewRadius(world);
                LongSet seen = new LongOpenHashSet();
                for (long index : chunks) {
                    if (tracker.shouldBeVisible(radius, index)) {
                        seen.add(index);
                    }
                }
                if (!seen.isEmpty()) {
                    tracker.clearChunks(seen);
                }
            }
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion could not refresh map chunks in %s", world.getName());
        }
    }
}
