package dev.ryvrook.dominion.api;

import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Relation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Public API for other mods. Everything here is safe to call from any thread.
 *
 * <p>Bind from {@link #onEnable(Consumer)}, which runs at once when Dominion is already up and again every time it
 * starts, and drop your state in {@link #onDisable(Runnable)}, which runs first thing when Dominion stops. A
 * {@code /plugin reload} of Dominion loads new copies of these classes that your plugin does not see, so two seconds
 * after it started again Dominion reloads every enabled plugin that lists {@code Ryvrook:Dominion} under its
 * dependencies or optional dependencies, and your plugin registers its hooks on the new copy.
 *
 * <p>Methods are only ever added. The ones added after 0.6.2 have default bodies, so a stand in written against
 * an older jar still compiles. Read {@link #API_VERSION} by reflection when you need a newer method, the compiled
 * constant is the one you built against.
 */
public interface DominionApi {
    /**
     * Raised whenever a method, an event or a record component is added. 1 was 0.6.2 and older. 2 added the
     * hooks, {@link #factions()}, the sieges, faction chat and the staff actions. 3 added the
     * {@link DominionEvent.MemberRankChanged} event.
     */
    int API_VERSION = 3;

    /** The running API. Throws IllegalStateException before Dominion started. */
    static DominionApi get() {
        return ApiHolder.get();
    }

    /** The running API, or null when Dominion is not started. Since API version 2. */
    static DominionApi find() {
        return ApiHolder.find();
    }

    /** True while Dominion is up. Since API version 2. */
    static boolean isPresent() {
        return ApiHolder.find() != null;
    }

    /** Runs the hook now when Dominion is up, and every time it starts. Since API version 2. */
    static void onEnable(Consumer<DominionApi> hook) {
        ApiHolder.onEnable(hook);
    }

    /** Runs the hook every time Dominion stops, before any module shuts down. Since API version 2. */
    static void onDisable(Runnable hook) {
        ApiHolder.onDisable(hook);
    }

    /** Removes hooks added with {@link #onEnable} and {@link #onDisable}. Since API version 2. */
    static void removeHooks(Consumer<DominionApi> enable, Runnable disable) {
        ApiHolder.removeHooks(enable, disable);
    }

    Optional<Faction> factionOf(UUID player);

    Optional<Faction> factionByTag(String tag);

    Optional<Faction> factionAt(String world, int blockX, int blockZ);

    Relation relationBetween(UUID playerA, UUID playerB);

    double powerOf(UUID player);

    double factionPower(UUID factionId);

    int claimCount(UUID factionId);

    boolean raidable(UUID factionId);

    boolean canAct(UUID player, FactionAction action, String world, int blockX, int blockY, int blockZ);

    /** Normal factions in ranking order. Safe zones and war zones are left out, {@link #factions()} has them. */
    List<Faction> top(int limit);

    /** Combined outpost boost multiplier of a kind (xp, missions, tax, sell) for the player's faction, 1.0 when none. */
    double boostFor(UUID player, String kind);

    TerritoryProvider territoryProvider();

    EventBus events();

    // ---- since API version 2 -------------------------------------------------------------------------------

    /** Every faction, safe zones and war zones included, sorted by tag. Read {@code Faction.type()} to tell them apart. */
    default List<Faction> factions() {
        return List.of();
    }

    /** One faction by id, zones included. */
    default Optional<Faction> factionById(UUID factionId) {
        return Optional.empty();
    }

    /** Sieges running now, oldest first, with both sides and the phase. Empty while the siege module is off. */
    default List<SiegeInfo> sieges() {
        return List.of();
    }

    /** Finished sieges a faction attacked or defended, newest first, at most limit of them. */
    default List<SiegeInfo> siegeHistory(UUID factionId, int limit) {
        return List.of();
    }

    /**
     * Sends a line to the online members of a faction as faction chat, under the given sender name, such as a
     * Discord user. The line is published as a {@link DominionEvent.ChatLine} with a null sender, so a relay that
     * mirrors chat lines can skip its own. Fails while the chat channels are off, for an unknown faction or an
     * empty text.
     */
    default Result sendFactionChat(UUID factionId, String name, String text) {
        return Result.fail("Not available");
    }

    /**
     * Staff action, disbands a faction the way {@code /fa force disband} does, bank payout included. by is the
     * name written to the faction log and the server log, such as {@code RestAPI key ops}.
     */
    default Result disband(UUID factionId, String by) {
        return Result.fail("Not available");
    }

    /** Staff action, makes a member the leader, as {@code /fa force leader}. */
    default Result setLeader(UUID factionId, UUID player, String by) {
        return Result.fail("Not available");
    }

    /** Staff action, sets a known player's power, clamped to their maximum, as {@code /fa power set}. */
    default Result setPower(UUID player, double power, String by) {
        return Result.fail("Not available");
    }

    /** Staff action, releases one claimed chunk of any faction, as {@code /fa unclaim}. */
    default Result unclaim(String world, int chunkX, int chunkZ, String by) {
        return Result.fail("Not available");
    }

    /** Staff action, releases every chunk a faction holds in every world. The message names the count. */
    default Result unclaimAll(UUID factionId, String by) {
        return Result.fail("Not available");
    }
}
