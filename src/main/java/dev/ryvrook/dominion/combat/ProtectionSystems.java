package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.ICancellableEcsEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import org.joml.Vector3i;

/** Block and entity protection. Each system asks the rules service and cancels on deny. */
final class ProtectionSystems {
    private ProtectionSystems() {}

    /** Where an action happens and what kind of action it is. */
    private record Target(FactionAction action, long chunk) {}

    abstract static class Guard<E extends EcsEvent & ICancellableEcsEvent> extends EntityEventSystem<EntityStore, E> {
        private final RulesServiceImpl rules;
        private final Denials denials;

        Guard(Class<E> type, RulesServiceImpl rules, Denials denials) {
            super(type);
            this.rules = rules;
            this.denials = denials;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, E event) {
            if (event.isCancelled()) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = Ecs.player(store, ref);
            if (player == null) {
                return;
            }
            Target target = target(event, store, ref);
            if (target == null || target.chunk() == Long.MIN_VALUE) {
                return;
            }
            String world = Ecs.worldName(store, player);
            Result r = rules.canAct(player.getUuid(), target.action(), world, target.chunk());
            if (r.failed()) {
                event.setCancelled(true);
                denials.tell(player, r.message());
            } else if (reportsBreach()) {
                rules.reportBreach(player.getUuid(), target.action(), world, target.chunk());
            }
        }

        abstract Target target(E event, Store<EntityStore> store, Ref<EntityStore> actor);

        /** Whether an allowed action here counts as a breach action for sieges. Block damage hits do not. */
        boolean reportsBreach() {
            return true;
        }
    }

    private static long chunkOf(Vector3i block) {
        return block == null ? Long.MIN_VALUE : Keys.chunkAtBlock(block.x, block.z);
    }

    static boolean isContainer(BlockType type) {
        String id = type == null ? null : type.getId();
        return id != null && (id.contains("Chest") || id.startsWith("Container"));
    }

    static final class BreakGuard extends Guard<BreakBlockEvent> {
        BreakGuard(RulesServiceImpl rules, Denials denials) {
            super(BreakBlockEvent.class, rules, denials);
        }

        @Override
        Target target(BreakBlockEvent event, Store<EntityStore> store, Ref<EntityStore> actor) {
            return new Target(FactionAction.BUILD, chunkOf(event.getTargetBlock()));
        }
    }

    static final class PlaceGuard extends Guard<PlaceBlockEvent> {
        PlaceGuard(RulesServiceImpl rules, Denials denials) {
            super(PlaceBlockEvent.class, rules, denials);
        }

        @Override
        Target target(PlaceBlockEvent event, Store<EntityStore> store, Ref<EntityStore> actor) {
            return new Target(FactionAction.BUILD, chunkOf(event.getTargetBlock()));
        }
    }

    static final class DamageBlockGuard extends Guard<DamageBlockEvent> {
        DamageBlockGuard(RulesServiceImpl rules, Denials denials) {
            super(DamageBlockEvent.class, rules, denials);
        }

        @Override
        Target target(DamageBlockEvent event, Store<EntityStore> store, Ref<EntityStore> actor) {
            return new Target(FactionAction.BUILD, chunkOf(event.getTargetBlock()));
        }

        @Override
        boolean reportsBreach() {
            return false;
        }
    }

    static final class UseBlockGuard extends Guard<UseBlockEvent.Pre> {
        UseBlockGuard(RulesServiceImpl rules, Denials denials) {
            super(UseBlockEvent.Pre.class, rules, denials);
        }

        @Override
        Target target(UseBlockEvent.Pre event, Store<EntityStore> store, Ref<EntityStore> actor) {
            FactionAction action = isContainer(event.getBlockType()) ? FactionAction.CONTAINER : FactionAction.INTERACT;
            return new Target(action, chunkOf(event.getTargetBlock()));
        }
    }

    static final class UseEntityGuard extends Guard<UseEntityEvent.Pre> {
        UseEntityGuard(RulesServiceImpl rules, Denials denials) {
            super(UseEntityEvent.Pre.class, rules, denials);
        }

        @Override
        Target target(UseEntityEvent.Pre event, Store<EntityStore> store, Ref<EntityStore> actor) {
            long chunk = Ecs.chunkOf(store, event.getTargetEntity());
            if (chunk == Long.MIN_VALUE) {
                chunk = Players.chunkOf(Ecs.player(store, actor));
            }
            return new Target(FactionAction.DAMAGE_ENTITY, chunk);
        }
    }
}
