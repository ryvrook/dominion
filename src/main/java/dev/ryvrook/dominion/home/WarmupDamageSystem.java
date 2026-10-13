package dev.ryvrook.dominion.home;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Cancels a pending home, warp or stuck teleport when the player takes any damage that survived
 * the filter group, including falls, mobs and the environment. The combat module only reports
 * player hits, so this covers the rest.
 */
final class WarmupDamageSystem extends DamageEventSystem {
    private final HomeServiceImpl homes;

    WarmupDamageSystem(HomeServiceImpl homes) {
        this.homes = homes;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0 || !homes.hasPending()) {
            return;
        }
        PlayerRef victim = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
        if (victim != null) {
            homes.onDamaged(victim.getUuid());
        }
    }
}
