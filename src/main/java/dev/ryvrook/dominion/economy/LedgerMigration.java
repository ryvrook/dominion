package dev.ryvrook.dominion.economy;

import com.hypixel.hytale.server.core.HytaleServer;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleFunction;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Moves balances from the internal ledger into VaultUnlocked or Coinage while that provider is the
 * active one. It runs once per start when the bridge first connects and then at
 * most once a day, so balances earned while the bridge was missing move over later. Every player
 * file with a positive balance is zeroed and saved, then the amount is deposited in the external
 * wallet. When the zeroed file cannot be written nothing is deposited, so a crash can never pay the
 * same balance twice. A refused deposit puts the amount back in the ledger for the next run. A
 * summary file is written after each run for admins but never stops a later run.
 */
final class LedgerMigration {
    private static final double CENT = 0.005;

    private final DominionContext ctx;
    private final Object lock;
    private final DoubleFunction<String> format;
    private final String target;
    private final String file;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long lastRunAt;

    LedgerMigration(DominionContext ctx, Object lock, DoubleFunction<String> format) {
        this(ctx, lock, format, "VaultUnlocked", "vaultunlocked-migrated.json");
    }

    /**
     * @param target the provider name in log lines
     * @param file   the summary file under the economy folder
     */
    LedgerMigration(DominionContext ctx, Object lock, DoubleFunction<String> format, String target, String file) {
        this.ctx = ctx;
        this.lock = lock;
        this.format = format;
        this.target = target;
        this.file = file;
    }

    private Path summary() {
        return ctx.repo().root().resolve("economy").resolve(file);
    }

    /**
     * Starts a migration in the background when none ran since this start or the last one was a
     * day ago or more, and none is running now.
     */
    void maybeRun(BalanceProvider external) {
        long now = System.currentTimeMillis();
        if (!due(lastRunAt, now)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        lastRunAt = now;
        try {
            HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
                try {
                    run(external);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not schedule the %s balance migration", target);
            running.set(false);
        }
    }

    /** True when no run happened since this start, or the last one started a day ago or more. */
    static boolean due(long lastRunAt, long now) {
        return lastRunAt == 0 || now - lastRunAt >= DAY_MS || now < lastRunAt;
    }

    /** What happened to one player's balance. */
    enum Outcome { SKIPPED, MOVED, FAILED }

    /** The outcome for one player, the amount involved and the reason when it failed. */
    record Step(Outcome outcome, double amount, String reason) {}

    /**
     * Moves one internal balance into the external wallet. The balance is zeroed under the ledger
     * lock and the player file is saved before the deposit. When that save fails, or the deposit is
     * refused, the balance is put back, so money is never created or lost.
     *
     * @param live true while the record is the one the repository holds
     * @param save writes the player file and returns false when it could not be written
     */
    static Step moveOne(PlayerData p, Object lock, Predicate<PlayerData> live, Predicate<PlayerData> save, BalanceProvider external) {
        return moveOne(p, lock, live, save, external, Double.NaN);
    }

    /**
     * As above, skipping an untouched balance, one that never moved and still equals the starting balance, since
     * the external economy hands out its own. Old files have no touched flag and count as untouched at that amount.
     */
    static Step moveOne(PlayerData p, Object lock, Predicate<PlayerData> live, Predicate<PlayerData> save, BalanceProvider external,
                        double startingBalance) {
        double amount;
        synchronized (lock) {
            synchronized (p) {
                if (!live.test(p)) {
                    return new Step(Outcome.SKIPPED, 0, null);
                }
                amount = Money.round(p.balance());
                if (amount < CENT || untouched(p, startingBalance)) {
                    return new Step(Outcome.SKIPPED, 0, null);
                }
                p.setBalance(0);
            }
        }
        if (!save.test(p)) {
            synchronized (lock) {
                p.setBalance(Money.round(p.balance() + amount));
            }
            return new Step(Outcome.FAILED, amount, "the player file could not be saved");
        }
        Result r;
        try {
            r = external.deposit(p.uuid(), amount, Messages.get(EconomyText.REASON_MIGRATION));
        } catch (RuntimeException e) {
            r = Result.fail(e.getMessage() == null ? "error" : e.getMessage());
        }
        if (r.success()) {
            return new Step(Outcome.MOVED, amount, null);
        }
        synchronized (lock) {
            p.setBalance(Money.round(p.balance() + amount));
        }
        // A failed save leaves the player marked dirty, so the periodic save writes the restored balance.
        save.test(p);
        return new Step(Outcome.FAILED, amount, r.message());
    }


    /** True when the balance never moved and still equals the starting balance. Caller holds the record's monitor. */
    static boolean untouched(PlayerData p, double startingBalance) {
        return !Double.isNaN(startingBalance) && !p.balanceTouched()
                && Math.abs(Money.round(p.balance()) - Money.round(startingBalance)) < CENT;
    }

    private boolean readable(UUID id) {
        Document d = ctx.files().read(ctx.repo().root().resolve("players").resolve(id + ".json"));
        if (d == null) {
            return false;
        }
        try {
            PlayerData.fromDocument(id, d);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void run(BalanceProvider external) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Path p : ctx.files().list(ctx.repo().root().resolve("players"), "*.json")) {
            String file = p.getFileName().toString();
            try {
                ids.add(UUID.fromString(file.substring(0, file.length() - ".json".length())));
            } catch (IllegalArgumentException ignored) {
                // not a player file
            }
        }
        for (PlayerData p : ctx.repo().loadedPlayers()) {
            ids.add(p.uuid());
        }
        int moved = 0;
        int failed = 0;
        double total = 0;
        for (UUID id : ids) {
            boolean wasLoaded = ctx.repo().playerIfLoaded(id) != null;
            if (!wasLoaded && !readable(id)) {
                // A missing or corrupt file would load as a fresh player with the starting balance, which is not theirs to move.
                continue;
            }
            PlayerData p = null;
            try {
                p = ctx.player(id);
                Step step = moveOne(p, lock, pd -> ctx.repo().playerIfLoaded(pd.uuid()) == pd, ctx.repo()::savePlayer, external,
                        ctx.settings().decimal("eco.startingBalance"));
                if (step.outcome() == Outcome.MOVED) {
                    moved++;
                    total += step.amount();
                } else if (step.outcome() == Outcome.FAILED) {
                    failed++;
                    ctx.logger().at(Level.WARNING).log("Dominion could not move %s for %s into %s, %s",
                            format.apply(step.amount()), id, target, step.reason());
                }
            } catch (RuntimeException e) {
                failed++;
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion balance migration failed for %s", id);
            } finally {
                // A player whose restored balance could not be written stays loaded so the periodic save retries it.
                if (!wasLoaded && !Players.isOnline(id) && (p == null || !p.isDirty())) {
                    ctx.repo().unloadPlayer(id);
                }
            }
        }
        if (moved > 0 || failed > 0) {
            ctx.files().write(summary(), new Document("MigratedAt", Time.now()).append("Players", moved)
                    .append("Total", Money.round(total)).append("Failed", failed));
        }
        if (failed == 0) {
            if (moved > 0) {
                ctx.logger().at(Level.INFO).log("Dominion moved %d internal balances worth %s into %s", moved, format.apply(total), target);
            }
        } else {
            ctx.logger().at(Level.WARNING).log(
                    "Dominion moved %d internal balances worth %s into %s and %d failed. The failed ones stay in the internal ledger and are retried in a day or on the next start",
                    moved, format.apply(total), target, failed);
        }
    }
}
