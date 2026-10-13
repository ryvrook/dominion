package dev.ryvrook.dominion.service;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;

import java.util.List;

/** Admin shops with infinite stock and faction shops stocked from the vault. */
public interface ShopService {
    List<String> adminCategories();

    /** Opens the admin shop page, optionally on a category. */
    void openAdminShop(PlayerRef player, String category);

    /** Opens the admin shop editor page. Caller checks the admin permission. */
    void openAdminEditor(PlayerRef player);

    Result adminSet(String category, String itemId, double buyPrice, double sellPrice);

    Result adminRemove(String category, String itemId);

    /** Opens a faction's shop page for a buyer. */
    void openFactionShop(PlayerRef player, Faction faction);

    /** Opens the faction shop manager for an officer. */
    void openFactionShopManager(PlayerRef player);

    /** Stocks the item from a vault page and slot, both 1 based. */
    Result factionStock(java.util.UUID actor, int vaultPage, int slot, double price);

    /** Stocks the item the officer holds in hand. */
    Result factionStockHeld(java.util.UUID actor, double price);

    Result factionUnstock(java.util.UUID actor, String entryId);
}
