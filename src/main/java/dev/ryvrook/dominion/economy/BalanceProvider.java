package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.api.Result;

import java.util.UUID;

/**
 * Where player money lives. The economy service validates amounts, then hands wallet changes to
 * the active provider. Faction banks never go through a provider. A failed Result carries a reason
 * the player can read. A passing Result carries no message because the service writes its own.
 */
interface BalanceProvider {
    /** Short id shown by providerName, internal or vaultunlocked or item. */
    String name();

    double balance(UUID player);

    /** Cheap balance for leaderboards. Providers that need a world thread return their last known value. */
    default double cachedBalance(UUID player) {
        return balance(player);
    }

    Result withdraw(UUID player, double amount, String reason);

    Result deposit(UUID player, double amount, String reason);

    /** Admin set. Providers that cannot set directly move the difference. */
    void set(UUID player, double amount);

    /** True when only whole amounts can move, as with an item currency. */
    default boolean wholeUnits() {
        return false;
    }

    /** Called when a player is ready in a world, so providers can deliver anything owed. */
    default void onReady(UUID player) {}
}
