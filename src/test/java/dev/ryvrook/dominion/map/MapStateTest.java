package dev.ryvrook.dominion.map;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.util.Keys;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MapStateTest {
    @Test
    void claimChangeQueuesTheChunkAndItsNeighbours() {
        MapState state = new MapState();
        long index = Keys.chunk(3, -2);
        long before = state.version("default");
        state.onEvent(new DominionEvent.ClaimChanged("default", index, null, UUID.randomUUID()));
        assertNotEquals(before, state.version("default"));
        Map<String, LongSet> drained = state.drainChunks();
        LongSet expected = new LongOpenHashSet(new long[] {index, Keys.chunk(4, -2), Keys.chunk(2, -2),
                Keys.chunk(3, -1), Keys.chunk(3, -3)});
        assertEquals(expected, drained.get("default"));
        assertTrue(state.drainChunks().isEmpty(), "draining empties the queue");
    }

    @Test
    void overlappingChangesMergePerWorld() {
        MapState state = new MapState();
        state.markChunk("a", Keys.chunk(0, 0));
        state.markChunk("a", Keys.chunk(1, 0));
        state.markChunk("b", Keys.chunk(0, 0));
        Map<String, LongSet> drained = state.drainChunks();
        assertEquals(8, drained.get("a").size());
        assertEquals(5, drained.get("b").size());
    }

    @Test
    void markChunksAddsNoNeighbours() {
        MapState state = new MapState();
        state.markChunks("a", new LongOpenHashSet(new long[] {Keys.chunk(5, 5)}));
        assertEquals(1, state.drainChunks().get("a").size());
    }

    @Test
    void factionChangesAndDisbandsAreQueued() {
        MapState state = new MapState();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        state.onEvent(new DominionEvent.FactionChanged(a));
        state.onEvent(new DominionEvent.FactionDisbanded(b, "B"));
        state.onEvent(new DominionEvent.FactionChanged(a));
        assertEquals(Set.of(a, b), state.drainFactions());
        assertTrue(state.drainFactions().isEmpty());
    }
}
