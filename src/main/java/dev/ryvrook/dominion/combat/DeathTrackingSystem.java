package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Member;
import dev.ryvrook.dominion.service.ChatService;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.SiegeService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;

import java.util.UUID;
import java.util.logging.Level;

/** Player deaths. Applies the power penalty, counts kills and deaths, pays kill rewards and informs sieges and missions. */
final class DeathTrackingSystem extends DeathSystems.OnDeathSystem {
    private final DominionContext ctx;

    DeathTrackingSystem(DominionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        try {
            record(ref, death, store);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion death tracking failed");
        }
    }

    private void record(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store) {
        PlayerRef victim = Ecs.player(store, ref);
        if (victim == null) {
            return;
        }
        UUID victimId = victim.getUuid();
        String world = Ecs.worldName(store, victim);
        long chunk = Ecs.chunkOf(store, ref);
        if (chunk == Long.MIN_VALUE) {
            chunk = Players.chunkOf(victim);
        }
        Faction zone = world == null || chunk == Long.MIN_VALUE ? null : ctx.repo().ownerAt(world, chunk);
        boolean inWarzone = zone != null && zone.type() == FactionType.WARZONE;
        // Nobody can be hurt by a player in a SafeZone, so a death there is the world's doing and costs no power.
        boolean inSafezone = zone != null && zone.type() == FactionType.SAFEZONE;

        PowerService power = ctx.serviceOrNull(PowerService.class);
        if (power != null && !inSafezone) {
            power.onDeath(victimId, world == null ? "" : world, inWarzone);
        }
        ctx.player(victimId).addDeath();
        // Death ends Dominion's own combat tag, the same way CombatLog ends its tag on death.
        ctx.player(victimId).setCombatTagUntil(0L);
        Faction victimFaction = factionOf(victimId);
        bump(victimFaction, victimId, false);
        siegeDeath(victimId, world, chunk);

        PlayerRef killer = killer(death, store, ref);
        if (killer == null) {
            return;
        }
        UUID killerId = killer.getUuid();
        ctx.player(killerId).addKill();
        Faction killerFaction = factionOf(killerId);
        bump(killerFaction, killerId, true);
        // The kill missions ask for players from other factions, so team kills and factionless victims do not count.
        if (killerFaction != null && victimFaction != null && !killerFaction.id().equals(victimFaction.id())) {
            progressKill(killerId, killerFaction);
        }
        playerKill(killerId, victimId);

        String line = victim.getUsername() + " was slain by " + killer.getUsername() + ".";
        String reward = reward(victimFaction, killerFaction, victim.getUsername(), killer.getUsername());
        ChatService chat = ctx.serviceOrNull(ChatService.class);
        if (chat == null) {
            return;
        }
        if (victimFaction != null) {
            chat.alert(victimFaction, Msg.warn(line));
        }
        if (killerFaction != null && (victimFaction == null || !killerFaction.id().equals(victimFaction.id()))) {
            chat.alert(killerFaction, Msg.good(reward.isEmpty() ? line : line + " " + reward));
        }
    }

    private static PlayerRef killer(DeathComponent death, Store<EntityStore> store, Ref<EntityStore> victim) {
        Damage info = death.getDeathInfo();
        if (info == null || !(info.getSource() instanceof Damage.EntitySource source)) {
            return null;
        }
        Ref<EntityStore> k = source.getRef();
        if (k == null || !k.isValid() || k.equals(victim)) {
            return null;
        }
        return Ecs.player(store, k);
    }

    /** Adds a kill or death to the faction member record. Synchronised on the faction lock. */
    private static void bump(Faction f, UUID player, boolean kill) {
        if (f == null) {
            return;
        }
        synchronized (f) {
            Member m = f.member(player);
            if (m == null) {
                return;
            }
            if (kill) {
                m.setKills(m.kills() + 1);
            } else {
                m.setDeaths(m.deaths() + 1);
            }
            f.putMember(m);
        }
    }

    /** Moves the configured share of the victim bank to the killer bank. Returns a line for the killers. */
    private String reward(Faction victim, Faction killer, String victimName, String killerName) {
        double percent = ctx.settings().decimal("combat.killRewardPercent");
        if (percent <= 0 || victim == null || killer == null || victim.id().equals(killer.id())
                || victim.isSystem() || killer.isSystem()) {
            return "";
        }
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        if (eco == null || !eco.enabled()) {
            return "";
        }
        double amount = Math.floor(victim.bank() * percent) / 100.0;
        if (amount <= 0) {
            return "";
        }
        // The economy moves the money under its ledger lock and audits both banks.
        Result moved = eco.bankTransfer(victim, killer, amount,
                "the death of " + victimName + " at the hands of " + killerName);
        if (moved.failed()) {
            return "";
        }
        return killer.tag() + " took " + eco.format(amount) + " from the bank of " + victim.tag() + ".";
    }

    /** Tells the siege module about the death so its raid log stays accurate. */
    private void siegeDeath(UUID victim, String world, long chunk) {
        SiegeService siege = ctx.serviceOrNull(SiegeService.class);
        if (siege == null || world == null || chunk == Long.MIN_VALUE) {
            return;
        }
        try {
            siege.onDeath(victim, world, chunk);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion siege death hook failed");
        }
    }

    /** Lets the events module pay bounties. */
    private void playerKill(UUID killer, UUID victim) {
        EventsService events = ctx.serviceOrNull(EventsService.class);
        if (events == null) {
            return;
        }
        try {
            events.onPlayerKill(killer, victim);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion events kill hook failed");
        }
    }

    private void progressKill(UUID killer, Faction faction) {
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress == null) {
            return;
        }
        try {
            // The outpost missions boost multiplies the reward when the mission completes, so progress counts one per kill.
            progress.advance(killer, "kill", 1);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion kill mission update failed");
        }
    }

    private Faction factionOf(UUID player) {
        FactionService fs = ctx.serviceOrNull(FactionService.class);
        if (fs != null) {
            return fs.factionOf(player);
        }
        return ctx.repo().faction(ctx.player(player).factionId());
    }
}
