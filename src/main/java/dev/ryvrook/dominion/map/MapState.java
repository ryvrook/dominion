package dev.ryvrook.dominion.map;

import dev.ryvrook.dominion.api.DominionEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Version counters that tell marker providers when to rebuild. Claim changes bump their world,
 * faction changes bump the global counter because colours and names live on every marker.
 */
public final class MapState {
    private final Map<String, AtomicLong> worlds = new ConcurrentHashMap<>();
    private final AtomicLong global = new AtomicLong();
    private final AtomicLong sieges = new AtomicLong();

    public void onEvent(DominionEvent event) {
        switch (event) {
            case DominionEvent.ClaimChanged c -> bump(c.world());
            case DominionEvent.FactionDisbanded d -> global.incrementAndGet();
            case DominionEvent.FactionChanged c -> global.incrementAndGet();
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
}
