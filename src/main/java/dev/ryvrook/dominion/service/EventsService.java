package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.PointOfInterest;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;

import java.util.List;
import java.util.UUID;

/** KOTH hills, outposts, envoys, bounties and the season clock. */
public interface EventsService {
    boolean enabled();

    // KOTH
    Result kothCreate(UUID admin, String name, String world, double x, double y, double z, int radius);
    Result kothDelete(UUID admin, String name);
    Result kothStart(UUID admin, String name);
    Result kothStop(UUID admin, String name);
    Result kothSchedule(UUID admin, String name, String cronLike);
    List<String> hillNames();

    List<String> outpostNames();

    /** Status lines for every hill. Each line starts with the hill name, which never contains spaces. */
    List<String> koths();
    /** Human readable status for a hill, or null. */
    String kothStatus(String name);

    // Outposts
    Result outpostCreate(UUID admin, String name, String world, double x, double y, double z, int radius, String boost, double multiplier);
    Result outpostDelete(UUID admin, String name);
    Result outpostReset(UUID admin, String name);
    /** Status lines for every outpost. Each line starts with the outpost name, which never contains spaces. */
    List<String> outposts();
    String outpostStatus(String name);
    /** The holder of an outpost, or null. */
    Faction outpostHolder(String name);
    /** Combined boost multiplier of the given kind for a faction from all outposts it holds, 1.0 when none. */
    double boostFor(Faction faction, String kind);

    // Envoys
    Result envoySpawn(UUID admin, String zoneTag);
    Result envoySchedule(UUID admin, String zoneTag, int everyMinutes);
    List<String> envoys();

    // Bounties
    Result bountyPlace(UUID placer, UUID target, double amount);
    List<String> bounties();
    /** Called by combat when a player kills another so bounties pay out. */
    void onPlayerKill(UUID killer, UUID victim);

    // Season
    Result seasonStart(UUID admin, int graceDays, int raidDays);
    Result seasonEnd(UUID admin);
    String seasonStatus();
    /** Preview lines or run the configured reward commands for the top factions. */
    List<String> payoutsPreview();
    Result payoutsRun(UUID admin);

    /** Points for maps, with kind hill, outpost or envoy. */
    List<PointOfInterest> points(String world);

    /** Called every second. */
    void tick();
}
