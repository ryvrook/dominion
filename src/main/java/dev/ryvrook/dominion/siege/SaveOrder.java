package dev.ryvrook.dominion.siege;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Keeps file writes in the order their snapshots were taken. A snapshot takes a generation number
 * while the service lock is held. Writes then run one at a time, and a snapshot older than one
 * already written is dropped, so a slow older write can never land over a newer file.
 */
final class SaveOrder {
    /** What happened to one write. */
    enum Outcome { WRITTEN, STALE, FAILED }

    private final AtomicLong issued = new AtomicLong();
    private final Object writeLock = new Object();
    private long written;

    /** The generation for a snapshot. Call it while the lock that guards the snapshot is held. */
    long next() {
        return issued.incrementAndGet();
    }

    /** Runs the write unless a newer generation already landed. The write returns false on failure. */
    Outcome write(long generation, BooleanSupplier write) {
        synchronized (writeLock) {
            if (generation <= written) {
                return Outcome.STALE;
            }
            if (!write.getAsBoolean()) {
                return Outcome.FAILED;
            }
            written = generation;
            return Outcome.WRITTEN;
        }
    }
}
