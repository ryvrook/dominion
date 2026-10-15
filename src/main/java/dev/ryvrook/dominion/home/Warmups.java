package dev.ryvrook.dominion.home;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Teleport warmups, kept free of the server so the rules can be tested on their own. A warmup is
 * cancelled when the player goes offline, changes world, moves more than the tolerance, takes
 * damage or becomes combat tagged. Only the tick that removes a warmup may complete it, so a
 * cancel on another thread can never race a teleport through.
 */
final class Warmups<T> {
    static final double MOVE_TOLERANCE = 1.0;

    /** Blocks a player may move before the warmup is cancelled, home.warmupMoveTolerance. */
    private volatile double tolerance = MOVE_TOLERANCE;

    void tolerance(double blocks) {
        tolerance = blocks >= 0 ? blocks : MOVE_TOLERANCE;
    }

    /** Where a player stands. */
    record Pos(String world, double x, double y, double z) {
        double distance(Pos o) {
            double dx = x - o.x;
            double dy = y - o.y;
            double dz = z - o.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** One waiting teleport with its payload, start position and seconds left. */
    static final class Pending<T> {
        final T payload;
        final Pos start;
        volatile int secondsLeft;

        Pending(T payload, Pos start, int seconds) {
            this.payload = payload;
            this.start = start;
            this.secondsLeft = seconds;
        }
    }

    enum Kind { OFFLINE, MOVED, COMBAT, COUNTDOWN, DONE }

    /** What a tick did for one player. */
    record Step<T>(UUID player, T payload, Kind kind, int secondsLeft) {}

    private final Map<UUID, Pending<T>> pending = new ConcurrentHashMap<>();

    /** Starts or replaces a warmup. */
    void start(UUID player, T payload, Pos start, int seconds) {
        pending.put(player, new Pending<>(payload, start, seconds));
    }

    /** Cancels a warmup. True when one was waiting. */
    boolean cancel(UUID player) {
        return pending.remove(player) != null;
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    boolean waiting(UUID player) {
        return pending.containsKey(player);
    }

    /**
     * Advances every warmup by one second. where answers null for an offline player. Countdown
     * steps are reported for the last three seconds.
     */
    List<Step<T>> tick(Function<UUID, Pos> where, Predicate<UUID> inCombat) {
        List<Step<T>> out = new ArrayList<>();
        for (Map.Entry<UUID, Pending<T>> e : pending.entrySet()) {
            UUID id = e.getKey();
            Pending<T> p = e.getValue();
            Pos now = where.apply(id);
            Kind end = null;
            if (now == null) {
                end = Kind.OFFLINE;
            } else if (!p.start.world().equals(now.world()) || now.distance(p.start) > tolerance) {
                end = Kind.MOVED;
            } else if (inCombat.test(id)) {
                end = Kind.COMBAT;
            }
            if (end == null) {
                p.secondsLeft--;
                if (p.secondsLeft <= 0) {
                    end = Kind.DONE;
                } else if (p.secondsLeft <= 3) {
                    out.add(new Step<>(id, p.payload, Kind.COUNTDOWN, p.secondsLeft));
                    continue;
                } else {
                    continue;
                }
            }
            // Only the removal that wins reports, so a damage cancel and a completion never both happen.
            if (pending.remove(id, p)) {
                out.add(new Step<>(id, p.payload, end, 0));
            }
        }
        return out;
    }
}
