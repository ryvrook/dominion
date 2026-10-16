package dev.ryvrook.dominion.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A plugin manager lookup that never parks on the manager's lock. The manager answers {@code getPlugin} and
 * {@code getPlugins} under the read side of its lock and holds the write side for a whole server stop and for every
 * {@code /plugin reload}. A Dominion timer that asked then would park on the lock until the manager is done, and on a
 * stop the manager is waiting for Dominion's own shutdown. So {@link #now} only tries the read lock and gives the last
 * answer it saw for that key while the write lock is held, or the fallback when there is none yet, so no binding drops
 * during a reload. Once the server is shutting down no lookup is made at all. Safe from any thread.
 */
public final class GatedLookup {
    /** How long {@link #fresh} waits for the read lock in one try before it checks for a stop again. */
    static final long SLICE_MILLIS = 100;

    private final Supplier<ReadWriteLock> lock;
    private final BooleanSupplier stopping;
    private final Map<Object, Object> last = new ConcurrentHashMap<>();

    /**
     * {@code lock} gives the manager's lock, or null when it is not known, and then every lookup goes through as it
     * did before. {@code stopping} is true once the server is shutting down.
     */
    public GatedLookup(Supplier<ReadWriteLock> lock, BooleanSupplier stopping) {
        this.lock = lock;
        this.stopping = stopping;
    }

    /** The lookup for timers and pools. Answers at once, the last state while the manager holds its write lock. */
    public <T> T now(Object key, Supplier<T> look, T none) {
        if (isStopping()) {
            return remembered(key, none);
        }
        ReadWriteLock rw = managerLock();
        if (rw == null) {
            return remember(key, look.get(), none);
        }
        Lock read = rw.readLock();
        if (!read.tryLock()) {
            return remembered(key, none);
        }
        try {
            return remember(key, look.get(), none);
        } finally {
            read.unlock();
        }
    }

    /**
     * The lookup for Dominion's own reload threads, which need the real state after a reload rather than the last
     * one. Waits for the read lock in short tries and gives the last state once the server is shutting down or the
     * thread is interrupted, so it never outlives a stop.
     */
    public <T> T fresh(Object key, Supplier<T> look, T none) {
        while (true) {
            if (isStopping() || Thread.currentThread().isInterrupted()) {
                return remembered(key, none);
            }
            ReadWriteLock rw = managerLock();
            if (rw == null) {
                return remember(key, look.get(), none);
            }
            Lock read = rw.readLock();
            boolean got;
            try {
                got = read.tryLock(SLICE_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return remembered(key, none);
            }
            if (got) {
                try {
                    return remember(key, look.get(), none);
                } finally {
                    read.unlock();
                }
            }
        }
    }

    private boolean isStopping() {
        try {
            return stopping != null && stopping.getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private ReadWriteLock managerLock() {
        try {
            return lock == null ? null : lock.get();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private <T> T remember(Object key, T found, T none) {
        T out = found == null ? none : found;
        if (out != null) {
            last.put(key, out);
        } else {
            last.remove(key);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private <T> T remembered(Object key, T none) {
        Object v = last.get(key);
        return v == null ? none : (T) v;
    }
}
