package dev.ryvrook.dominion.service;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Listing;

import java.util.List;
import java.util.UUID;

/** Market board, buy orders, auctions, storefronts and the mailbox. */
public interface MarketService {
    boolean enabled();

    List<Listing> browse(Listing.Kind kind, String search, String sort, int page, int pageSize);

    int count(Listing.Kind kind, String search);

    List<Listing> mine(UUID player);

    List<Listing> storefront(Faction faction);

    Listing byId(UUID id);

    /** Lists the item the player holds in hand, or a slot of their inventory, for the given price. */
    Result sellHeld(UUID player, double price, int amount);

    Result sellStack(UUID player, ItemStack stack, double price);

    Result buy(UUID buyer, UUID listingId, int amount);

    Result cancel(UUID player, UUID listingId);

    Result placeOrder(UUID player, String itemId, double pricePerItem, int amount);

    Result fulfilOrder(UUID seller, UUID orderId, int amount);

    Result auctionHeld(UUID player, double startingBid, double buyout, int hours);

    Result bid(UUID bidder, UUID auctionId, double amount);

    Result setStorefront(UUID actor, UUID listingId, boolean pinned);

    /** Puts items and coins into a player mailbox, used by other modules such as the vault. */
    void mail(UUID player, List<ItemStack> stacks, double coins);

    /** Leaves a short text note in a player mailbox, shown when they collect. */
    default void mailNote(UUID player, String text) {}

    /** Items and coins waiting for the player. */
    List<ItemStack> mailbox(UUID player);

    double mailboxCoins(UUID player);

    /** Moves everything from the mailbox into the player's inventory and balance. */
    Result collect(UUID player);

    /** Price history points for an item id, newest last, as epoch millis and price pairs. */
    List<double[]> priceHistory(String itemId, int limit);

    /** Called once a minute. Expires listings and closes auctions. */
    void tick();
}
