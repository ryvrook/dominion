package dev.ryvrook.dominion.command;

import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.text.Messages;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Dangerous actions wait here until the player types /f confirm or thirty seconds pass. */
public final class Confirmations {
    public static final long TIMEOUT_MILLIS = 30_000L;

    private record Pending(String label, Supplier<Result> action, long expiresAt) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void put(UUID player, String label, Supplier<Result> action) {
        pending.put(player, new Pending(label, action, System.currentTimeMillis() + TIMEOUT_MILLIS));
    }

    /** Runs the pending action if it is still valid. */
    public Result confirm(UUID player) {
        Pending p = pending.remove(player);
        if (p == null || p.expiresAt() < System.currentTimeMillis()) {
            return Result.fail(Messages.get(CommandText.YOU_HAVE_NOTHING_WAITING_TO_BE));
        }
        return p.action().get();
    }

    public void clear(UUID player) {
        pending.remove(player);
    }

    /** Drops expired entries. Called once a minute. */
    public void sweep() {
        long now = System.currentTimeMillis();
        pending.values().removeIf(p -> p.expiresAt() < now);
    }
}
