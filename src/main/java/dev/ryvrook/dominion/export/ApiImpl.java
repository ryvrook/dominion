package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionApi;
import dev.ryvrook.dominion.api.EventBus;
import dev.ryvrook.dominion.api.TerritoryProvider;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.FactionType;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.ClaimService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.PowerService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.service.RulesService;
import dev.ryvrook.dominion.util.Keys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Thin public API over the services, published through ApiHolder. */
public final class ApiImpl implements DominionApi {
    private final DominionContext ctx;
    private final TerritoryProvider territories;

    ApiImpl(DominionContext ctx, TerritoryProvider territories) {
        this.ctx = ctx;
        this.territories = territories;
    }

    @Override
    public Optional<Faction> factionOf(UUID player) {
        FactionService f = ctx.serviceOrNull(FactionService.class);
        return player == null || f == null ? Optional.empty() : Optional.ofNullable(f.factionOf(player));
    }

    @Override
    public Optional<Faction> factionByTag(String tag) {
        return Optional.ofNullable(ctx.repo().factionByTag(tag));
    }

    @Override
    public Optional<Faction> factionAt(String world, int blockX, int blockZ) {
        return world == null ? Optional.empty() : Optional.ofNullable(ctx.repo().ownerAt(world, Keys.chunkAtBlock(blockX, blockZ)));
    }

    @Override
    public Relation relationBetween(UUID playerA, UUID playerB) {
        RelationService r = ctx.serviceOrNull(RelationService.class);
        return r == null || playerA == null || playerB == null ? Relation.NEUTRAL : r.betweenPlayers(playerA, playerB);
    }

    @Override
    public double powerOf(UUID player) {
        PowerService p = ctx.serviceOrNull(PowerService.class);
        return p == null || player == null ? 0.0 : p.powerOf(player);
    }

    @Override
    public double factionPower(UUID factionId) {
        PowerService p = ctx.serviceOrNull(PowerService.class);
        Faction f = ctx.repo().faction(factionId);
        return p == null || f == null ? 0.0 : p.factionPower(f);
    }

    @Override
    public int claimCount(UUID factionId) {
        return factionId == null ? 0 : ctx.repo().claimCount(factionId);
    }

    @Override
    public boolean raidable(UUID factionId) {
        ClaimService c = ctx.serviceOrNull(ClaimService.class);
        Faction f = ctx.repo().faction(factionId);
        return c != null && f != null && c.raidable(f);
    }

    @Override
    public boolean canAct(UUID player, FactionAction action, String world, int blockX, int blockY, int blockZ) {
        RulesService r = ctx.serviceOrNull(RulesService.class);
        return r == null || r.canAct(player, action, world, blockX, blockY, blockZ).success();
    }

    @Override
    public List<Faction> top(int limit) {
        ProgressService p = ctx.serviceOrNull(ProgressService.class);
        if (p != null) {
            return p.top(limit);
        }
        List<Faction> list = new ArrayList<>(ctx.repo().factions(FactionType.NORMAL));
        list.sort(Comparator.comparingInt((Faction f) -> ctx.repo().claimCount(f.id())).reversed());
        return list.subList(0, Math.max(0, Math.min(limit, list.size())));
    }

    /** Reads the outpost boost of the player's faction from the events module, 1.0 when it is absent. */
    @Override
    public double boostFor(UUID player, String kind) {
        if (player == null || kind == null || kind.isBlank()) {
            return 1.0;
        }
        EventsService events = ctx.serviceOrNull(EventsService.class);
        if (events == null) {
            return 1.0;
        }
        Faction f = factionOf(player).orElse(null);
        if (f == null) {
            return 1.0;
        }
        try {
            double m = events.boostFor(f, kind);
            return Double.isFinite(m) && m > 0 ? m : 1.0;
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    @Override
    public TerritoryProvider territoryProvider() {
        return territories;
    }

    @Override
    public EventBus events() {
        return ctx.bus();
    }
}
