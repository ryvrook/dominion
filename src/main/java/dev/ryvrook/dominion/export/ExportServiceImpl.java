package dev.ryvrook.dominion.export;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Siege;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.ExportService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.service.ShieldService;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Writes export/snapshot.json for the web map. A change counter is bumped by every bus event and
 * every two seconds while players are online, and the file is only rewritten when it moved.
 */
public final class ExportServiceImpl implements ExportService {
    private static final int SCHEMA_VERSION = 1;
    private static final int CHAT_LIMIT = 50;

    private final DominionContext ctx;
    private final MapData data;
    private final Feed feed;
    private final AtomicLong changes = new AtomicLong(1);
    private long written;
    private int secondsSinceFlush;
    private int secondsSinceMove;
    private boolean warned;
    /** Last public chat lines, oldest first. Guarded by its own monitor. */
    private final ArrayDeque<ChatLine> chat = new ArrayDeque<>(CHAT_LIMIT);
    private long lastChatAt;

    /** One public chat line as written to the snapshot. */
    record ChatLine(long at, String name, String tag, String text) {}

    ExportServiceImpl(DominionContext ctx, MapData data) {
        this.ctx = ctx;
        this.data = data;
        this.feed = new Feed(ctx);
    }

    MapData data() {
        return data;
    }

    Path file() {
        return ctx.repo().root().resolve("export").resolve("snapshot.json");
    }

    @Override
    public String snapshotPath() {
        Path p = file();
        try {
            return Path.of("").toAbsolutePath().relativize(p.toAbsolutePath()).toString();
        } catch (Exception e) {
            return p.toString();
        }
    }

    @Override
    public void recordChat(String name, String tag, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        synchronized (chat) {
            // Timestamps stay strictly increasing so a reader can track the last one it has seen.
            long at = Math.max(Time.now(), lastChatAt + 1);
            lastChatAt = at;
            chat.addLast(new ChatLine(at, name == null ? "" : name, tag == null ? "" : tag, text));
            while (chat.size() > CHAT_LIMIT) {
                chat.removeFirst();
            }
        }
        changes.incrementAndGet();
    }

    private List<ChatLine> chatLines() {
        synchronized (chat) {
            return new ArrayList<>(chat);
        }
    }

    void onEvent(DominionEvent event) {
        changes.incrementAndGet();
        feed.accept(event);
    }

    /** Runs every second from the context timer. */
    void secondTick() {
        if (!ctx.settings().bool("export.enabled")) {
            return;
        }
        if (++secondsSinceMove >= 2) {
            secondsSinceMove = 0;
            try {
                if (Universe.get().getPlayerCount() > 0) {
                    changes.incrementAndGet();
                }
            } catch (Exception ignored) {
                // universe not ready yet
            }
        }
        if (++secondsSinceFlush >= Math.max(1, ctx.settings().integer("export.intervalSeconds"))) {
            secondsSinceFlush = 0;
            flush();
        }
    }

    @Override
    public synchronized void flush() {
        long version = changes.get();
        if (version == written) {
            return;
        }
        try {
            String json = build();
            write(file(), json);
            written = version;
            warned = false;
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not write the map snapshot");
            }
        }
    }

    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---- snapshot ------------------------------------------------------------

    String build() {
        Json j = new Json();
        Collection<PlayerRef> online = onlinePlayers();
        j.beginObject();
        j.field("version", SCHEMA_VERSION);
        j.field("at", Time.now());
        j.key("server").beginObject().field("online", online.size()).endObject();

        j.key("worlds").beginArray();
        Map<UUID, Boolean> raidable = new HashMap<>();
        for (String world : data.worlds()) {
            writeWorld(j, world, online, raidable);
        }
        j.endArray();

        j.key("factions").beginArray();
        for (Faction f : ctx.repo().factions(FactionType.NORMAL)) {
            writeFaction(j, f);
        }
        j.endArray();

        writeNations(j);

        j.key("sieges").beginArray();
        for (Siege s : data.activeSieges()) {
            j.beginObject();
            j.field("id", s.id().toString());
            j.field("attacker", tagOf(s.attacker()));
            j.field("defender", tagOf(s.defender()));
            j.field("phase", s.phase().name());
            j.field("world", s.world());
            j.key("chunk").pair(Keys.chunkX(s.chunkIndex()), Keys.chunkZ(s.chunkIndex()));
            j.field("endsAt", s.phaseEndsAt());
            j.endObject();
        }
        j.endArray();

        ShieldService shield = ctx.serviceOrNull(ShieldService.class);
        RulesService rules = ctx.serviceOrNull(RulesService.class);
        boolean grace = shield != null ? shield.graceActive() : rules != null && rules.graceActive();
        long graceEnds = shield != null ? shield.graceEndsAt() : 0L;
        boolean eotw = shield != null ? shield.eotwActive() : rules != null && rules.eotwActive();
        j.key("grace").beginObject().field("active", grace).field("endsAt", graceEnds).endObject();
        j.field("eotw", eotw);

        writeEvents(j);

        j.key("feed").beginArray();
        for (Feed.Line line : feed.lines()) {
            j.beginObject().field("at", line.at()).field("type", line.type()).field("text", line.text()).endObject();
        }
        j.endArray();

        // Public chat only, oldest first. The chat module never passes faction, ally, officer or nation lines.
        j.key("chat").beginArray();
        for (ChatLine line : chatLines()) {
            j.beginObject();
            j.field("at", line.at());
            j.field("name", line.name());
            j.field("tag", line.tag());
            j.field("text", line.text());
            j.endObject();
        }
        j.endArray();
        j.endObject();
        return j.toString();
    }

    /** Top level nations list with id, tag, name, color and members, the tags of member factions that still exist. */
    private void writeNations(Json j) {
        j.key("nations").beginArray();
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations != null) {
            for (Nation n : safe(nations::all, List.<Nation>of())) {
                j.beginObject();
                j.field("id", n.id().toString());
                j.field("tag", n.tag());
                j.field("name", n.name());
                j.field("color", n.color());
                // Atlas reads the member faction tags under members, as its README documents.
                j.key("members").beginArray();
                for (UUID member : n.members()) {
                    Faction f = ctx.repo().faction(member);
                    if (f != null) {
                        j.value(f.tag());
                    }
                }
                j.endArray();
                j.endObject();
            }
        }
        j.endArray();
    }

    private static Collection<PlayerRef> onlinePlayers() {
        try {
            return new ArrayList<>(Universe.get().getPlayers());
        } catch (Exception e) {
            return List.of();
        }
    }

    private void writeWorld(Json j, String world, Collection<PlayerRef> online, Map<UUID, Boolean> raidable) {
        FactionService factions = ctx.serviceOrNull(FactionService.class);
        j.beginObject();
        j.field("name", world);

        // The file is public to the web map, so positions are only written when export.playerPositions
        // is on, and never for a player who hid from the map. Names and ids of online players stay.
        boolean positions = ctx.settings().bool("export.playerPositions");
        j.key("players").beginArray();
        for (PlayerRef p : online) {
            if (!world.equals(Players.worldName(p))) {
                continue;
            }
            Vector3d pos = Players.position(p);
            UUID id = p.getUuid();
            Faction f = factions == null ? null : factions.factionOf(id);
            j.beginObject();
            j.field("uuid", id.toString());
            j.field("name", p.getUsername());
            boolean hidden = ctx.player(id).mapHidden();
            if (hidden || !positions || pos == null) {
                j.field("x", (String) null).field("y", (String) null).field("z", (String) null);
            } else {
                j.field("x", pos.x).field("y", pos.y).field("z", pos.z);
            }
            j.field("faction", f == null ? null : f.tag());
            j.field("color", f == null ? null : f.color());
            j.field("hidden", hidden);
            j.endObject();
        }
        j.endArray();

        j.key("territories").beginArray();
        Long2ObjectMap<ClaimEntry> claims = ctx.repo().claimsIn(world);
        for (MapData.Zone z : data.zones(world, raidable)) {
            Faction f = z.faction();
            j.beginObject();
            j.field("id", f.id().toString());
            j.field("tag", f.tag());
            j.field("name", f.name());
            j.field("color", f.color());
            j.field("type", f.type().name());
            j.field("raidable", z.raidable());
            j.key("chunks").beginArray();
            for (long c : z.chunks()) {
                j.pair(Keys.chunkX(c), Keys.chunkZ(c));
            }
            j.endArray();
            j.key("breached").beginArray();
            for (long c : z.breached()) {
                j.pair(Keys.chunkX(c), Keys.chunkZ(c));
            }
            j.endArray();
            writeZones(j, f, z.chunks(), claims);
            j.endObject();
        }
        j.endArray();

        j.key("points").beginArray();
        for (PointOfInterest p : data.points(world)) {
            j.beginObject();
            j.field("id", p.id());
            j.field("kind", p.kind());
            j.field("label", p.label());
            j.field("x", p.x()).field("y", p.y()).field("z", p.z());
            j.field("color", p.color());
            j.field("faction", tagOf(p.ownerId()));
            j.field("expiresAt", p.expiresAt());
            j.endObject();
        }
        j.endArray();
        j.endObject();
    }

    /** Named zones inside one territory with the chunks assigned to each. */
    private static void writeZones(Json j, Faction f, long[] chunks, Long2ObjectMap<ClaimEntry> claims) {
        Map<String, List<Long>> byZone = new LinkedHashMap<>();
        for (long c : chunks) {
            ClaimEntry e = claims == null ? null : claims.get(c);
            String zoneId = e == null ? null : e.zoneId();
            if (zoneId != null && !zoneId.isEmpty()) {
                byZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(c);
            }
        }
        j.key("zones").beginArray();
        for (Zone zone : f.zones()) {
            j.beginObject();
            j.field("id", zone.id());
            j.field("name", zone.name());
            j.key("chunks").beginArray();
            for (long c : byZone.getOrDefault(zone.id(), List.of())) {
                j.pair(Keys.chunkX(c), Keys.chunkZ(c));
            }
            j.endArray();
            j.endObject();
        }
        j.endArray();
    }

    /** KOTH hills, outposts, envoys, bounties and the season from the events module, or null. */
    private void writeEvents(Json j) {
        EventsService events = ctx.serviceOrNull(EventsService.class);
        j.key("events");
        if (events == null) {
            j.value((String) null);
            return;
        }
        j.beginObject();
        j.field("enabled", safe(events::enabled, false));
        // koths() and outposts() return status sentences, so names come from the map points, whose ids end in the lower case name.
        List<String> hills = new ArrayList<>();
        List<String> posts = new ArrayList<>();
        for (dev.ryvrook.dominion.api.PointOfInterest poi : safe(() -> events.points(null), List.<dev.ryvrook.dominion.api.PointOfInterest>of())) {
            String id = poi.id() == null ? "" : poi.id();
            if ("hill".equals(poi.kind()) && id.startsWith("hill:")) {
                hills.add(id.substring(5));
            } else if ("outpost".equals(poi.kind()) && id.startsWith("outpost:")) {
                posts.add(id.substring(8));
            }
        }
        j.key("koths").beginArray();
        for (String name : hills) {
            j.beginObject().field("name", name).field("status", safe(() -> events.kothStatus(name), null)).endObject();
        }
        j.endArray();
        j.key("outposts").beginArray();
        for (String name : posts) {
            Faction holder = safe(() -> events.outpostHolder(name), null);
            j.beginObject();
            j.field("name", name);
            j.field("holder", holder == null ? null : holder.tag());
            j.field("control", safe(() -> events.outpostStatus(name), null));
            j.endObject();
        }
        j.endArray();
        j.key("envoys").beginArray();
        for (String envoy : safe(events::envoys, List.<String>of())) {
            j.value(envoy);
        }
        j.endArray();
        List<String> bounties = safe(events::bounties, List.<String>of());
        j.field("bounties", bounties == null ? 0 : bounties.size());
        j.field("season", safe(events::seasonStatus, null));
        j.endObject();
    }

    /** Calls into another module, falling back when it throws or returns null. */
    private static <T> T safe(Supplier<T> call, T fallback) {
        try {
            T v = call.get();
            return v == null ? fallback : v;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private String nationTag(Faction f) {
        NationService nations = ctx.serviceOrNull(NationService.class);
        if (nations == null) {
            return null;
        }
        Nation n = safe(() -> nations.nationOf(f), null);
        return n == null ? null : n.tag();
    }

    private void writeFaction(Json j, Faction f) {
        PowerService power = ctx.serviceOrNull(PowerService.class);
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        List<Member> members = f.members();
        int onlineCount = 0;
        long kills = 0;
        long deaths = 0;
        String leader = "";
        for (Member m : members) {
            if (Players.isOnline(m.uuid())) {
                onlineCount++;
            }
            kills += m.kills();
            deaths += m.deaths();
            if (m.uuid().equals(f.leader())) {
                leader = m.name();
            }
        }
        j.beginObject();
        j.field("id", f.id().toString());
        j.field("tag", f.tag());
        j.field("name", f.name());
        j.field("color", f.color());
        j.field("nation", nationTag(f));
        j.field("leader", leader);
        j.field("members", members.size());
        j.key("memberIds").beginArray();
        for (dev.ryvrook.dominion.model.Member m : members) {
            j.value(m.uuid().toString());
        }
        j.endArray();
        j.field("online", onlineCount);
        j.field("claims", ctx.repo().claimCount(f.id()));
        j.field("power", power == null ? 0.0 : power.factionPower(f));
        j.field("maxPower", power == null ? 0.0 : power.factionMaxPower(f));
        j.field("value", progress == null ? 0.0 : progress.value(f));
        j.field("rank", progress == null ? 0 : progress.rank(f));
        j.field("kills", Math.max(f.stat("kills"), kills));
        j.field("deaths", Math.max(f.stat("deaths"), deaths));
        j.field("siegesWon", f.stat("siegesWon"));
        j.field("siegesLost", f.stat("siegesLost"));
        j.field("level", progress == null ? 1 + Math.max(0, f.points()) / 100 : progress.level(f));
        j.field("points", f.points());
        j.field("bank", f.bank());
        j.key("relations").beginObject();
        Map<String, String> rel = new HashMap<>();
        for (Map.Entry<UUID, Relation> e : f.relations().entrySet()) {
            Relation r = e.getValue();
            if (r != Relation.ALLY && r != Relation.TRUCE && r != Relation.ENEMY) {
                continue;
            }
            Faction other = ctx.repo().faction(e.getKey());
            if (other != null) {
                rel.put(other.tag(), r.name());
            }
        }
        for (Map.Entry<String, String> e : rel.entrySet()) {
            j.field(e.getKey(), e.getValue());
        }
        j.endObject();
        j.field("createdAt", f.createdAt());
        j.field("description", f.description());
        j.endObject();
    }

    private String tagOf(UUID factionId) {
        Faction f = ctx.repo().faction(factionId);
        return f == null ? null : f.tag();
    }
}
