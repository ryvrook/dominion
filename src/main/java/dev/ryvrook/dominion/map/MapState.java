package dev.ryvrook.dominion.map;

import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.util.Keys;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Version counters that tell marker providers when to rebuild. Claim changes bump their world,
 * faction changes bump the global counter because colours and names live on every marker.
 * It also collects the chunks whose map images must be drawn again for the claims overlay, a
 * changed chunk plus its four neighbours because their borders change too, and the factions whose
 * colour may have changed or that were disbanded.
 */
public final class MapState {
    private final Map<String, AtomicLong> worlds = new ConcurrentHashMap<>();
    private final AtomicLong global = new AtomicLong();
    private final AtomicLong sieges = new AtomicLong();
    private final Map<String, LongSet> dirtyChunks = new HashMap<>();
    private final Set<UUID> dirtyFactions = new HashSet<>();

    public void onEvent(DominionEvent event) {
        switch (event) {
            case DominionEvent.ClaimChanged c -> {
                bump(c.world());
                markChunk(c.world(), c.chunkIndex());
            }
            case DominionEvent.FactionDisbanded d -> {
                global.incrementAndGet();
                markFaction(d.factionId());
            }
            case DominionEvent.FactionChanged c -> {
                global.incrementAndGet();
                markFaction(c.factionId());
            }
            case DominionEvent.FactionCreated c -> global.incrementAndGet();
            case DominionEvent.SiegePhase p -> sieges.incrementAndGet();
            default -> { }
        }
    }

    public void bump(String world) {
        if (world != null) {
            worlds.computeIfAbsent(world, w -> new AtomicLong()).incrementAndGet();
        }
    }

    public void bumpAll() {
        global.incrementAndGet();
    }

    /** Changes whenever a siege starts, changes phase or ends. */
    public long siegeVersion() {
        return sieges.get();
    }

    /** Combined version for a world. Changes whenever the world or any faction changes. */
    public long version(String world) {
        AtomicLong w = worlds.get(world);
        return (w == null ? 0 : w.get()) * 1_000_003L + global.get();
    }

    /** Queues a chunk and its four neighbours for a fresh map image. */
    public void markChunk(String world, long index) {
        if (world == null) {
            return;
        }
        synchronized (dirtyChunks) {
            LongSet set = dirtyChunks.computeIfAbsent(world, w -> new LongOpenHashSet());
            set.add(index);
            for (long n : Keys.neighbours(index)) {
                set.add(n);
            }
        }
    }

    /** Queues exactly these chunks, without neighbours. */
    public void markChunks(String world, LongSet indexes) {
        if (world == null || indexes == null || indexes.isEmpty()) {
            return;
        }
        synchronized (dirtyChunks) {
            dirtyChunks.computeIfAbsent(world, w -> new LongOpenHashSet()).addAll(indexes);
        }
    }

    /** Queues a faction whose colour may have changed or that was disbanded. */
    public void markFaction(UUID faction) {
        if (faction == null) {
            return;
        }
        synchronized (dirtyFactions) {
            dirtyFactions.add(faction);
        }
    }

    /** Takes every queued chunk, by world, and empties the queue. */
    public Map<String, LongSet> drainChunks() {
        synchronized (dirtyChunks) {
            if (dirtyChunks.isEmpty()) {
                return Map.of();
            }
            Map<String, LongSet> out = new HashMap<>(dirtyChunks);
            dirtyChunks.clear();
            return out;
        }
    }

    /** Takes every queued faction and empties the queue. */
    public Set<UUID> drainFactions() {
        synchronized (dirtyFactions) {
            if (dirtyFactions.isEmpty()) {
                return Set.of();
            }
            Set<UUID> out = new HashSet<>(dirtyFactions);
            dirtyFactions.clear();
            return out;
        }
    }
}
