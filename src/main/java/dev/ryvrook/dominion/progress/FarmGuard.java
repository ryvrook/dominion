package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.FactionService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Stops mission farming. Ore and trunk blocks placed by players are remembered by world and
 * position for missions.placedBlockMemoryMinutes, and breaking one of them earns no mission credit.
 * Other blocks never earn credit, so they are not remembered and the cap lasts longer. Items a
 * player drops are remembered for five minutes, and picking such an item up earns no credit for
 * the dropper or any member of the dropper's faction, so two members cannot farm a mission by
 * passing the same crops back and forth. Drops by players of other factions never withhold credit.
 * The memory outlives a disconnect for the same reason. Both memories are bounded so a busy server
 * cannot grow them without limit.
 */
final class FarmGuard {
    /** Most placed positions kept across all worlds. The oldest are forgotten first. */
    static final int MAX_PLACED = 100_000;
    /** Most remembered drops per player. The oldest are forgotten first. */
    static final int MAX_DROPS_PER_PLAYER = 256;
    static final long DROP_MEMORY_MILLIS = 5 * 60_000L;

    private record Pos(String world, int x, int y, int z) {}

    private static final class Drop {
        final String itemId;
        final long at;
        int quantity;

        Drop(String itemId, int quantity, long at) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.at = at;
        }
    }

    private final LongSupplier placedMemory;
    /** The faction id of a player, or null when the player has none. */
    private final Function<UUID, UUID> factionOf;
    /** Insertion ordered, so the eldest entry is always the oldest placement. */
    private final LinkedHashMap<Pos, Long> placed = new LinkedHashMap<>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Pos, Long> eldest) {
            return size() > MAX_PLACED;
        }
    };
    private final Map<UUID, Deque<Drop>> drops = new ConcurrentHashMap<>();

    FarmGuard(DominionContext ctx) {
        this(() -> ctx.settings().integer("missions.placedBlockMemoryMinutes") * 60_000L, player -> {
            FactionService fs = ctx.serviceOrNull(FactionService.class);
            Faction f = fs == null ? null : fs.factionOf(player);
            return f == null ? null : f.id();
        });
    }

    /**
     * For tests and callers that supply the placed block memory in milliseconds, read on every use,
     * and a lookup from player to faction id.
     */
    FarmGuard(LongSupplier placedMemoryMillis, Function<UUID, UUID> factionOf) {
        this.placedMemory = placedMemoryMillis;
        this.factionOf = factionOf;
    }

    private long placedMemoryMillis() {
        return Math.max(0, placedMemory.getAsLong());
    }

    // ---- placed blocks -------------------------------------------------------

    /** True for block ids whose breaking can earn mission credit, ids starting with Ore_ or Wood_ trunks. */
    static boolean remembers(String blockId) {
        return GatherSystems.blockKind(blockId) != null;
    }

    void placed(String world, String blockId, int x, int y, int z, long now) {
        if (world == null || !remembers(blockId) || placedMemoryMillis() <= 0) {
            return;
        }
        Pos p = new Pos(world, x, y, z);
        synchronized (placed) {
            // Remove first so a re-placed block moves to the young end of the order.
            placed.remove(p);
            placed.put(p, now);
        }
    }

    /** True when a player placed this block recently. The memory is consumed, since the block is gone. */
    boolean wasPlaced(String world, int x, int y, int z, long now) {
        if (world == null) {
            return false;
        }
        long memory = placedMemoryMillis();
        Long at;
        synchronized (placed) {
            at = placed.remove(new Pos(world, x, y, z));
        }
        return at != null && memory > 0 && now - at <= memory;
    }

    // ---- dropped items -------------------------------------------------------

    void dropped(UUID player, String itemId, int quantity, long now) {
        if (player == null || itemId == null || quantity <= 0) {
            return;
        }
        Deque<Drop> list = drops.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (list) {
            prune(list, now);
            list.addLast(new Drop(itemId, quantity, now));
            while (list.size() > MAX_DROPS_PER_PLAYER) {
                list.removeFirst();
            }
        }
    }

    /**
     * How many of the picked up items the picker or a member of the picker's faction dropped in the
     * last five minutes, starting with the picker's own drops. Those items are used up from the
     * memory and earn no credit.
     */
    int ownDrops(UUID player, String itemId, int quantity, long now) {
        if (itemId == null || quantity <= 0) {
            return 0;
        }
        int matched = 0;
        Deque<Drop> mine = player == null ? null : drops.get(player);
        if (mine != null) {
            matched += take(mine, itemId, quantity, now);
        }
        UUID faction = player == null || matched >= quantity ? null : factionOf.apply(player);
        if (faction == null) {
            return matched;
        }
        for (Map.Entry<UUID, Deque<Drop>> e : drops.entrySet()) {
            if (matched >= quantity) {
                break;
            }
            if (e.getValue() != mine && faction.equals(factionOf.apply(e.getKey()))) {
                matched += take(e.getValue(), itemId, quantity - matched, now);
            }
        }
        return matched;
    }

    /** Uses up to quantity matching items from one player's drop memory and returns how many. */
    private static int take(Deque<Drop> list, String itemId, int quantity, long now) {
        int matched = 0;
        synchronized (list) {
            prune(list, now);
            Iterator<Drop> it = list.iterator();
            while (it.hasNext() && matched < quantity) {
                Drop d = it.next();
                if (!d.itemId.equals(itemId)) {
                    continue;
                }
                int n = Math.min(d.quantity, quantity - matched);
                d.quantity -= n;
                matched += n;
                if (d.quantity <= 0) {
                    it.remove();
                }
            }
        }
        return matched;
    }

    private static void prune(Deque<Drop> list, long now) {
        while (!list.isEmpty() && now - list.peekFirst().at > DROP_MEMORY_MILLIS) {
            list.removeFirst();
        }
    }

    void forget(UUID player) {
        drops.remove(player);
    }

    /** Called once a minute. Drops placed positions older than the memory window. */
    void sweep(long now) {
        long memory = placedMemoryMillis();
        synchronized (placed) {
            Iterator<Map.Entry<Pos, Long>> it = placed.entrySet().iterator();
            while (it.hasNext()) {
                long at = it.next().getValue();
                if (memory <= 0 || now - at > memory) {
                    it.remove();
                } else {
                    // Insertion order means every later entry is younger.
                    break;
                }
            }
        }
        for (Map.Entry<UUID, Deque<Drop>> e : drops.entrySet()) {
            Deque<Drop> list = e.getValue();
            synchronized (list) {
                prune(list, now);
                if (list.isEmpty()) {
                    drops.remove(e.getKey(), list);
                }
            }
        }
    }
}
