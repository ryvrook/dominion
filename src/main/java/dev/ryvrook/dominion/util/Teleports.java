package dev.ryvrook.dominion.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.model.Location;

/** The one teleport path Dominion uses, shared by homes, warps, stuck and the SafeZone push back. */
public final class Teleports {
    private Teleports() {}

    /**
     * Attaches the Teleport component on the thread of the world that owns the player entity.
     * The target world is passed to the component so cross world teleports work too. False when
     * either world is not loaded.
     */
    public static boolean teleport(PlayerRef player, Location loc) {
        if (player == null || loc == null || loc.world() == null) {
            return false;
        }
        World target = Universe.get() == null ? null : Universe.get().getWorld(loc.world());
        World current = Players.world(player);
        if (target == null || current == null) {
            return false;
        }
        current.execute(() -> {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Transform transform = new Transform(loc.x(), loc.y(), loc.z(), loc.pitch(), loc.yaw(), 0f);
            store.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(target, transform));
        });
        return true;
    }
}
