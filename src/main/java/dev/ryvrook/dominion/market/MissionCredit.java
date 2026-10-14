package dev.ryvrook.dominion.market;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a trade counts toward trade missions, so cheap trades between faction mates or
 * alts cannot farm them. A trade counts only when it is worth at least {@link #MIN_VALUE}, the two
 * sides are different players in different factions, the same buyer and seller pair has not
 * counted within the window, and the buyer has fewer than {@link #MAX_PER_WINDOW} counted trades in
 * the window. Shared by the market and the shops.
 */
public final class MissionCredit {
    /** Smallest trade value that counts. */
    public static final double MIN_VALUE = 10.0;
    /** Most counted trades per buyer in one window. */
    public static final int MAX_PER_WINDOW = 10;
    /** Window length in milliseconds. */
    public static final long WINDOW_MS = 60 * 60_000L;

    private final Map<UUID, Deque<Long>> byBuyer = new ConcurrentHashMap<>();
    private final Map<String, Long> byPair = new ConcurrentHashMap<>();

    /**
     * True when the trade counts, recording it when it does. Faction ids may be null for players
     * without a faction, and a null seller is the server.
     */
    public synchronized boolean counts(UUID buyer, UUID buyerFaction, UUID seller, UUID sellerFaction, double value, long now) {
        if (buyer == null || !(value >= MIN_VALUE) || buyer.equals(seller)) {
            return false;
        }
        if (buyerFaction != null && Objects.equals(buyerFaction, sellerFaction)) {
            return false;
        }
        String pair = buyer + ">" + seller;
        Long last = byPair.get(pair);
        if (last != null && now - last < WINDOW_MS) {
            return false;
        }
        Deque<Long> times = byBuyer.computeIfAbsent(buyer, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
            times.removeFirst();
        }
        if (times.size() >= MAX_PER_WINDOW) {
            return false;
        }
        times.addLast(now);
        byPair.put(pair, now);
        if (byPair.size() > 10_000) {
            byPair.values().removeIf(t -> now - t >= WINDOW_MS);
        }
        return true;
    }
}
