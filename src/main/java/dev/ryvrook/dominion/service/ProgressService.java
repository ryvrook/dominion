package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Mission;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Faction value, leaderboard, upgrades, missions, levels and strikes. */
public interface ProgressService {
    double value(Faction faction);

    /** Weighted components of the value so the panel can show them. */
    Map<String, Double> valueBreakdown(Faction faction);

    List<Faction> top(int limit);

    int rank(Faction faction);

    /** Upgrade definitions available to buy. Keys are members, regen, maxpower, warps, vault, shield, immunity. */
    List<String> upgradeKeys();

    int upgradeLevel(Faction faction, String key);

    int upgradeMaxLevel(String key);

    double upgradeCost(Faction faction, String key);

    String upgradeDescription(String key, int level);

    Result buyUpgrade(UUID actor, String key);

    List<Mission> activeMissions(boolean weekly);

    long missionProgress(Faction faction, Mission mission);

    boolean missionDone(Faction faction, Mission mission);

    /** Called by other modules when a counter advances. Kind names match Mission.kind. */
    void advance(UUID player, String kind, long amount);

    int level(Faction faction);

    int points(Faction faction);

    Result strike(UUID admin, String adminName, Faction faction, int points, String reason);

    Result unstrike(UUID admin, Faction faction, int index);

    List<dev.ryvrook.dominion.model.Strike> strikes(Faction faction);

    /** Called once a minute. Rotates missions at the day and week boundary. */
    void tick();
}
