package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;

import java.util.UUID;

/** Daily shield windows, grace and end of world state. */
public interface ShieldService {
    /** True when the faction's shield covers the given chunk right now. */
    boolean shielded(Faction faction, String world, long chunkIndex);

    /** True when the shield window is active for the faction regardless of chunk. */
    boolean windowActive(Faction faction);

    Result setWindow(UUID actor, int startHour);

    /** Human readable status for the panel and HUD. */
    String status(Faction faction);

    boolean graceActive();

    long graceEndsAt();

    Result setGrace(UUID admin, long endsAtMillis);

    Result endGrace(UUID admin);

    boolean eotwActive();

    Result setEotw(UUID admin, boolean on);

    /** Called once a minute. Applies pending window changes and expires grace. */
    void tick();
}
