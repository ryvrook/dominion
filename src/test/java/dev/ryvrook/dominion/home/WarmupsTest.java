package dev.ryvrook.dominion.home;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmupsTest {
    private final Warmups<String> w = new Warmups<>();
    private final Map<UUID, Warmups.Pos> where = new HashMap<>();
    private final Set<UUID> combat = new HashSet<>();
    private final UUID p = UUID.randomUUID();

    private List<Warmups.Step<String>> tick() {
        return w.tick(where::get, combat::contains);
    }

    private Warmups.Pos at(double x) {
        return new Warmups.Pos("world", x, 64, 0);
    }

    @Test
    void completesAfterTheWarmupWithACountdown() {
        where.put(p, at(0));
        w.start(p, "home", at(0), 5);
        assertTrue(tick().isEmpty());
        List<Warmups.Step<String>> s = tick();
        assertEquals(Warmups.Kind.COUNTDOWN, s.get(0).kind());
        assertEquals(3, s.get(0).secondsLeft());
        tick();
        tick();
        s = tick();
        assertEquals(Warmups.Kind.DONE, s.get(0).kind());
        assertEquals("home", s.get(0).payload());
        assertTrue(w.isEmpty());
        assertTrue(tick().isEmpty());
    }

    @Test
    void smallMovesAreFineAndLargerOnesCancel() {
        where.put(p, at(0));
        w.start(p, "home", at(0), 5);
        where.put(p, at(0.9));
        assertTrue(tick().isEmpty());
        where.put(p, at(1.2));
        assertEquals(Warmups.Kind.MOVED, tick().get(0).kind());
        assertFalse(w.waiting(p));
    }

    @Test
    void changingWorldOrLoggingOffCancels() {
        where.put(p, at(0));
        w.start(p, "home", at(0), 5);
        where.put(p, new Warmups.Pos("nether", 0, 64, 0));
        assertEquals(Warmups.Kind.MOVED, tick().get(0).kind());
        w.start(p, "home", at(0), 5);
        where.remove(p);
        assertEquals(Warmups.Kind.OFFLINE, tick().get(0).kind());
    }

    @Test
    void enteringCombatCancels() {
        where.put(p, at(0));
        w.start(p, "warp base", at(0), 5);
        combat.add(p);
        assertEquals(Warmups.Kind.COMBAT, tick().get(0).kind());
        assertTrue(w.isEmpty());
    }

    @Test
    void aCancelledWarmupNeverCompletes() {
        where.put(p, at(0));
        w.start(p, "home", at(0), 1);
        assertTrue(w.cancel(p));
        assertFalse(w.cancel(p));
        assertTrue(tick().isEmpty());
    }

    @Test
    void aNewWarmupReplacesTheOld() {
        where.put(p, at(0));
        w.start(p, "home", at(0), 10);
        w.start(p, "warp mine", at(0), 1);
        List<Warmups.Step<String>> s = tick();
        assertEquals(1, s.size());
        assertEquals("warp mine", s.get(0).payload());
    }

    /** A damage cancel racing the tick on another thread never lets both happen. */
    @Test
    void cancelRacingTheTickHasOneWinner() throws Exception {
        for (int i = 0; i < 2000; i++) {
            UUID id = UUID.randomUUID();
            where.put(id, at(0));
            w.start(id, "home", at(0), 1);
            boolean[] cancelled = new boolean[1];
            Thread t = new Thread(() -> cancelled[0] = w.cancel(id));
            t.start();
            List<Warmups.Step<String>> s = tick();
            t.join();
            boolean done = s.stream().anyMatch(x -> x.player().equals(id) && x.kind() == Warmups.Kind.DONE);
            assertTrue(done ^ cancelled[0], "exactly one of cancel and completion must win");
        }
    }
}
