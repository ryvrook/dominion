package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.service.HomeService;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;

/**
 * Runs in the damage filter group. Cancels player hits the PvP rules forbid, protects creatures
 * in claimed land through DAMAGE_ENTITY, and combat tags both players on allowed hits.
 */
final class PvpFilterSystem extends DamageEventSystem {
    private final DominionContext ctx;
    private final RulesServiceImpl rules;
    private final Denials denials;

    PvpFilterSystem(DominionContext ctx, RulesServiceImpl rules, Denials denials) {
        this.ctx = ctx;
        this.rules = rules;
        this.denials = denials;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, Damage damage) {
        if (damage.isCancelled() || !(damage.getSource() instanceof Damage.EntitySource source)) {
            return;
        }
        Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
        Ref<EntityStore> attackerRef = source.getRef();
        if (attackerRef == null || !attackerRef.isValid() || attackerRef.equals(victimRef)) {
            return;
        }
        PlayerRef attacker = Ecs.player(store, attackerRef);
        if (attacker == null) {
            return;
        }
        String world = Ecs.worldName(store, attacker);
        PlayerRef victim = Ecs.player(store, victimRef);
        if (victim == null) {
            long at = Ecs.chunkOf(store, victimRef);
            if (at == Long.MIN_VALUE) {
                return;
            }
            Result r = rules.canAct(attacker.getUuid(), FactionAction.DAMAGE_ENTITY, world, at);
            if (r.failed()) {
                damage.setCancelled(true);
                denials.tell(attacker, r.message());
            }
            return;
        }
        long at = Ecs.chunkOf(store, victimRef);
        if (at == Long.MIN_VALUE) {
            at = Players.chunkOf(victim);
        }
        Result r = rules.pvp(attacker.getUuid(), victim.getUuid(), world, at, Ecs.chunkOf(store, attackerRef));
        if (r.failed()) {
            damage.setCancelled(true);
            denials.tell(attacker, r.message());
            return;
        }
        long until = Time.now() + ctx.settings().integer("combat.tagSeconds") * 1000L;
        ctx.player(attacker.getUuid()).setCombatTagUntil(until);
        ctx.player(victim.getUuid()).setCombatTagUntil(until);
        HomeService homes = ctx.serviceOrNull(HomeService.class);
        if (homes != null) {
            homes.onDamaged(victim.getUuid());
        }
    }
}
