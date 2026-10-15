package dev.ryvrook.dominion.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.util.Keys;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MapOverlayTest {
    private static final int GROUND = 0x408040ff;

    @TempDir
    Path dir;
    private DominionContext ctx;
    private MapState state;
    private MapOverlay overlay;
    private Faction red;
    private Faction blue;

    @BeforeEach
    void setUp() {
        ctx = TestContexts.create(dir);
        state = new MapState();
        overlay = new MapOverlay(ctx, state);
        red = new Faction(UUID.randomUUID(), "Red", "Red name", UUID.randomUUID(), 1L);
        red.setColor("#ff0000");
        blue = new Faction(UUID.randomUUID(), "Blue", "Blue name", UUID.randomUUID(), 1L);
        blue.setColor("not a colour");
        ctx.repo().addFaction(red);
        ctx.repo().addFaction(blue);
    }

    @AfterEach
    void tearDown() {
        TestContexts.close(ctx);
    }

    private void claim(Faction f, int x, int z) {
        ctx.repo().putClaim("w", Keys.chunk(x, z), new ClaimEntry(f.id(), 1L, null, null));
    }

    private static MapImage plain() {
        int[] px = new int[32 * 32];
        Arrays.fill(px, GROUND);
        return OverlayPainter.encode(32, 32, px);
    }

    @Test
    void coloursFollowTheFactionAndZones() {
        assertEquals(0xff0000, MapOverlay.rgbOf(red));
        assertEquals(0x7fd6ff, MapOverlay.rgbOf(blue));
        Faction safe = new Faction(UUID.randomUUID(), "SafeZone", "Safe", null, 1L);
        safe.setType(FactionType.SAFEZONE);
        assertEquals(Integer.parseInt(MapOverlay.SAFEZONE_COLOR.substring(1), 16), MapOverlay.rgbOf(safe));
        Faction war = new Faction(UUID.randomUUID(), "WarZone", "War", null, 1L);
        war.setType(FactionType.WARZONE);
        assertEquals(0xe0413c, MapOverlay.rgbOf(war));
    }

    @Test
    void edgesFaceOnlyOtherOwners() {
        claim(red, 0, 0);
        claim(red, 1, 0);
        claim(blue, 0, 1);
        Long2ObjectMap<ClaimEntry> claims = ctx.repo().claimsIn("w");
        int edges = MapOverlay.edgesOf(Keys.chunk(0, 0), red.id(), i -> {
            ClaimEntry e = claims.get(i);
            return e == null ? null : e.factionId();
        });
        assertEquals(OverlayPainter.NORTH | OverlayPainter.SOUTH | OverlayPainter.WEST, edges);
    }

    @Test
    void decorateTintsClaimedChunksAndPassesTheRestThrough() {
        claim(red, 0, 0);
        claim(red, 1, 0);
        Long2ObjectOpenHashMap<MapImage> chunks = new Long2ObjectOpenHashMap<>();
        MapImage wild = plain();
        chunks.put(Keys.chunk(0, 0), plain());
        chunks.put(Keys.chunk(5, 5), wild);
        overlay.decorate("w", chunks);

        assertSame(wild, chunks.get(Keys.chunk(5, 5)), "unclaimed images are untouched");
        int[] px = OverlayPainter.decode(chunks.get(Keys.chunk(0, 0)));
        int opacity = ctx.settings().integer("map.overlayOpacity");
        int line = OverlayPainter.shade(0xff0000) << 8 | 0xff;
        assertEquals(line, px[0], "north west corner is outlined");
        assertEquals(line, px[16 * 32], "west edge is outlined");
        assertEquals(OverlayPainter.blend(GROUND, 0xff0000, opacity), px[16 * 32 + 31], "east edge joins the next claim");
        assertEquals(OverlayPainter.blend(GROUND, 0xff0000, opacity), px[16 * 32 + 16]);
    }

    @Test
    void overlayOffLeavesImagesAlone() {
        claim(red, 0, 0);
        ctx.settings().set("map.overlay", false);
        Long2ObjectOpenHashMap<MapImage> chunks = new Long2ObjectOpenHashMap<>();
        MapImage image = plain();
        chunks.put(Keys.chunk(0, 0), image);
        overlay.decorate("w", chunks);
        assertSame(image, chunks.get(Keys.chunk(0, 0)));
    }

    @Test
    void colourChangeAndDisbandQueueThePaintedChunks() {
        claim(red, 0, 0);
        claim(blue, 4, 4);
        Long2ObjectOpenHashMap<MapImage> chunks = new Long2ObjectOpenHashMap<>();
        chunks.put(Keys.chunk(0, 0), plain());
        chunks.put(Keys.chunk(4, 4), plain());
        overlay.decorate("w", chunks);

        overlay.resolveFactions(Set.of(red.id(), blue.id()));
        assertTrue(state.drainChunks().isEmpty(), "nothing changed yet");

        red.setColor("#00ff00");
        overlay.resolveFactions(Set.of(red.id()));
        Map<String, LongSet> drained = state.drainChunks();
        assertEquals(Set.of(Keys.chunk(0, 0)), Set.copyOf(drained.get("w")));

        ctx.repo().removeFaction(blue);
        overlay.resolveFactions(Set.of(blue.id()));
        assertEquals(5, state.drainChunks().get("w").size(), "a disbanded faction redraws its chunks and neighbours");
    }

    @Test
    void anchorSitsInTheLargestRegion() {
        claim(red, 0, 0);
        for (int x = 10; x < 13; x++) {
            for (int z = 0; z < 3; z++) {
                claim(red, x, z);
            }
        }
        Long2ObjectMap<ClaimEntry> claims = ctx.repo().claimsIn("w");
        assertEquals(Keys.chunk(11, 1), ClaimMarkerProvider.anchor(claims, red.id(), null));
    }

    @Test
    void homeBreaksATieBetweenRegions() {
        claim(red, 0, 0);
        claim(red, 9, 9);
        Long2ObjectMap<ClaimEntry> claims = ctx.repo().claimsIn("w");
        assertEquals(Keys.chunk(9, 9), ClaimMarkerProvider.anchor(claims, red.id(), Keys.chunk(9, 9)));
        assertEquals(Keys.chunk(0, 0), ClaimMarkerProvider.anchor(claims, red.id(), Keys.chunk(0, 0)));
    }
}
