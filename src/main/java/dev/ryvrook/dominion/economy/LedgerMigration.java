package dev.ryvrook.dominion.economy;

import com.hypixel.hytale.server.core.HytaleServer;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.PlayerData;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleFunction;
import java.util.logging.Level;

/**
 * Moves balances from the internal ledger into VaultUnlocked while the bridge is connected and
 * eco.provider is vaultunlocked. It runs once per start when the bridge first connects and then at
 * most once a day, so balances earned while the bridge was missing move over later. Every player
 * file with a positive balance is zeroed and saved, then the amount is deposited in the external
 * wallet. A refused deposit puts the amount back in the ledger for the next run. A summary file is
 * written after each run for admins but never stops a later run.
 */
final class LedgerMigration {
    private static final double CENT = 0.005;

    private final DominionContext ctx;
    private final Object lock;
    private final DoubleFunction<String> format;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long lastRunAt;

    LedgerMigration(DominionContext ctx, Object lock, DoubleFunction<String> format) {
        this.ctx = ctx;
        this.lock = lock;
        this.format = format;
    }

    private Path summary() {
        return ctx.repo().root().resolve("economy").resolve("vaultunlocked-migrated.json");
    }

    /**
     * Starts a migration in the background when none ran since this start or the last one was a
     * day ago or more, and none is running now.
     */
    void maybeRun(BalanceProvider external) {
        long now = System.currentTimeMillis();
        long last = lastRunAt;
        if (last != 0 && now - last < DAY_MS) {
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
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not schedule the VaultUnlocked balance migration");
            running.set(false);
        }
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
            try {
                PlayerData p = ctx.player(id);
                double amount;
                synchronized (lock) {
                    amount = Money.round(p.balance());
                    if (amount < CENT) {
                        continue;
                    }
                    p.setBalance(0);
                }
                // Saved as zero before the deposit, so a crash can never pay the same balance twice.
                ctx.repo().savePlayer(p);
                Result r;
                try {
                    r = external.deposit(id, amount, "Dominion balance migration");
                } catch (RuntimeException e) {
                    r = Result.fail(e.getMessage() == null ? "error" : e.getMessage());
                }
                if (r.success()) {
                    moved++;
                    total += amount;
                } else {
                    failed++;
                    synchronized (lock) {
                        p.setBalance(Money.round(p.balance() + amount));
                    }
                    ctx.repo().savePlayer(p);
                    ctx.logger().at(Level.WARNING).log("Dominion could not move %s for %s into VaultUnlocked, %s",
                            format.apply(amount), id, r.message());
                }
            } catch (RuntimeException e) {
                failed++;
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion balance migration failed for %s", id);
            } finally {
                if (!wasLoaded && !Players.isOnline(id)) {
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
                ctx.logger().at(Level.INFO).log("Dominion moved %d internal balances worth %s into VaultUnlocked", moved, format.apply(total));
            }
        } else {
            ctx.logger().at(Level.WARNING).log(
                    "Dominion moved %d internal balances worth %s into VaultUnlocked and %d failed. The failed ones stay in the internal ledger and are retried in a day or on the next start",
                    moved, format.apply(total), failed);
        }
    }
}
