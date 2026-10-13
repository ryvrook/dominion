package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.FactionAction;

import java.util.UUID;

/** The single decision function for protection and PvP, implementing the rules table. */
public interface RulesService {
    Result canAct(UUID player, FactionAction action, String world, int blockX, int blockY, int blockZ);

    Result canAct(UUID player, FactionAction action, String world, long chunkIndex);

    /** True when the attacker may damage the victim. */
    boolean canPvp(UUID attacker, UUID victim, String world, long victimChunk);

    boolean bypassing(UUID player);

    void setBypass(UUID player, boolean on);

    boolean graceActive();

    boolean eotwActive();
}
