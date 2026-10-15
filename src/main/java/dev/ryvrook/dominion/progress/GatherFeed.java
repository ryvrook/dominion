package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.util.Keys;

import java.util.UUID;

/**
 * The mine, chop, farm and fish mission counters, fed either by Dominion's own block and item systems or by
 * TrackerAPI. Registered as a service under its own class, so {@code integration.tracker.TrackerBridge} finds it
 * without the progress package naming a TrackerAPI type. While {@link #external()} is true the own systems return
 * at once and the bridge calls the four methods below from TrackerAPI's dispatch thread with the same facts, so
 * the counts are the same either way. Dominion's {@link FarmGuard} stays the anti farm memory in both cases,
 * because it is wider than TrackerAPI's flags: a block any player placed earns nothing, and a crop a faction mate
 * dropped earns nothing.
 */
public final class GatherFeed {
    private final DominionContext ctx;
    private final ProgressService progress;
    private volatile FarmGuard guard;
    private volatile boolean external;

    GatherFeed(DominionContext ctx, ProgressService progress) {
        this.ctx = ctx;
        this.progress = progress;
    }

    /** Set once the progress module started. Until then every call does nothing. */
    void guard(FarmGuard g) {
        this.guard = g;
    }

    /** True while TrackerAPI feeds the counters and the own systems stand down. */
    public boolean external() {
        return external;
    }

    /** Called by the TrackerAPI bridge when its subscription opens or closes. */
    public void useTracker(boolean on) {
        external = on;
    }

    /** A player placed a block. {@code blockId} is the block the held item places, else the item id. */
    public void placed(String world, String blockId, int x, int y, int z, long at) {
        FarmGuard g = guard;
        if (g != null) {
            g.placed(world, blockId, x, y, z, at);
        }
    }

    /** A player dropped an item on purpose. Only fish and crops are remembered. */
    public void dropped(UUID player, String itemId, int quantity, long at) {
        FarmGuard g = guard;
        if (g != null && GatherSystems.pickupKind(itemId) != null) {
            g.dropped(player, itemId, Math.max(1, quantity), at);
        }
    }

    /** A player broke a block. Ore and trunks count unless a player placed them or the land rules deny the break. */
    public void broken(UUID player, String world, String blockId, int x, int y, int z, boolean hasPosition, long at) {
        FarmGuard g = guard;
        String kind = GatherSystems.blockKind(blockId);
        if (g == null || kind == null) {
            return;
        }
        if (hasPosition && g.wasPlaced(world, x, y, z, at)) {
            return;
        }
        if (allowed(player, world, x, z, hasPosition)) {
            progress.advance(player, kind, 1);
        }
    }

    /** A player picked an item up by hand. Fish and crops count, less what the player or a faction mate dropped. */
    public void pickedUp(UUID player, String itemId, int quantity, long at) {
        FarmGuard g = guard;
        String kind = GatherSystems.pickupKind(itemId);
        if (g == null || kind == null) {
            return;
        }
        int n = Math.max(1, quantity);
        int credited = n - g.ownDrops(player, itemId, n, at);
        if (credited > 0) {
            progress.advance(player, kind, credited);
        }
    }

    /**
     * The protection system may run after the counter and cancel the break, so a break the rules deny is not
     * counted. Only ore and trunk breaks reach this check, which keeps it cheap.
     */
    private boolean allowed(UUID player, String world, int x, int z, boolean hasPosition) {
        RulesService rules = ctx.serviceOrNull(RulesService.class);
        if (rules == null || !hasPosition) {
            return true;
        }
        return rules.canAct(player, FactionAction.BUILD, world, Keys.chunkAtBlock(x, z)).success();
    }
}
