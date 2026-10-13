package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Relation;

import java.util.UUID;

/** Diplomacy between factions. */
public interface RelationService {
    Relation between(Faction a, Faction b);

    /** Relation of the viewer's faction to the other player's faction. OWN for same faction, NEUTRAL when either has none. */
    Relation betweenPlayers(UUID viewer, UUID other);

    Relation playerToFaction(UUID viewer, Faction target);

    /** Requests ally or truce, sets enemy or neutral directly. */
    Result request(UUID actor, String targetTag, Relation relation);

    Result accept(UUID actor, String targetTag);

    Result decline(UUID actor, String targetTag);

    /** Called once a minute. Expires stale requests. */
    void tick();
}
