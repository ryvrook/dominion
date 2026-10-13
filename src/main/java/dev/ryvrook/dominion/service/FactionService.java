package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;

import java.util.List;
import java.util.UUID;

/** Faction lifecycle and membership. Every mutating call validates, audits and publishes events. */
public interface FactionService {
    Faction factionOf(UUID player);

    Faction byTag(String tag);

    Faction byId(UUID id);

    List<Faction> all();

    Result create(UUID actor, String actorName, String tag, String name);

    Result disband(UUID actor, boolean confirmed);

    Result rename(UUID actor, String newTag, String newName);

    Result setDescription(UUID actor, String description);

    Result setMotd(UUID actor, String motd);

    Result setColor(UUID actor, String color);

    Result setOpen(UUID actor, boolean open);

    Result invite(UUID actor, UUID target, String targetName);

    Result uninvite(UUID actor, UUID target);

    Result join(UUID player, String playerName, String tag);

    Result requestJoin(UUID player, String playerName, String tag);

    Result acceptRequest(UUID actor, UUID target);

    Result declineRequest(UUID actor, UUID target);

    Result leave(UUID player);

    Result kick(UUID actor, UUID target);

    Result ban(UUID actor, UUID target);

    Result unban(UUID actor, UUID target);

    Result setRank(UUID actor, UUID target, Rank rank);

    Result promote(UUID actor, UUID target);

    Result demote(UUID actor, UUID target);

    Result transferLeader(UUID actor, UUID target, boolean confirmed);

    Result setTitle(UUID actor, UUID target, String title);

    Result rosterAdd(UUID actor, String name, Rank rank);

    Result rosterRemove(UUID actor, String name);

    Result setSetting(UUID actor, String key, boolean value);

    Result setRankPerm(UUID actor, Rank rank, dev.ryvrook.dominion.model.FactionAction action, boolean allowed);

    Result setRelationPerm(UUID actor, dev.ryvrook.dominion.model.Relation relation, dev.ryvrook.dominion.model.FactionAction action, boolean allowed);

    Result resetPerms(UUID actor);

    /** Admin actions bypass rank checks and are audited under the admin's name. */
    Result forceJoin(UUID admin, String adminName, UUID target, String targetName, Faction faction);

    Result forceKick(UUID admin, String adminName, UUID target);

    Result forceLeader(UUID admin, String adminName, Faction faction, UUID target);

    Result forceDisband(UUID admin, String adminName, Faction faction);

    Result forceRename(UUID admin, String adminName, Faction faction, String tag, String name);

    Result setRankName(UUID actor, Rank rank, String name);

    Result zoneCreate(UUID actor, String name);

    Result zoneDelete(UUID actor, String zoneId);

    /** Assigns the actor's current chunk to a zone, or clears it when zoneId is null. */
    Result zoneAssign(UUID actor, String zoneId);

    Result zoneSetPerm(UUID actor, String zoneId, Rank rank, dev.ryvrook.dominion.model.Relation relation, dev.ryvrook.dominion.model.FactionAction action, Boolean allowed);

    /** Removes every override on a zone so it follows the faction grid again. */
    Result zoneReset(UUID actor, String zoneId);

    /** Appends an audit line and marks the faction dirty. */
    void audit(Faction faction, UUID who, String whoName, String category, String text);

    /** True when the actor holds the rank permission or is the leader. Also enforces the new member hold. */
    boolean can(UUID actor, dev.ryvrook.dominion.model.FactionAction action);

    /** Called once a minute. Kicks inactive members and disbands inactive factions. */
    void inactivityTick();

    /** Records a login or logout so last seen and alerts stay current. */
    void onPlayerSeen(UUID player, String name, boolean online);
}
