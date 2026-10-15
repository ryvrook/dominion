package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.event.events.ecs.RespawnEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Location;
import dev.ryvrook.dominion.service.HomeService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Sends players to their faction home after death when the faction turns homeOnDeath on.
 *
 * The server picks the respawn point inside the world's RespawnController, which comes from the
 * world DeathConfig and is shared by every player in that world. Replacing it would mean editing
 * the saved world config, and PlayerRespawnPointData is the bed list players manage themselves.
 * Neither is a per player hook, so this system listens for the RespawnEvent that
 * DeathComponent.respawn fires just before the controller moves the player, then teleports the
 * player to the home one second later, once the vanilla respawn has finished. If the player is
 * still dead at that point the move is retried a few times before giving up.
 */
final class HomeRespawnSystem extends EntityEventSystem<EntityStore, RespawnEvent> {
    private static final long DELAY_MILLIS = 1000;
    private static final int ATTEMPTS = 5;

    private final DominionContext ctx;

    HomeRespawnSystem(DominionContext ctx) {
        super(RespawnEvent.class);
        this.ctx = ctx;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, RespawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        try {
            PlayerRef player = Ecs.player(store, chunk.getReferenceTo(index));
            HomeService homes = ctx.serviceOrNull(HomeService.class);
            if (player == null || homes == null) {
                return;
            }
            Location home = homes.respawnLocation(player.getUuid());
            if (home != null) {
                schedule(player, home, ATTEMPTS);
            }
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion home respawn failed");
        }
    }

    private void schedule(PlayerRef player, Location home, int attemptsLeft) {
        World world = Players.world(player);
        if (world == null || attemptsLeft <= 0) {
            return;
        }
        world.scheduleAfter(() -> {
            World now = Players.world(player);
            if (now != null) {
                now.execute(() -> move(player, home, attemptsLeft));
            }
        }, DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Runs on the world thread that owns the player entity. */
    private void move(PlayerRef player, Location home, int attemptsLeft) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            // The vanilla respawn has not finished, or another mod cancelled it. Try again shortly.
            schedule(player, home, attemptsLeft - 1);
            return;
        }
        World target = Universe.get().getWorld(home.world());
        if (target == null) {
            return;
        }
        Transform transform = new Transform(home.x(), home.y(), home.z(), home.pitch(), home.yaw(), 0f);
        store.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(target, transform));
        player.sendMessage(Msg.info(Messages.get(CombatText.RESPAWNED_HOME)));
    }
}
