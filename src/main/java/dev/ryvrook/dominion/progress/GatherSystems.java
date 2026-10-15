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
import dev.ryvrook.dominion.util.Time;
import org.joml.Vector3i;

import java.util.UUID;

/**
 * Dominion's own feed of the mine, chop, farm and fish mission counters, from block breaks and item pickups.
 * The rules mirror the Feats classifier so both mods count the same things. Breaking a block a player placed
 * recently, or picking up an item the player or a faction mate dropped, earns no credit, which the FarmGuard
 * tracks from place and drop events. While TrackerAPI is bound and tracks all four kinds, {@link GatherFeed}
 * takes its events instead and these systems return at once.
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
        protected final GatherFeed feed;

        Base(Class<E> type, GatherFeed feed) {
            super(type);
            this.feed = feed;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, E event) {
            if (feed.external()) {
                return;
            }
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
                if (player != null) {
                    handlePlayer(player.getUuid(), store, event);
                }
            } catch (RuntimeException ignored) {
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
        Place(GatherFeed feed) {
            super(PlaceBlockEvent.class, feed);
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, PlaceBlockEvent event) {
            Vector3i block = event.getTargetBlock();
            if (event.isCancelled() || block == null) {
                return;
            }
            // A place that protection cancels later is remembered too, which only withholds credit.
            feed.placed(worldName(store), placedId(event.getItemInHand()), block.x, block.y, block.z, Time.now());
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
        Drop(GatherFeed feed) {
            super(DropItemEvent.Drop.class, feed);
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, DropItemEvent.Drop event) {
            ItemStack stack = event.getItemStack();
            if (event.isCancelled() || stack == null || stack.isEmpty()) {
                return;
            }
            feed.dropped(player, stack.getItemId(), stack.getQuantity(), Time.now());
        }
    }

    static final class Break extends Base<BreakBlockEvent> {
        Break(GatherFeed feed) {
            super(BreakBlockEvent.class, feed);
        }

        @Override
        void handlePlayer(UUID player, Store<EntityStore> store, BreakBlockEvent event) {
            if (event.isCancelled()) {
                return;
            }
            String blockId = event.getBlockType() == null ? null : event.getBlockType().getId();
            if (blockKind(blockId) == null) {
                return;
            }
            Vector3i block = event.getTargetBlock();
            feed.broken(player, worldName(store), blockId, block == null ? 0 : block.x, block == null ? 0 : block.y,
                    block == null ? 0 : block.z, block != null, Time.now());
        }
    }

    static final class Pickup extends Base<InteractivelyPickupItemEvent> {
        Pickup(GatherFeed feed) {
            super(InteractivelyPickupItemEvent.class, feed);
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
            feed.pickedUp(player, stack.getItemId(), stack.getQuantity(), Time.now());
        }
    }
}
