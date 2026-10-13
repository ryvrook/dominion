package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;

import java.util.UUID;

/** Faction home, warps, stuck and teleport warmups. */
public interface HomeService {
    Result setHome(UUID actor);

    Result delHome(UUID actor);

    Result home(UUID player);

    /** Teleport to an ally faction's home when that faction allows it. */
    Result allyHome(UUID player, String allyTag);

    /** Location to respawn at when the faction setting homeOnDeath is on, otherwise null. */
    dev.ryvrook.dominion.model.Location respawnLocation(UUID player);

    Result setWarp(UUID actor, String name, String password);

    Result delWarp(UUID actor, String name);

    Result warp(UUID player, String name, String password);

    Result stuck(UUID player);

    /** Called every second. Advances warmups and cancels on movement or damage. */
    void tick();

    void onDamaged(UUID player);
}
