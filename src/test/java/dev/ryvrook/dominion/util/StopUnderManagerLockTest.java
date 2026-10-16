package dev.ryvrook.dominion.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin manager holds the write side of its lock for a whole server stop and calls Dominion's shutdown under it.
 * A timer or IO task that looked a plugin up then parked on the read side until the stop was over.
 */
class StopUnderManagerLockTest {
    private static final Object FEATS = new Object();

    /** A lookup that takes the read lock like PluginManager.getPlugin does. */
    private static Supplier<Object> engineLike(ReentrantReadWriteLock rw, AtomicInteger calls) {
        return () -> {
            rw.readLock().lock();
            try {
                calls.incrementAndGet();
                return FEATS;
            } finally {
                rw.readLock().unlock();
            }
        };
    }

    /** Holds the write lock on another thread, as the engine's stop does, until the returned latch counts down. */
    private static CountDownLatch holdWrite(ReentrantReadWriteLock rw) throws InterruptedException {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            rw.writeLock().lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                rw.writeLock().unlock();
            }
        }, "manager-stop");
        t.setDaemon(true);
        t.start();
        assertTrue(held.await(5, TimeUnit.SECONDS));
        return release;
    }

    @Test
    void aLookupUnderTheWriteLockAnswersTheLastStateAtOnce() throws Exception {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        AtomicInteger calls = new AtomicInteger();
        GatedLookup lookup = new GatedLookup(() -> rw, () -> false);
        assertSame(FEATS, lookup.now("Feats", engineLike(rw, calls), null));
        CountDownLatch release = holdWrite(rw);
        try {
            long t0 = System.nanoTime();
            assertSame(FEATS, lookup.now("Feats", engineLike(rw, calls), null), "the last answer, so no binding drops");
            assertNull(lookup.now("Atlas", engineLike(rw, calls), null), "nothing known yet is the fallback");
            assertTrue(System.nanoTime() - t0 < TimeUnit.MILLISECONDS.toNanos(200), "never waits for the manager");
            assertEquals(1, calls.get(), "the engine was not asked while the write lock was held");
        } finally {
            release.countDown();
        }
    }

    @Test
    void theOwnerOfTheWriteLockStillLooksUp() {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        AtomicInteger calls = new AtomicInteger();
        GatedLookup lookup = new GatedLookup(() -> rw, () -> false);
        rw.writeLock().lock();
        try {
            assertSame(FEATS, lookup.now("Feats", engineLike(rw, calls), null));
            assertSame(FEATS, lookup.fresh("Feats", engineLike(rw, calls), null));
            assertEquals(2, calls.get());
        } finally {
            rw.writeLock().unlock();
        }
    }

    @Test
    void noLookupOnceTheServerIsShuttingDownAndNoGateWithoutTheLock() {
        AtomicBoolean stopping = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        GatedLookup lookup = new GatedLookup(() -> null, stopping::get);
        assertSame(FEATS, lookup.now("Feats", engineLike(rw, calls), null), "a missing lock field looks up as before");
        stopping.set(true);
        assertSame(FEATS, lookup.now("Feats", engineLike(rw, calls), null));
        assertSame(FEATS, lookup.fresh("Feats", engineLike(rw, calls), null));
        assertEquals(1, calls.get(), "the engine is not asked during a stop");
    }

    @Test
    void aFreshLookupGivesUpWhenTheStopBegins() throws Exception {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        AtomicBoolean stopping = new AtomicBoolean();
        GatedLookup lookup = new GatedLookup(() -> rw, stopping::get);
        CountDownLatch release = holdWrite(rw);
        try {
            AtomicReference<Object> got = new AtomicReference<>(FEATS);
            Thread t = new Thread(() -> got.set(lookup.fresh("Feats", engineLike(rw, new AtomicInteger()), null)));
            t.start();
            Thread.sleep(250);
            assertTrue(t.isAlive(), "a reload thread waits for the real state");
            stopping.set(true);
            t.join(1_000);
            assertFalse(t.isAlive());
            assertNull(got.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void stopIsBoundedEvenWithAStuckTask() throws Exception {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        ExecutorService io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Dominion-IO");
            t.setDaemon(true);
            return t;
        });
        CountDownLatch release = holdWrite(rw);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Thread> stuck = new AtomicReference<>();
        // An ungated engine lookup, the old behaviour, parks uninterruptibly on the read lock.
        io.execute(() -> {
            stuck.set(Thread.currentThread());
            entered.countDown();
            engineLike(rw, new AtomicInteger()).get();
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            long t0 = System.nanoTime();
            assertFalse(Pools.stop(io, Pools.GRACE_MILLIS, Pools.INTERRUPT_MILLIS), "the stuck task is left behind");
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            assertTrue(ms < 2_000, "stop took " + ms + " ms");
        } finally {
            release.countDown();
        }
        stuck.get().join(5_000);
        assertEquals(Thread.State.TERMINATED, stuck.get().getState());
    }
}
