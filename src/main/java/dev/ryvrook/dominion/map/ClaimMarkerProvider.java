package dev.ryvrook.dominion.map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.util.Keys;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Draws claimed chunks, the viewer's faction home, ally homes the viewer may use and active
 * sieges on the world map. The chunk list is cached per world and rebuilt lazily when the map
 * state version changes. The siege list is rebuilt on siege phase events and at least every
 * half minute in case an event was missed. Event hills, outposts and envoys come from the events
 * service when it is installed.
 */
public final class ClaimMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final String ID = "dominion";
    /** Resolved by the client relative to Common/UI/WorldMap/MapMarkers, like vanilla Home.png. */
    static final String CLAIM_IMAGE = "Dominion_Claim.png";
    static final String HOME_IMAGE = "Dominion_Home.png";
    static final String SIEGE_IMAGE = "Dominion_Siege.png";
    static final String HILL_IMAGE = "Dominion_Hill.png";
    static final String OUTPOST_IMAGE = "Dominion_Outpost.png";
    static final String ENVOY_IMAGE = "Dominion_Envoy.png";
    private static final long EVENT_REFRESH_MS = 2_000L;
    private static final long SIEGE_REFRESH_MS = 30_000L;

    private record Cell(long index, UUID faction) {}

    private record Layer(long version, int borderAbove, List<Cell> cells) {}

    /** A siege ready to draw. The label and colour are fixed until the next rebuild. */
    private record SiegeMark(UUID id, String world, double x, double y, double z, String label, String color,
                             String attackerTag) {}

    private record SiegeLayer(long version, long builtAt, List<SiegeMark> marks) {}

    /** Event points of one world, cached briefly so every viewer does not query the events service. */
    private record EventLayer(long builtAt, List<PointOfInterest> points) {}

    private final DominionContext ctx;
    private final MapState state;
    private final Map<String, Layer> layers = new HashMap<>();
    private volatile SiegeLayer siegeLayer = new SiegeLayer(-1, 0, List.of());
    private final Map<String, EventLayer> eventLayers = new java.util.concurrent.ConcurrentHashMap<>();

    public ClaimMarkerProvider(DominionContext ctx, MapState state) {
        this.ctx = ctx;
        this.state = state;
    }

    @Override
    public void update(World world, Player player, MarkersCollector collector) {
        try {
            draw(world, player, collector);
        } catch (Exception e) {
            ctx.logger().at(Level.FINE).withCause(e).log("Dominion map markers failed in %s", world.getName());
        }
    }

    private void draw(World world, Player player, MarkersCollector collector) {
        if (!ctx.settings().bool("map.markers")) {
            return;
        }
        PlayerRef ref = viewerOf(world, player);
        if (ref == null) {
            return;
        }
        UUID viewer = ref.getUuid();
        // mapHidden hides a player from the web map. It never hides land from their own in game map.
        PlayerData data = ctx.repo().playerIfLoaded(viewer);
        Transform t = ref.getTransform();
        double y = t == null || t.getPosition() == null ? 64 : t.getPosition().y;
        String worldName = world.getName();
        UUID own = data == null ? null : data.factionId();

        int size = Keys.chunkSize();
        double half = size / 2.0;
        for (Cell cell : layer(worldName).cells()) {
            double x = Keys.minBlock(Keys.chunkX(cell.index())) + half;
            double z = Keys.minBlock(Keys.chunkZ(cell.index())) + half;
            if (!collector.isInViewDistance(x, z)) {
                continue;
            }
            Faction f = ctx.repo().faction(cell.faction());
            if (f == null) {
                continue;
            }
            MapMarkerBuilder b = new MapMarkerBuilder(MarkerIds.of("claim", worldName, cell.index()), CLAIM_IMAGE,
                    new Transform(x, y, z))
                    .withName(Message.raw(f.tag() + "  " + f.name()).color(colorOf(f)))
                    .withComponent(new TintComponent(tint(colorOf(f))))
                    .withContextMenuItem(new ContextMenuItem("Faction info", "/f who " + f.tag()));
            // No unclaim entry here. /f unclaim acts on the chunk the player stands in, not the
            // chunk that was clicked, so offering it on a distant marker would unclaim the wrong land.
            collector.add(b.build());
        }

        Faction mine = own == null ? null : ctx.repo().faction(own);
        Location home = mine == null ? null : mine.home();
        if (home != null && worldName.equals(home.world())) {
            collector.add(new MapMarkerBuilder(MarkerIds.of("home", mine.id()), HOME_IMAGE,
                    new Transform(new Vector3d(home.x(), home.y(), home.z())))
                    .withName(Message.raw(mine.tag() + " home").color(colorOf(mine)))
                    .withComponent(new TintComponent(tint(colorOf(mine))))
                    .withContextMenuItem(new ContextMenuItem("Faction info", "/f who " + mine.tag()))
                    .build());
        }

        if (mine != null) {
            drawAllyHomes(worldName, mine, collector);
        }
        drawSieges(worldName, collector);
        drawEventPoints(worldName, collector);
    }

    /** Hills, outposts and envoys from the events service when it is installed. */
    private void drawEventPoints(String worldName, MarkersCollector collector) {
        for (PointOfInterest p : eventPoints(worldName)) {
            String image = switch (p.kind() == null ? "" : p.kind().toLowerCase(java.util.Locale.ROOT)) {
                case "hill", "koth" -> HILL_IMAGE;
                case "outpost" -> OUTPOST_IMAGE;
                case "envoy" -> ENVOY_IMAGE;
                default -> null;
            };
            if (image == null || !collector.isInViewDistance(p.x(), p.z())) {
                continue;
            }
            String color = p.color() != null && p.color().matches("#[0-9a-fA-F]{6}") ? p.color() : "#ffd166";
            String label = p.label() == null || p.label().isBlank() ? p.kind() : p.label();
            MapMarkerBuilder b = new MapMarkerBuilder(MarkerIds.of("event", p.id()), image,
                    new Transform(new Vector3d(p.x(), p.y(), p.z())))
                    .withName(Message.raw(label).color(color))
                    .withComponent(new TintComponent(tint(color)));
            Faction owner = p.ownerId() == null ? null : ctx.repo().faction(p.ownerId());
            if (owner != null) {
                b.withContextMenuItem(new ContextMenuItem("Holder info", "/f who " + owner.tag()));
            }
            collector.add(b.build());
        }
    }

    private List<PointOfInterest> eventPoints(String worldName) {
        EventsService events = ctx.serviceOrNull(EventsService.class);
        if (events == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        EventLayer cached = eventLayers.get(worldName);
        if (cached != null && now - cached.builtAt() < EVENT_REFRESH_MS) {
            return cached.points();
        }
        List<PointOfInterest> points;
        try {
            List<PointOfInterest> raw = events.enabled() ? events.points(worldName) : null;
            List<PointOfInterest> live = new ArrayList<>();
            if (raw != null) {
                for (PointOfInterest p : raw) {
                    if (p != null && (p.expiresAt() <= 0 || p.expiresAt() > now)) {
                        live.add(p);
                    }
                }
            }
            points = List.copyOf(live);
        } catch (RuntimeException e) {
            points = List.of();
        }
        eventLayers.put(worldName, new EventLayer(now, points));
        return points;
    }

    /** Homes of allied factions that let allies use them, matching the home service rules. */
    private void drawAllyHomes(String worldName, Faction mine, MarkersCollector collector) {
        RelationService rs = ctx.serviceOrNull(RelationService.class);
        if (rs == null) {
            return;
        }
        for (Map.Entry<UUID, Relation> e : mine.relations().entrySet()) {
            if (e.getValue() != Relation.ALLY) {
                continue;
            }
            Faction ally = ctx.repo().faction(e.getKey());
            Location home = ally == null ? null : ally.home();
            if (home == null || !worldName.equals(home.world()) || !ally.setting("allyHome", false)
                    || !ally.relationCan(Relation.ALLY, FactionAction.USE_HOME) || rs.between(mine, ally) != Relation.ALLY) {
                continue;
            }
            collector.add(new MapMarkerBuilder(MarkerIds.of("allyhome", ally.id()), HOME_IMAGE,
                    new Transform(new Vector3d(home.x(), home.y(), home.z())))
                    .withName(Message.raw(ally.tag() + " home is open to you").color(Relation.ALLY.color()))
                    .withComponent(new TintComponent(tint(Relation.ALLY.color())))
                    .withContextMenuItem(new ContextMenuItem("Faction info", "/f who " + ally.tag()))
                    .build());
        }
    }

    private void drawSieges(String worldName, MarkersCollector collector) {
        for (SiegeMark m : siegeLayer().marks()) {
            if (!worldName.equals(m.world()) || !collector.isInViewDistance(m.x(), m.z())) {
                continue;
            }
            collector.add(new MapMarkerBuilder(MarkerIds.of("siege", m.id()), SIEGE_IMAGE,
                    new Transform(new Vector3d(m.x(), m.y(), m.z())))
                    .withName(Message.raw(m.label()).color(m.color()))
                    .withComponent(new TintComponent(tint(m.color())))
                    .withContextMenuItem(new ContextMenuItem("Attacker info", "/f who " + m.attackerTag()))
                    .build());
        }
    }

    /** Active sieges, rebuilt when a siege phase event arrives or the cache is half a minute old. */
    private SiegeLayer siegeLayer() {
        SiegeLayer cached = siegeLayer;
        long version = state.siegeVersion();
        long now = System.currentTimeMillis();
        if (cached.version() == version && now - cached.builtAt() < SIEGE_REFRESH_MS) {
            return cached;
        }
        synchronized (this) {
            cached = siegeLayer;
            if (cached.version() == version && now - cached.builtAt() < SIEGE_REFRESH_MS) {
                return cached;
            }
            SiegeService sieges = ctx.serviceOrNull(SiegeService.class);
            List<SiegeMark> marks = new ArrayList<>();
            if (sieges != null) {
                for (Siege s : sieges.active()) {
                    if (!s.active()) {
                        continue;
                    }
                    Faction attacker = ctx.repo().faction(s.attacker());
                    Faction defender = ctx.repo().faction(s.defender());
                    String a = attacker == null ? "Unknown" : attacker.tag();
                    String d = defender == null ? "Unknown" : defender.tag();
                    String phase = s.phase() == Siege.Phase.BREACHED ? "breached" : "capturing";
                    marks.add(new SiegeMark(s.id(), s.world(), s.x(), s.y(), s.z(),
                            a + " sieging " + d + ", " + phase, attacker == null ? "#ff6b6b" : colorOf(attacker), a));
                }
            }
            SiegeLayer built = new SiegeLayer(version, now, List.copyOf(marks));
            siegeLayer = built;
            return built;
        }
    }

    /** Finds the viewer's PlayerRef by entity reference, avoiding the deprecated Player accessors. */
    private static PlayerRef viewerOf(World world, Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null) {
            return null;
        }
        for (PlayerRef p : world.getPlayerRefs()) {
            if (ref.equals(p.getReference())) {
                return p;
            }
        }
        return null;
    }

    /** Chunks to draw in a world. Large factions show only their border so the map stays light. */
    private synchronized Layer layer(String world) {
        long version = state.version(world);
        int borderAbove = ctx.settings().integer("map.borderOnlyAbove");
        Layer cached = layers.get(world);
        if (cached != null && cached.version() == version && cached.borderAbove() == borderAbove) {
            return cached;
        }
        Long2ObjectMap<ClaimEntry> claims = ctx.repo().claimsIn(world);
        Map<UUID, Integer> counts = new HashMap<>();
        for (ClaimEntry e : claims.values()) {
            counts.merge(e.factionId(), 1, Integer::sum);
        }
        List<Cell> cells = new ArrayList<>();
        for (Long2ObjectMap.Entry<ClaimEntry> e : claims.long2ObjectEntrySet()) {
            UUID owner = e.getValue().factionId();
            long index = e.getLongKey();
            if (borderAbove > 0 && counts.getOrDefault(owner, 0) > borderAbove && interior(claims, index, owner)) {
                continue;
            }
            cells.add(new Cell(index, owner));
        }
        Layer layer = new Layer(version, borderAbove, List.copyOf(cells));
        layers.put(world, layer);
        return layer;
    }

    private static boolean interior(Long2ObjectMap<ClaimEntry> claims, long index, UUID owner) {
        for (long n : Keys.neighbours(index)) {
            ClaimEntry e = claims.get(n);
            if (e == null || !owner.equals(e.factionId())) {
                return false;
            }
        }
        return true;
    }

    private static String colorOf(Faction f) {
        String c = f.color();
        return c != null && c.matches("#[0-9a-fA-F]{6}") ? c : "#7fd6ff";
    }

    private static Color tint(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return new Color((byte) (rgb >> 16 & 0xff), (byte) (rgb >> 8 & 0xff), (byte) (rgb & 0xff));
    }
}
