package dev.ryvrook.dominion.integration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfReloadTest {
    @Test
    void reloadsOnceUnderTheLockWhenNobodyElseDid() throws Exception {
        Object self = new Object();
        AtomicReference<Object> current = new AtomicReference<>(self);
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger reloads = new AtomicInteger();
        SelfReload.Outcome o = SelfReload.run(current::get, self, () -> {
            assertTrue(lock.isHeldByCurrentThread(), "the reload runs inside the shared lock");
            reloads.incrementAndGet();
            current.set(new Object());
            return true;
        }, lock);
        assertEquals(SelfReload.Outcome.RELOADED, o);
        assertEquals(1, reloads.get());
        assertTrue(!lock.isLocked());
    }

    @Test
    void waitsForAnotherPassAndSkipsWhenItReloadedDominion() throws Exception {
        Object self = new Object();
        AtomicReference<Object> current = new AtomicReference<>(self);
        Lock lock = new ReentrantLock();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread pass = new Thread(() -> {
            lock.lock();
            try {
                locked.countDown();
                release.await(5, TimeUnit.SECONDS);
                current.set(new Object());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        pass.start();
        locked.await();
        AtomicInteger reloads = new AtomicInteger();
        AtomicReference<SelfReload.Outcome> out = new AtomicReference<>();
        Thread mine = new Thread(() -> {
            try {
                out.set(SelfReload.run(current::get, self, () -> reloads.incrementAndGet() > 0, lock));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        mine.start();
        Thread.sleep(200);
        assertEquals(0, reloads.get(), "nothing happens while the other pass holds the lock");
        release.countDown();
        mine.join(5000);
        pass.join(5000);
        assertEquals(SelfReload.Outcome.SKIPPED, out.get());
        assertEquals(0, reloads.get(), "Dominion is started once");
    }

    @Test
    void anUnloadedDominionIsNotReloaded() throws Exception {
        AtomicInteger reloads = new AtomicInteger();
        assertEquals(SelfReload.Outcome.SKIPPED,
                SelfReload.run(() -> null, new Object(), () -> reloads.incrementAndGet() > 0, new ReentrantLock()));
        assertEquals(0, reloads.get());
    }

    @Test
    void theLockIsTheOneTrackerApiAndCoinageUse() {
        assertSame(SelfReload.sharedLock(), SelfReload.sharedLock());
        assertSame(System.getProperties().get("ryvrook.dependents.lock"), SelfReload.sharedLock());
    }
}
