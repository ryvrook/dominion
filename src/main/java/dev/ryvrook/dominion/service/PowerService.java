package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.model.Faction;

import java.util.UUID;

/** Player and faction power. */
public interface PowerService {
    double powerOf(UUID player);

    double maxPowerOf(UUID player);

    double factionPower(Faction faction);

    double factionMaxPower(Faction faction);

    /** Current over maximum, 0 to 1. */
    double factionRatio(Faction faction);

    void setPower(UUID player, double value);

    void addPower(UUID player, double delta);

    /** Applies the death penalty if the world and grace rules allow it. */
    void onDeath(UUID player, String world, boolean inWarzone);

    /** Called once a minute. Regen for online players and decay for offline ones. */
    void tick();
}
