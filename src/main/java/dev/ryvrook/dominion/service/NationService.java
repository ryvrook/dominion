package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Nation;

import java.util.List;
import java.util.UUID;

/** Nations are groups of factions that are allies of each other. */
public interface NationService {
    Nation nationOf(Faction faction);

    Nation byTag(String tag);

    List<Nation> all();

    Result create(UUID actor, String tag, String name);

    Result invite(UUID actor, String factionTag);

    Result join(UUID actor, String nationTag);

    Result leave(UUID actor);

    Result kick(UUID actor, String factionTag);

    Result disband(UUID actor, boolean confirmed);

    Result setLeaderFaction(UUID actor, String factionTag);

    /** True when both factions are in the same nation. */
    boolean sameNation(Faction a, Faction b);
}
