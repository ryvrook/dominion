package dev.ryvrook.dominion.events;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HillAndOutpostTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static Hill hill() {
        Hill h = new Hill("Peak", "world", 0, 64, 0, 8);
        h.running = true;
        return h;
    }

    @Test
    void hillCapturesAfterCapSecondsOfSoleControl() {
        Hill h = hill();
        assertEquals(Hill.Step.TAKING, h.step(List.of(A), 3));
        assertEquals(Hill.Step.HOLDING, h.step(List.of(A), 3));
        assertEquals(Hill.Step.CAPTURED, h.step(List.of(A), 3));
        assertEquals(3, h.held);
    }

    @Test
    void contestFreezesTheCount() {
        Hill h = hill();
        h.step(List.of(A), 10);
        h.step(List.of(A), 10);
        assertEquals(Hill.Step.CONTESTED, h.step(List.of(A, B), 10));
        assertTrue(h.contested);
        assertEquals(2, h.held);
        assertEquals(A, h.capturer);
        assertEquals(Hill.Step.HOLDING, h.step(List.of(A), 10));
        assertFalse(h.contested);
        assertEquals(3, h.held);
    }

    @Test
    void leavingTheHillResetsProgress() {
        Hill h = hill();
        h.step(List.of(A), 10);
        h.step(List.of(A), 10);
        assertEquals(Hill.Step.EMPTY, h.step(List.of(), 10));
        assertNull(h.capturer);
        assertEquals(0, h.held);
        assertEquals(Hill.Step.TAKING, h.step(List.of(A), 10));
        assertEquals(1, h.held);
    }

    @Test
    void newFactionStartsOver() {
        Hill h = hill();
        for (int i = 0; i < 5; i++) {
            h.step(List.of(A), 10);
        }
        assertEquals(Hill.Step.TAKING, h.step(List.of(B), 10));
        assertEquals(B, h.capturer);
        assertEquals(1, h.held);
    }

    @Test
    void hillInsideIsACylinder() {
        Hill h = hill();
        assertTrue(h.inside("world", 8, 64, 0));
        assertFalse(h.inside("world", 8.1, 64, 0));
        assertTrue(h.inside("world", 0, 72, 0));
        assertFalse(h.inside("world", 0, 72.5, 0));
        assertFalse(h.inside("nether", 0, 64, 0));
    }

    @Test
    void missedOpenings() {
        assertFalse(Hill.missed(0, 10_000_000));
        assertFalse(Hill.missed(1000, 1000 + Hill.MISSED_MILLIS));
        assertTrue(Hill.missed(1000, 1000 + Hill.MISSED_MILLIS + 1));
        assertFalse(Hill.missed(5000, 1000));
    }

    @Test
    void hillRoundTripsThroughTheFile() {
        Hill h = hill();
        h.capturer = A;
        h.held = 42;
        h.schedule = Schedule.parse("at 18:00");
        h.nextRun = 123L;
        Hill back = Hill.fromDocument(Document.parse(h.toDocument().toJson()));
        assertEquals(A, back.capturer);
        assertEquals(42, back.held);
        assertTrue(back.running);
        assertEquals("at 18:00", back.schedule.text());
        assertEquals(123L, back.nextRun);
    }

    private static Outpost outpost() {
        return new Outpost("Mine", "world", 0, 64, 0, 10, "xp", 1.5);
    }

    private static Map<UUID, Integer> counts(Object... pairs) {
        Map<UUID, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((UUID) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void outpostCaptureNeedsFullControl() {
        Outpost o = outpost();
        for (int i = 0; i < 49; i++) {
            assertNull(o.step(counts(A, 2), 1.0, 0.5));
        }
        assertEquals(98.0, o.controlOf(A), 1e-9);
        assertEquals(A, o.step(counts(A, 2), 1.0, 0.5));
        assertEquals(100.0, o.controlOf(A), 1e-9);
    }

    @Test
    void outpostControlCapsAtOneHundred() {
        Outpost o = outpost();
        o.control.put(A, 99.5);
        o.holder = A;
        assertNull(o.step(counts(A, 5), 1.0, 0.5));
        assertEquals(100.0, o.controlOf(A), 1e-9);
    }

    @Test
    void contestedOutpostFreezes() {
        Outpost o = outpost();
        o.control.put(A, 40.0);
        o.control.put(B, 30.0);
        assertNull(o.step(counts(A, 1, B, 3), 1.0, 0.5));
        assertTrue(o.contested);
        assertEquals(40.0, o.controlOf(A), 1e-9);
        assertEquals(30.0, o.controlOf(B), 1e-9);
    }

    @Test
    void emptyOutpostDecaysAndForgetsNonHolders() {
        Outpost o = outpost();
        o.holder = A;
        o.control.put(A, 0.4);
        o.control.put(B, 0.3);
        assertNull(o.step(counts(), 1.0, 0.5));
        assertFalse(o.contested);
        assertEquals(0.0, o.controlOf(A), 1e-9);
        assertTrue(o.control.containsKey(A), "the holder keeps its entry at zero");
        assertFalse(o.control.containsKey(B));
        assertEquals(A, o.holder);
    }

    @Test
    void challengerTakesFromAHolderBelowFull() {
        Outpost o = outpost();
        o.holder = A;
        o.control.put(A, 60.0);
        o.control.put(B, 99.0);
        assertEquals(B, o.step(counts(B, 1), 1.0, 0.5));
        assertEquals(59.5, o.controlOf(A), 1e-9);
    }

    @Test
    void absentHolderDecaysInTheSameTickAsTheChallengerFills() {
        Outpost o = outpost();
        o.holder = A;
        o.control.put(A, 100.0);
        o.control.put(B, 99.5);
        // B fills to 100 while the absent holder decays to 99.5 in the same tick, so B takes it.
        UUID taker = o.step(counts(B, 1), 1.0, 0.5);
        assertEquals(B, taker);
        Outpost p = outpost();
        p.holder = A;
        p.control.put(A, 100.0);
        p.control.put(B, 99.5);
        assertNull(p.step(counts(B, 1), 1.0, 0.0), "without decay the full holder keeps it");
    }

    @Test
    void zeroGainNeverCaptures() {
        Outpost o = outpost();
        for (int i = 0; i < 500; i++) {
            assertNull(o.step(counts(A, 10), 0.0, 0.5));
        }
        assertEquals(0.0, o.controlOf(A), 1e-9);
    }

    @Test
    void outpostRoundTripsThroughTheFile() {
        Outpost o = outpost();
        o.holder = A;
        o.heldSince = 77L;
        o.control.put(A, 88.5);
        o.control.put(B, 12.0);
        Outpost back = Outpost.fromDocument(Document.parse(o.toDocument().toJson()));
        assertEquals(A, back.holder);
        assertEquals(77L, back.heldSince);
        assertEquals(88.5, back.controlOf(A), 1e-9);
        assertEquals(12.0, back.controlOf(B), 1e-9);
        assertEquals("xp", back.boost);
        assertEquals(1.5, back.multiplier, 1e-9);
    }
}
