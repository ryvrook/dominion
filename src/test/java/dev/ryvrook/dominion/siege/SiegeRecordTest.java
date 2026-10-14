package dev.ryvrook.dominion.siege;

import dev.ryvrook.dominion.model.Siege;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The siege record as the service saves it to sieges.json and reads it back after a restart. */
class SiegeRecordTest {
    @Test
    void activeSiegeRoundTrips() {
        UUID by = UUID.randomUUID();
        Siege s = new Siege(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "world", 123456789L, 1.5, 64, -2.5, 1000L, by);
        s.setPhase(Siege.Phase.BREACHED, 99_000L);
        s.addBlocksBroken(7);
        s.addContainersOpened(2);
        s.addAttackerDeath();
        s.addDefenderDeath();
        s.addDefenderDeath();
        Siege back = Siege.fromDocument(Document.parse(s.toDocument().toJson()));
        assertEquals(s.id(), back.id());
        assertEquals(Siege.Phase.BREACHED, back.phase());
        assertEquals(99_000L, back.phaseEndsAt());
        assertEquals(123456789L, back.chunkIndex());
        assertEquals(7, back.blocksBroken());
        assertEquals(2, back.containersOpened());
        assertEquals(1, back.attackerDeaths());
        assertEquals(2, back.defenderDeaths());
        assertEquals(by, back.startedBy());
        assertTrue(back.active());
    }

    @Test
    void finishedPhasesAreNotActive() {
        Siege s = new Siege(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "world", 0L, 0, 0, 0, 0L, null);
        for (Siege.Phase p : new Siege.Phase[] {Siege.Phase.FAILED, Siege.Phase.DEFENDED, Siege.Phase.EXPIRED}) {
            s.setPhase(p, 0L);
            assertFalse(s.active());
        }
        Siege back = Siege.fromDocument(Document.parse(s.toDocument().toJson()));
        assertNull(back.startedBy());
    }

    @Test
    void presenceTimersAreNotSavedSoTheResumeGraceMatters() {
        Siege s = new Siege(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "world", 0L, 0, 0, 0, 0L, null);
        s.setAttackerAbsentSince(5000L);
        s.setDefenderPresentSince(6000L);
        Siege back = Siege.fromDocument(Document.parse(s.toDocument().toJson()));
        assertEquals(0L, back.attackerAbsentSince());
        assertEquals(0L, back.defenderPresentSince());
    }
}
