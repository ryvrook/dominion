package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Siege;

import java.util.List;
import java.util.UUID;

/** Siege banners, capture, breach and the raid log. */
public interface SiegeService {
    /** Starts a siege against the enemy chunk adjacent to the actor. */
    Result start(UUID actor);

    Result cancel(UUID actor);

    /** Ends any active siege by id as an admin action. */
    Result adminCancel(UUID admin, String adminName, UUID siegeId);

    List<Siege> active();

    List<Siege> activeFor(Faction faction);

    /** Most recent finished sieges, newest first. */
    List<Siege> history(Faction faction, int limit);

    Siege byId(UUID id);

    /** Raid points earned by a faction from sieges, used by the leaderboard. */
    int raidPoints(Faction faction);

    /** Called every second. Advances capture and breach timers and records presence. */
    void tick();

    /** Called by combat when a player dies during a siege so the log stays accurate. */
    void onDeath(UUID player, String world, long chunkIndex);

    /** Called by combat on block break or container open in a breached chunk. */
    void onBreachAction(UUID player, String world, long chunkIndex, boolean container);
}
