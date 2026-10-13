package dev.ryvrook.dominion.api;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Relation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read API for other mods. Obtain it with {@code DominionApi.get()} once Dominion has
 * started. Everything here is safe to call from any thread.
 */
public interface DominionApi {
    static DominionApi get() {
        return ApiHolder.get();
    }

    Optional<Faction> factionOf(UUID player);

    Optional<Faction> factionByTag(String tag);

    Optional<Faction> factionAt(String world, int blockX, int blockZ);

    Relation relationBetween(UUID playerA, UUID playerB);

    double powerOf(UUID player);

    double factionPower(UUID factionId);

    int claimCount(UUID factionId);

    boolean raidable(UUID factionId);

    boolean canAct(UUID player, FactionAction action, String world, int blockX, int blockY, int blockZ);

    List<Faction> top(int limit);

    /** Combined outpost boost multiplier of a kind (xp, missions, tax, sell) for the player's faction, 1.0 when none. */
    double boostFor(UUID player, String kind);

    TerritoryProvider territoryProvider();

    EventBus events();
}
