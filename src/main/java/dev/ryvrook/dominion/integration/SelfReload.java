package dev.ryvrook.dominion.integration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Dominion reloading itself after a sibling came back with new classes. TrackerAPI and Coinage reload the plugins
 * that list them after their own reload, under a lock they share through the JVM wide system properties. Dominion
 * takes the same lock for its own reload and then checks that the plugin manager still holds the instance that asked,
 * so a reload one of them already did is not done a second time.
 */
final class SelfReload {
    /** The system properties key of the lock TrackerAPI and Coinage hold for their whole reload pass. */
    static final String LOCK_KEY = "ryvrook.dependents.lock";
    /** How long to wait for another reloader's pass before going ahead on the instance check alone. */
    static final long LOCK_SECONDS = 30;
    /** How long a reload the manager finishes on a future may take. */
    static final long SETTLE_MILLIS = 15_000;

    enum Outcome { RELOADED, SKIPPED, FAILED }

    private SelfReload() {}

    /** The lock shared by every reloader of the suite in this JVM. */
    static Lock sharedLock() {
        Object o = System.getProperties().computeIfAbsent(LOCK_KEY, k -> new ReentrantLock());
        return o instanceof Lock l ? l : new ReentrantLock();
    }

    /**
     * Reloads under the lock when {@code current} still answers {@code self}. SKIPPED when the manager holds another
     * instance or none, which means someone reloaded or removed Dominion meanwhile.
     */
    static Outcome run(Supplier<Object> current, Object self, BooleanSupplier reload, Lock lock) throws InterruptedException {
        boolean held = lock.tryLock(LOCK_SECONDS, TimeUnit.SECONDS);
        try {
            if (current.get() != self) {
                return Outcome.SKIPPED;
            }
            if (reload.getAsBoolean()) {
                return Outcome.RELOADED;
            }
            long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SETTLE_MILLIS);
            while (current.get() == self) {
                if (System.nanoTime() > until) {
                    return Outcome.FAILED;
                }
                Thread.sleep(100);
            }
            return Outcome.RELOADED;
        } finally {
            if (held) {
                lock.unlock();
            }
        }
    }
}
