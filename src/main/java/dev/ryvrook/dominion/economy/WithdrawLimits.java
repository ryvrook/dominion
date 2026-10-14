package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.model.Faction;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Tracks how much each non leader withdrew from the bank today. Stored in faction stats so the
 * limit survives restarts. Callers hold the ledger lock.
 */
final class WithdrawLimits {
    private WithdrawLimits() {}

    private static String dayKey(UUID player) {
        return "eco.withdrawDay." + player;
    }

    private static String centsKey(UUID player) {
        return "eco.withdrawCents." + player;
    }

    static long today() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    /** Amount already withdrawn today in cents. */
    static long usedToday(Faction faction, UUID player) {
        return used(faction, player, today());
    }

    /** Amount withdrawn on the given epoch day in cents. */
    static long used(Faction faction, UUID player, long day) {
        if (faction.stat(dayKey(player)) != day) {
            return 0;
        }
        return Math.max(0, faction.stat(centsKey(player)));
    }

    /** Adds to today's total. A negative amount gives allowance back, never below zero. */
    static void record(Faction faction, UUID player, double amount) {
        record(faction, player, amount, today());
    }

    static void record(Faction faction, UUID player, double amount, long day) {
        long used = used(faction, player, day);
        set(faction, dayKey(player), day);
        set(faction, centsKey(player), Math.max(0, used + Money.cents(amount)));
    }

    private static void set(Faction faction, String key, long value) {
        faction.addStat(key, value - faction.stat(key));
    }
}
