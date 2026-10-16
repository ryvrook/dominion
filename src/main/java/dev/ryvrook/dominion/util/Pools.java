package dev.ryvrook.dominion.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Stopping Dominion's own thread pools with a bounded wait. */
public final class Pools {
    /** How long a stop lets a running task finish before it interrupts it. */
    public static final long GRACE_MILLIS = 1_000;
    /** How long a stop waits for an interrupted task. */
    public static final long INTERRUPT_MILLIS = 500;

    private Pools() {}

    /**
     * Lets queued and running tasks finish for {@code graceMillis}, interrupts them and waits {@code interruptMillis}
     * more. A task still running then is stuck, for example parked on the plugin manager lock, and is left behind.
     * Returns true when the pool ended.
     */
    public static boolean stop(ExecutorService pool, long graceMillis, long interruptMillis) {
        if (pool == null) {
            return true;
        }
        pool.shutdown();
        try {
            if (pool.awaitTermination(graceMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
            pool.shutdownNow();
            return pool.awaitTermination(interruptMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return pool.isTerminated();
        }
    }
}
