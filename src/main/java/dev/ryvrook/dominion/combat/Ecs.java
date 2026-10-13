package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.util.Keys;
import dev.ryvrook.dominion.util.Players;
import org.joml.Vector3d;

/** Small helpers for reading world and position data inside ECS systems on the world thread. */
final class Ecs {
    private Ecs() {}

    static String worldName(Store<EntityStore> store, PlayerRef fallback) {
        try {
            EntityStore es = store.getExternalData();
            World w = es == null ? null : es.getWorld();
            if (w != null) {
                return w.getName();
            }
        } catch (RuntimeException ignored) {
            // fall through to the player lookup
        }
        return Players.worldName(fallback);
    }

    /** Chunk index of an entity, or Long.MIN_VALUE when it has no position. */
    static long chunkOf(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return Long.MIN_VALUE;
        }
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t == null ? null : t.getPosition();
        return p == null ? Long.MIN_VALUE : Keys.chunkAt(p.x, p.z);
    }

    static PlayerRef player(Store<EntityStore> store, Ref<EntityStore> ref) {
        return ref == null || !ref.isValid() ? null : store.getComponent(ref, PlayerRef.getComponentType());
    }
}
