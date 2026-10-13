package dev.ryvrook.dominion.service;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;

import java.util.UUID;

/** Faction item vaults as virtual chest pages. */
public interface VaultService {
    int pages(Faction faction);

    /** Opens the given page as a container window for the player. */
    Result open(UUID player, int page);

    /** Item in a vault slot without removing it, or null. Pages and slots are 1 based. */
    com.hypixel.hytale.server.core.inventory.ItemStack peekSlot(Faction faction, int page, int slot);

    /** Removes and returns the item in a vault slot, or null. Must be called on a world thread or the scheduler, the vault handles locking. */
    com.hypixel.hytale.server.core.inventory.ItemStack takeSlot(Faction faction, int page, int slot);

    /** Called when a faction disbands so items are dropped or returned. */
    void onDisband(Faction faction, UUID leader);
}
