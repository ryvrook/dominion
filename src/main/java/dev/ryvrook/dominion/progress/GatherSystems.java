package dev.ryvrook.dominion.progress;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Time;
import org.joml.Vector3i;

import java.util.UUID;

/**
 * Feeds the mine, chop, farm and fish mission counters from block breaks and item pickups.
 * The rules mirror the Feats classifier so both mods count the same things. Breaking a block a
 * player placed recently, or picking up an item the same player dropped in the last five minutes,
 * earns no credit, which the FarmGuard tracks from place and drop events.
 */
final class GatherSystems {
    private GatherSystems() {}

    /** Ore blocks count as mining, tree trunks count as chopping. */
    static String blockKind(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        if (blockId.startsWith("Ore_")) {
            return "mine";
        }
        if (blockId.startsWith("Wood_") && blockId.contains("Trunk")) {
            return "chop";
        }
        return null;
    }

    /** Fish pickups count as fishing, crop item pickups count as farming. */
    static String pickupKind(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        if (itemId.startsWith("Fish_")) {
            return "fish";
        }
        if (itemId.startsWith("Plant_Crop_") && itemId.endsWith("_Item")) {
            return "farm";
        }
        return null;
    }

    abstract static class Base<E extends EcsEvent> extends EntityEventSystem<EntityStore, E> {
        protected final ProgressService progress;

        Base(Class<E> type, ProgressService progress) {
            super(type);
            this.progress = progress;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, E event) {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
                if (player != null) {
                    handlePlayer(player.getUuid(), store, event);
                }
            } catch (Exception ignored) {
                // mission counting must never break gameplay
            }
        }

        abstract void handlePlayer(UUID player, Store<EntityStore> store, E event);
    }

    static String worldName(Store<EntityStore> store) {
        return store.getExternalData() == null || store.getExternalData().getWorld() == null
                ? null : store.getExternalData().getWorld().getName();
    }

    /** Remembers blocks players place so breaking them again earns no mission credit. */
    static final class Place extends Base<PlaceBlockEvent> {
        private final FarmGuard guard;

        Place(ProgressService progress, FarmGuard guard) {
            super(PlaceBlockEvent.class, progress);
            this.guard = guard;
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, PlaceBlockEvent event) {
            Vector3i block = event.getTargetBlock();
            if (event.isCancelled() || block == null) {
                return;
            }
            // A place that protection cancels later is remembered too, which only withholds credit.
            guard.placed(worldName(store), placedId(event.getItemInHand()), block.x, block.y, block.z, Time.now());
        }

        /** The block id the held item places, falling back to the item id, which matches for plain block items. */
        private static String placedId(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            String key = stack.getBlockKey();
            return key != null && !key.isEmpty() ? key : stack.getItemId();
        }
    }

    /** Remembers items players drop so picking them back up earns no mission credit. */
    static final class Drop extends Base<DropItemEvent.Drop> {
        private final FarmGuard guard;

        Drop(ProgressService progress, FarmGuard guard) {
            super(DropItemEvent.Drop.class, progress);
            this.guard = guard;
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, DropItemEvent.Drop event) {
            ItemStack stack = event.getItemStack();
            if (event.isCancelled() || stack == null || stack.isEmpty() || pickupKind(stack.getItemId()) == null) {
                return;
            }
            guard.dropped(player, stack.getItemId(), Math.max(1, stack.getQuantity()), Time.now());
        }
    }

    static final class Break extends Base<BreakBlockEvent> {
        private final DominionContext ctx;
        private final FarmGuard guard;

        Break(ProgressService progress, DominionContext ctx, FarmGuard guard) {
            super(BreakBlockEvent.class, progress);
            this.ctx = ctx;
            this.guard = guard;
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, BreakBlockEvent event) {
            if (event.isCancelled()) {
                return;
            }
            String kind = blockKind(event.getBlockType() == null ? null : event.getBlockType().getId());
            if (kind == null) {
                return;
            }
            Vector3i block = event.getTargetBlock();
            if (block != null && guard.wasPlaced(worldName(store), block.x, block.y, block.z, Time.now())) {
                return;
            }
            if (allowed(player, store, block)) {
                progress.advance(player, kind, 1);
            }
        }

        /**
         * The protection system may run after this one and cancel the break, so a break the rules
         * deny is not counted. Only ore and trunk breaks reach this check, which keeps it cheap.
         */
        private boolean allowed(UUID player, Store<EntityStore> store, Vector3i block) {
            RulesService rules = ctx.serviceOrNull(RulesService.class);
            if (rules == null || block == null) {
                return true;
            }
            String world = worldName(store);
            return rules.canAct(player, FactionAction.BUILD, world, Keys.chunkAtBlock(block.x, block.z)).success();
        }
    }

    static final class Pickup extends Base<InteractivelyPickupItemEvent> {
        private final FarmGuard guard;

        Pickup(ProgressService progress, FarmGuard guard) {
            super(InteractivelyPickupItemEvent.class, progress);
            this.guard = guard;
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, InteractivelyPickupItemEvent event) {
            if (event.isCancelled()) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) {
                return;
            }
            String kind = pickupKind(stack.getItemId());
            if (kind == null) {
                return;
            }
            int quantity = Math.max(1, stack.getQuantity());
            int credited = quantity - guard.ownDrops(player, stack.getItemId(), quantity, Time.now());
            if (credited > 0) {
                progress.advance(player, kind, credited);
            }
        }
    }
}
