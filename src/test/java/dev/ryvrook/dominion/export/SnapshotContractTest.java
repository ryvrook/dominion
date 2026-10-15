package dev.ryvrook.dominion.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Nation;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.model.Zone;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.NationService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds a snapshot from a small fake world and checks it against the contract the web map reads,
 * as documented under "Factions snapshot" in the Atlas README and read by Atlas DominionSnapshot.
 */
class SnapshotContractTest {
    @TempDir
    Path dir;

    private DominionContext ctx;
    private ExportServiceImpl export;
    private Faction wolf;
    private Faction bear;
    private Faction fox;
    private Faction safe;
    private Nation pact;

    private static Faction faction(String tag, String color) {
        UUID leader = UUID.randomUUID();
        Faction f = new Faction(UUID.randomUUID(), tag, tag + " Clan", leader, 1_700_000_000_000L);
        f.putMember(new Member(leader, tag + "Lead", Rank.LEADER, 1L));
        f.setColor(color);
        return f;
    }

    @BeforeEach
    void world() {
        ctx = TestContexts.create(dir);
        wolf = faction("WOLF", "#ff8800");
        bear = faction("BEAR", "#0088ff");
        fox = faction("FOX", "#00ff88");
        safe = new Faction(UUID.randomUUID(), "SafeZone", "Safe Zone", null, 1L);
        safe.setType(FactionType.SAFEZONE);
        for (Faction f : List.of(wolf, bear, fox, safe)) {
            ctx.repo().addFaction(f);
        }
        wolf.putMember(new Member(UUID.randomUUID(), "Pup", Rank.MEMBER, 2L));
        wolf.putZone(new Zone("farm", "North Farm"));
        wolf.setDescription("Line one\nwith \"quotes\"");
        wolf.setHome(new Location("default", 8, 70, 8, 0f, 0f));
        // Wolf asked Bear for an alliance that Bear never accepted, and Fox declared Wolf an enemy.
        wolf.setRelation(bear.id(), Relation.ALLY);
        fox.setRelation(wolf.id(), Relation.ENEMY);
        ctx.repo().putClaim("default", Keys.chunk(0, 0), new ClaimEntry(wolf.id(), 1L, null, "farm"));
        ctx.repo().putClaim("default", Keys.chunk(1, 0), new ClaimEntry(wolf.id(), 1L, null, null));
        ctx.repo().putClaim("default", Keys.chunk(-5, 3), new ClaimEntry(safe.id(), 1L, null, null));

        pact = new Nation(UUID.randomUUID(), "PACT", "The Pact", bear.id(), 1L);
        pact.addMember(bear.id());
        pact.addMember(fox.id());
        pact.setColor("#aa00aa");

        ctx.register(PowerService.class, TestContexts.fake(PowerService.class, Map.of(
                "factionPower", a -> 12.5, "factionMaxPower", a -> 40.0)));
        ctx.register(ClaimService.class, TestContexts.fake(ClaimService.class, Map.of(
                "raidable", a -> a[0] == wolf)));
        ctx.register(NationService.class, TestContexts.fake(NationService.class, Map.of(
                "all", a -> List.of(pact),
                "nationOf", a -> pact.isMember(((Faction) a[0]).id()) ? pact : null)));
        ctx.register(RelationService.class, TestContexts.fake(RelationService.class, Map.of("between", a -> {
            Faction x = (Faction) a[0];
            Faction y = (Faction) a[1];
            if (pact.isMember(x.id()) && pact.isMember(y.id())) {
                return Relation.ALLY;
            }
            if (x.relationTo(y.id()) == Relation.ENEMY || y.relationTo(x.id()) == Relation.ENEMY) {
                return Relation.ENEMY;
            }
            Relation r = x.relationTo(y.id());
            return r == y.relationTo(x.id()) ? r : Relation.NEUTRAL;
        })));
        ctx.register(EventsService.class, TestContexts.fake(EventsService.class, Map.of(
                "enabled", a -> true,
                "points", a -> List.of(new PointOfInterest("hill:spawn hill", "hill", "Spawn Hill", "default",
                        100, 64, 100, null, "#ffd166", 0L)),
                "kothStatus", a -> "Contested",
                "envoys", a -> List.of("An envoy lands soon"),
                "bounties", a -> List.of("a", "b"),
                "seasonStatus", a -> "Season 1, day 3")));
        export = new ExportServiceImpl(ctx, new MapData(ctx));
    }

    @org.junit.jupiter.api.AfterEach
    void close() {
        TestContexts.close(ctx);
    }

    private JsonObject snapshot() {
        return JsonParser.parseString(export.build()).getAsJsonObject();
    }

    private static JsonObject byKey(JsonArray arr, String key, String value) {
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (o.has(key) && !o.get(key).isJsonNull() && value.equals(o.get(key).getAsString())) {
                return o;
            }
        }
        return null;
    }

    private static void number(JsonObject o, String... keys) {
        for (String k : keys) {
            assertTrue(o.has(k), "missing " + k);
            assertTrue(o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isNumber(), k + " must be a number");
        }
    }

    private static void text(JsonObject o, String... keys) {
        for (String k : keys) {
            assertTrue(o.has(k), "missing " + k);
            assertTrue(o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isString(), k + " must be a string");
        }
    }

    private static void bool(JsonObject o, String... keys) {
        for (String k : keys) {
            assertTrue(o.has(k), "missing " + k);
            assertTrue(o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isBoolean(), k + " must be a boolean");
        }
    }

    private static void pairs(JsonArray chunks) {
        for (JsonElement c : chunks) {
            JsonArray p = c.getAsJsonArray();
            assertEquals(2, p.size());
            assertTrue(p.get(0).getAsJsonPrimitive().isNumber() && p.get(1).getAsJsonPrimitive().isNumber());
        }
    }

    @Test
    void topLevelShape() {
        JsonObject root = snapshot();
        assertEquals(1, root.get("version").getAsInt());
        number(root, "at");
        number(root.getAsJsonObject("server"), "online");
        for (String k : new String[] {"worlds", "factions", "nations", "sieges", "feed", "chat"}) {
            assertTrue(root.get(k).isJsonArray(), k + " must be an array");
        }
        JsonObject grace = root.getAsJsonObject("grace");
        bool(grace, "active");
        number(grace, "endsAt");
        bool(root, "eotw");
    }

    @Test
    void territoriesMatchTheContract() {
        JsonObject world = byKey(snapshot().getAsJsonArray("worlds"), "name", "default");
        assertNotNull(world);
        assertTrue(world.get("players").isJsonArray());
        JsonArray terr = world.getAsJsonArray("territories");
        JsonObject w = byKey(terr, "tag", "WOLF");
        text(w, "id", "tag", "name", "color", "type");
        bool(w, "raidable");
        assertTrue(w.get("raidable").getAsBoolean());
        assertEquals("FACTION", w.get("type").getAsString(), "player factions read as FACTION so the map shows no badge");
        assertEquals(2, w.getAsJsonArray("chunks").size());
        pairs(w.getAsJsonArray("chunks"));
        assertTrue(w.getAsJsonArray("breached").isEmpty());
        JsonObject zone = w.getAsJsonArray("zones").get(0).getAsJsonObject();
        text(zone, "id", "name");
        assertEquals("North Farm", zone.get("name").getAsString());
        assertEquals(List.of(0, 0), List.of(zone.getAsJsonArray("chunks").get(0).getAsJsonArray().get(0).getAsInt(),
                zone.getAsJsonArray("chunks").get(0).getAsJsonArray().get(1).getAsInt()));
        JsonObject s = byKey(terr, "tag", "SafeZone");
        assertEquals("SAFEZONE", s.get("type").getAsString());
        assertFalse(s.get("raidable").getAsBoolean());
        JsonArray c = s.getAsJsonArray("chunks").get(0).getAsJsonArray();
        assertEquals(-5, c.get(0).getAsInt());
        assertEquals(3, c.get(1).getAsInt());
    }

    @Test
    void pointsMatchTheContract() {
        JsonObject world = byKey(snapshot().getAsJsonArray("worlds"), "name", "default");
        JsonArray points = world.getAsJsonArray("points");
        JsonObject hill = byKey(points, "kind", "hill");
        text(hill, "id", "kind", "label", "color");
        number(hill, "x", "y", "z", "expiresAt");
        assertTrue(hill.get("faction").isJsonNull(), "an event point has no owner");
        JsonObject home = byKey(points, "kind", "home");
        assertEquals("WOLF", home.get("faction").getAsString(), "points carry the owner's tag, which Atlas compares with the viewer's");
    }

    @Test
    void factionsMatchTheContract() {
        JsonArray factions = snapshot().getAsJsonArray("factions");
        assertEquals(3, factions.size(), "only player factions are listed");
        JsonObject w = byKey(factions, "tag", "WOLF");
        text(w, "id", "tag", "name", "color", "leader", "description");
        number(w, "members", "online", "claims", "power", "maxPower", "value", "rank", "kills", "deaths",
                "siegesWon", "siegesLost", "level", "points", "bank", "createdAt");
        assertEquals(2, w.get("members").getAsInt());
        assertEquals(2, w.getAsJsonArray("memberIds").size());
        assertEquals(2, w.get("claims").getAsInt());
        assertEquals(12.5, w.get("power").getAsDouble());
        assertTrue(w.get("nation").isJsonNull());
        assertEquals("Line one\nwith \"quotes\"", w.get("description").getAsString());
        JsonObject b = byKey(factions, "tag", "BEAR");
        assertEquals("PACT", b.get("nation").getAsString());
    }

    /** The faction fields Chronicle Stats reads in DominionSnapshotReader, with the member list it logs from. */
    @Test
    void factionsCarryWhatChronicleReads() {
        JsonObject w = byKey(snapshot().getAsJsonArray("factions"), "tag", "WOLF");
        text(w, "tag", "name", "color");
        number(w, "claims", "power", "maxPower", "level", "kills", "deaths", "createdAt");
        assertEquals(1_700_000_000_000L, w.get("createdAt").getAsLong());
        JsonArray ids = w.getAsJsonArray("memberIds");
        assertTrue(ids.contains(new com.google.gson.JsonPrimitive(wolf.leader().toString())));
        JsonObject b = byKey(snapshot().getAsJsonArray("factions"), "tag", "BEAR");
        assertEquals("PACT", b.get("nation").getAsString(), "the nation is the tag Chronicle shows");
    }

    @Test
    void relationsAreTheResolvedOnes() {
        JsonArray factions = snapshot().getAsJsonArray("factions");
        JsonObject wolfRel = byKey(factions, "tag", "WOLF").getAsJsonObject("relations");
        assertFalse(wolfRel.has("BEAR"), "an ally request Bear never accepted is not an alliance");
        assertEquals("ENEMY", wolfRel.get("FOX").getAsString(), "Fox declared Wolf an enemy, which counts for both");
        JsonObject bearRel = byKey(factions, "tag", "BEAR").getAsJsonObject("relations");
        assertEquals("ALLY", bearRel.get("FOX").getAsString(), "nation members are allies");
    }

    @Test
    void nationsAndEvents() {
        JsonObject root = snapshot();
        JsonObject n = root.getAsJsonArray("nations").get(0).getAsJsonObject();
        text(n, "id", "tag", "name", "color");
        assertEquals(List.of("BEAR", "FOX"), List.of(n.getAsJsonArray("members").get(0).getAsString(),
                n.getAsJsonArray("members").get(1).getAsString()));
        JsonObject ev = root.getAsJsonObject("events");
        bool(ev, "enabled");
        JsonObject koth = ev.getAsJsonArray("koths").get(0).getAsJsonObject();
        assertEquals("spawn hill", koth.get("name").getAsString());
        assertEquals("Contested", koth.get("status").getAsString());
        assertTrue(ev.get("outposts").isJsonArray());
        assertEquals("An envoy lands soon", ev.getAsJsonArray("envoys").get(0).getAsString());
        assertEquals(2, ev.get("bounties").getAsInt());
        assertEquals("Season 1, day 3", ev.get("season").getAsString());
    }

    @Test
    void eventsAreLeftOutWithoutTheEventsModule(@TempDir Path other) {
        DominionContext bare = TestContexts.create(other);
        JsonObject root;
        try {
            root = JsonParser.parseString(new ExportServiceImpl(bare, new MapData(bare)).build()).getAsJsonObject();
        } finally {
            TestContexts.close(bare);
        }
        assertFalse(root.has("events"), "events is optional and is left out rather than written as null");
        assertTrue(root.getAsJsonArray("nations").isEmpty());
    }

    @Test
    void chatIsPublicOnlyOldestFirstWithStrictlyIncreasingTimes() {
        for (int i = 0; i < 60; i++) {
            export.recordChat("Ann", i % 2 == 0 ? "WOLF" : "", "line " + i);
        }
        JsonArray chat = snapshot().getAsJsonArray("chat");
        assertEquals(50, chat.size(), "the last 50 lines");
        assertEquals("line 10", chat.get(0).getAsJsonObject().get("text").getAsString());
        long last = -1;
        for (JsonElement e : chat) {
            JsonObject o = e.getAsJsonObject();
            number(o, "at");
            text(o, "name", "tag", "text");
            assertTrue(o.get("at").getAsLong() > last);
            last = o.get("at").getAsLong();
        }
    }

    @Test
    void feedTimesAreStrictlyIncreasing() {
        for (int i = 0; i < 5; i++) {
            export.onEvent(new DominionEvent.FactionCreated(i % 2 == 0 ? wolf.id() : bear.id()));
        }
        JsonArray feed = snapshot().getAsJsonArray("feed");
        assertTrue(feed.size() >= 2);
        long last = -1;
        for (JsonElement e : feed) {
            JsonObject o = e.getAsJsonObject();
            number(o, "at");
            text(o, "type", "text");
            assertTrue(o.get("at").getAsLong() > last);
            last = o.get("at").getAsLong();
        }
    }

    @Test
    void playerPositionsAreOffByDefault() {
        assertFalse(ctx.settings().bool("export.playerPositions"));
    }

    @Test
    void flushWritesTheFileAtomicallyAndForces() throws Exception {
        export.flush();
        Path file = dir.resolve("export").resolve("snapshot.json");
        assertTrue(Files.exists(file));
        JsonParser.parseString(Files.readString(file));
        long before = Files.getLastModifiedTime(file).toMillis();
        assertFalse(export.write(false), "nothing changed, so a timed write skips");
        Thread.sleep(5);
        export.flush();
        assertTrue(Files.getLastModifiedTime(file).toMillis() >= before);
        try (var l = Files.list(file.getParent())) {
            assertTrue(l.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
        assertFalse(Files.exists(dev.ryvrook.dominion.storage.JsonFiles.backupOf(file)), "the snapshot keeps no backup");
    }

    @Test
    void numbersStayPlainAndFinite() {
        wolf.setBank(Double.MAX_VALUE);
        ctx.register(PowerService.class, TestContexts.fake(PowerService.class, new HashMap<>(Map.of(
                "factionPower", a -> Double.NaN, "factionMaxPower", a -> Double.POSITIVE_INFINITY))));
        JsonObject w = byKey(snapshot().getAsJsonArray("factions"), "tag", "WOLF");
        assertEquals(0, w.get("power").getAsDouble());
        assertEquals(0, w.get("maxPower").getAsDouble());
        assertTrue(Double.isFinite(w.get("bank").getAsDouble()));
    }
}
