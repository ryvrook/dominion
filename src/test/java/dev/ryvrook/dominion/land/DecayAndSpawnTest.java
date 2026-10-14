package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.faction.FactionServiceImpl;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.util.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DecayAndSpawnTest {
    static final String W = "world";
    static final long DAY = 86_400_000L;

    Fixtures fx;
    ClaimServiceImpl claims;

    @BeforeEach
    void setUp() {
        Outage.boot();
        fx = Fixtures.create();
        fx.ctx.register(FactionService.class, new FactionServiceImpl(fx.ctx));
        claims = new ClaimServiceImpl(fx.ctx);
        fx.set("land.bufferChunks", 0).set("land.mustBeConnected", false);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    static long c(int x, int z) {
        return Keys.chunk(x, z);
    }

    /** An L of four chunks, (0,0) (1,0) (2,0) and (2,1), with the leader last seen long ago. */
    Faction idle(long lastSeen) {
        Faction f = fx.faction("Idle");
        for (long chunk : new long[] { c(0, 0), c(1, 0), c(2, 0), c(2, 1) }) {
            claims.adminClaim(null, f, W, chunk, true);
        }
        Member m = f.member(f.leader());
        m.setLastSeen(lastSeen);
        return f;
    }

    @Test
    void decayTakesTheWeakestEdgeOncePerDayAndKeepsOneChunk() {
        // A busy faction seen just before this boot keeps the outage estimate near zero.
        Faction busy = fx.faction("Busy");
        busy.member(busy.leader()).setLastSeen(Outage.bootAt() - 1);
        Faction f = idle(System.currentTimeMillis() - 20 * DAY);
        fx.set("land.decayAfterDays", 14).set("land.decayChunksPerDay", 2);
        claims.decayTick();
        assertEquals(2, claims.claimCount(f));
        assertNull(claims.ownerAt(W, c(0, 0)), "the end of the L goes first");
        assertNull(claims.ownerAt(W, c(2, 1)));
        claims.decayTick();
        assertEquals(2, claims.claimCount(f), "a second run on the same day does nothing");
        f.removeStat(Decay.LAST_DAY);
        fx.set("land.decayChunksPerDay", 5);
        claims.decayTick();
        assertEquals(1, claims.claimCount(f), "decay never takes the last chunk");
    }

    @Test
    void decayKeepsTheHomeChunkForLast() {
        Faction busy = fx.faction("Busy");
        busy.member(busy.leader()).setLastSeen(Outage.bootAt() - 1);
        Faction f = idle(System.currentTimeMillis() - 20 * DAY);
        f.setHome(new Location(W, 1, 64, 1, 0, 0));
        fx.set("land.decayChunksPerDay", 3);
        claims.decayTick();
        assertEquals(1, claims.claimCount(f));
        assertEquals(f.id(), claims.ownerAt(W, c(0, 0)).id());
    }

    @Test
    void recentOrOnlineMembersStopDecay() {
        Faction f = idle(System.currentTimeMillis() - 2 * DAY);
        claims.decayTick();
        assertEquals(4, claims.claimCount(f));
        Faction g = fx.faction("Gone");
        claims.adminClaim(null, g, W, c(10, 10), true);
        claims.adminClaim(null, g, W, c(11, 10), true);
        UUID lead = g.leader();
        g.member(lead).setLastSeen(System.currentTimeMillis() - 30 * DAY);
        claims.land().online = lead::equals;
        claims.decayTick();
        assertEquals(2, claims.claimCount(g));
        fx.set("land.decayAfterDays", 0);
        claims.land().online = id -> false;
        claims.decayTick();
        assertEquals(2, claims.claimCount(g), "zero days turns decay off");
    }

    @Test
    void downtimeIsCreditedBackToOldLastSeenTimes() {
        Faction f = idle(System.currentTimeMillis() - 20 * DAY);
        // This faction is the only record before the boot, so the whole gap counts as downtime.
        claims.decayTick();
        assertEquals(4, claims.claimCount(f));
        assertTrue(Outage.downtime(fx.ctx) >= 20 * DAY - 1000);
    }

    @Test
    void spawnGuardMeasuresToTheNearestPointOfTheChunk() {
        int size = Keys.chunkSize();
        assertTrue(SpawnGuard.within(0.5, 0.5, c(0, 0), 64), "the spawn chunk itself");
        assertTrue(SpawnGuard.within(0, 0, c(1, 0), size + 1));
        assertFalse(SpawnGuard.within(0, 0, c(1, 0), size), "exactly the radius away is outside");
        assertTrue(SpawnGuard.within(0, 0, c(-2, 0), size + 1), "the west chunk ends at minus one chunk");
        assertFalse(SpawnGuard.within(0, 0, c(-2, 0), size));
        assertFalse(SpawnGuard.within(0, 0, c(0, 0), 0), "a zero radius turns it off");
        // Diagonal neighbour, corner at (size, size) from a spawn at the origin.
        double corner = Math.sqrt(2.0) * size;
        assertFalse(SpawnGuard.within(0, 0, c(1, 1), (int) Math.floor(corner)));
        assertTrue(SpawnGuard.within(0, 0, c(1, 1), (int) Math.ceil(corner)));
    }
}
