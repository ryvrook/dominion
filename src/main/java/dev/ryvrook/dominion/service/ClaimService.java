package dev.ryvrook.dominion.service;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.ClaimEntry;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionType;

import java.util.List;
import java.util.UUID;

/** Land ownership, claiming rules, zones, raid eligibility and territory tracking. */
public interface ClaimService {
    Faction ownerAt(String world, long chunkIndex);

    ClaimEntry claimAt(String world, long chunkIndex);

    int claimCount(Faction faction);

    int maxClaims(Faction faction);

    /** The chunk the player currently stands in, or null if unknown. */
    long[] playerChunk(UUID player);

    String playerWorld(UUID player);

    Result claim(UUID actor, String world, long chunkIndex);

    Result claimRadius(UUID actor, String world, long centerIndex, int radius);

    Result claimLine(UUID actor, String world, long startIndex, int dx, int dz, int length);

    Result unclaim(UUID actor, String world, long chunkIndex);

    Result unclaimRadius(UUID actor, String world, long centerIndex, int radius);

    Result unclaimAll(UUID actor, boolean confirmed);

    /** Admin claim for any faction, bypassing rules. */
    Result adminClaim(UUID actor, Faction target, String world, long chunkIndex);

    Result adminUnclaim(UUID actor, String world, long chunkIndex);

    /** Admin claim for bulk work such as imports. With quiet true no audit line or bus event is written per chunk. */
    default Result adminClaim(UUID actor, Faction target, String world, long chunkIndex, boolean quiet) {
        return adminClaim(actor, target, world, chunkIndex);
    }

    Result createZone(UUID actor, FactionType type, String name);

    /** Whether the defender is currently raidable by anyone under the enabled modes. */
    boolean raidable(Faction defender);

    /** Whether this attacker may overclaim this defender right now. Includes dominance mode. */
    Result raidableBy(Faction attacker, Faction defender, String world, long chunkIndex);

    /** Breached chunks grant attackers access during a siege window. */
    boolean breached(String world, long chunkIndex, UUID attackerFaction);

    void setBreached(String world, long chunkIndex, UUID attackerFaction, long until);

    /** Called every 500 ms. Tracks territory changes for banners, auto claim and events. */
    void territoryTick();

    /** Called once a day. Decays land of inactive factions. */
    void decayTick();

    /** Chat map rows centred on the player. */
    List<Message> chatMap(UUID player, int width, int height);

    /** Grid cells for the UI map page, row major, each a relation colour hex and tag. */
    List<String[]> gridMap(UUID player, int radius);
}
