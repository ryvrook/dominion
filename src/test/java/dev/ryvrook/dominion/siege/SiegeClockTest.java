package dev.ryvrook.dominion.siege;

import dev.ryvrook.dominion.model.Siege;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static dev.ryvrook.dominion.siege.SiegeClock.Step.BREACH;
import static dev.ryvrook.dominion.siege.SiegeClock.Step.CONTINUE;
import static dev.ryvrook.dominion.siege.SiegeClock.Step.DEFENDED;
import static dev.ryvrook.dominion.siege.SiegeClock.Step.FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeClockTest {
    private static final long START = 1_000_000L;
    private static final long CAPTURE = 15 * 60_000L;
    private static final long ABSENT = 60_000L;

    private static Siege siege() {
        Siege s = new Siege(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "world", 0L, 0, 64, 0, START, UUID.randomUUID());
        s.setPhase(Siege.Phase.CAPTURING, START + CAPTURE);
        return s;
    }

    private static SiegeClock.Step step(Siege s, int attackers, int defenders, long now) {
        return SiegeClock.capture(s, attackers, defenders, now, ABSENT, 0L);
    }

    @Test
    void attackersHoldingContinueUntilCaptureTime() {
        Siege s = siege();
        assertEquals(CONTINUE, step(s, 3, 0, START + 1000));
        assertEquals(CONTINUE, step(s, 3, 2, START + CAPTURE - 1));
        assertEquals(BREACH, step(s, 1, 0, START + CAPTURE));
    }

    @Test
    void contestedBannerStillBreachesWhenTimeIsUp() {
        Siege s = siege();
        assertEquals(BREACH, step(s, 1, 5, START + CAPTURE + 10));
    }

    @Test
    void wallsNeverFallWithoutAnAttackerAtTheBanner() {
        Siege s = siege();
        assertEquals(CONTINUE, step(s, 0, 0, START + CAPTURE + 1000));
        assertEquals(CONTINUE, step(s, 0, 0, START + CAPTURE + 30_000));
        // An attacker returning before the absence limit breaches at once.
        assertEquals(BREACH, step(s, 1, 0, START + CAPTURE + 50_000));
    }

    @Test
    void absenceFailsAtTheLimitAndNotBefore() {
        Siege s = siege();
        long t = START + 5000;
        assertEquals(CONTINUE, step(s, 0, 0, t));
        assertEquals(CONTINUE, step(s, 0, 0, t + ABSENT - 1));
        assertEquals(FAILED, step(s, 0, 0, t + ABSENT));
    }

    @Test
    void returningAttackerResetsTheAbsenceTimer() {
        Siege s = siege();
        long t = START + 5000;
        step(s, 0, 0, t);
        step(s, 0, 0, t + 50_000);
        assertEquals(CONTINUE, step(s, 1, 0, t + 55_000));
        assertEquals(0, s.attackerAbsentSince());
        assertEquals(CONTINUE, step(s, 0, 0, t + 56_000));
        assertEquals(CONTINUE, step(s, 0, 0, t + 56_000 + ABSENT - 1));
    }

    @Test
    void defendersAloneHoldOffAfterThirtySeconds() {
        Siege s = siege();
        long t = START + 5000;
        assertEquals(CONTINUE, step(s, 0, 2, t));
        assertEquals(CONTINUE, step(s, 0, 2, t + SiegeClock.HOLD_OFF_MILLIS - 1));
        assertEquals(DEFENDED, step(s, 0, 2, t + SiegeClock.HOLD_OFF_MILLIS));
    }

    @Test
    void defenceWinsBeforeAbsenceWhenBothAreDue() {
        Siege s = siege();
        long t = START + 5000;
        step(s, 0, 1, t);
        // With a short absence limit both timers are due at the same tick, and the defence is checked first.
        assertEquals(DEFENDED, SiegeClock.capture(s, 0, 1, t + SiegeClock.HOLD_OFF_MILLIS, 1000, 0));
    }

    @Test
    void attackerArrivingResetsTheDefenceTimer() {
        Siege s = siege();
        long t = START + 5000;
        step(s, 0, 2, t);
        step(s, 1, 2, t + 20_000);
        assertEquals(0, s.defenderPresentSince());
        assertEquals(CONTINUE, step(s, 0, 2, t + 21_000));
        assertEquals(CONTINUE, step(s, 0, 2, t + 21_000 + SiegeClock.HOLD_OFF_MILLIS - 1));
    }

    @Test
    void absenceLimitHasAOneSecondFloor() {
        Siege s = siege();
        long t = START + 5000;
        step(s, 0, 0, t);
        assertEquals(CONTINUE, SiegeClock.capture(s, 0, 0, t + 999, 0, 0));
        assertEquals(FAILED, SiegeClock.capture(s, 0, 0, t + 1000, 0, 0));
    }

    @Test
    void resumeGraceHoldsBothTimers() {
        Siege s = siege();
        long t = START + 5000;
        long graceUntil = t + SiegeClock.RESUME_GRACE_MILLIS;
        for (long now = t; now < graceUntil; now += 10_000) {
            assertEquals(CONTINUE, SiegeClock.capture(s, 0, 3, now, ABSENT, graceUntil));
        }
        assertEquals(0, s.attackerAbsentSince());
        assertEquals(0, s.defenderPresentSince());
        // After the grace the timers start from scratch.
        assertEquals(CONTINUE, SiegeClock.capture(s, 0, 3, graceUntil, ABSENT, graceUntil));
        assertEquals(DEFENDED, SiegeClock.capture(s, 0, 3, graceUntil + SiegeClock.HOLD_OFF_MILLIS, ABSENT, graceUntil));
    }

    @Test
    void resumeGraceAlsoHoldsTheBreach() {
        Siege s = siege();
        long late = START + CAPTURE + 5000;
        assertEquals(CONTINUE, SiegeClock.capture(s, 4, 0, late, ABSENT, late + 1));
        assertEquals(BREACH, SiegeClock.capture(s, 4, 0, late + 1, ABSENT, late + 1));
    }

    @Test
    void restartKeepsTheTimeThatWasLeft() {
        long savedAt = 10_000L;
        long endsAt = savedAt + 5 * 60_000L;
        long now = savedAt + 3_600_000L;
        assertEquals(now + 5 * 60_000L, SiegeClock.resumedEnd(endsAt, savedAt, now, 0));
        assertEquals(now + 7 * 60_000L, SiegeClock.resumedEnd(endsAt, savedAt, now, 2 * 60_000L));
    }

    @Test
    void restartWithExpiredTimeEndsAtGrace() {
        assertEquals(500 + 100, SiegeClock.resumedEnd(10, 50, 500, 100));
        assertEquals(500, SiegeClock.resumedEnd(10, 50, 500, -7));
    }

    @Test
    void restartWithoutASaveTimeNeverMovesIntoThePast() {
        assertEquals(900, SiegeClock.resumedEnd(900, 0, 500, 0));
        assertEquals(500, SiegeClock.resumedEnd(100, 0, 500, 0));
    }

    @Test
    void cooldown() {
        long minute = 60_000L;
        assertFalse(SiegeClock.onCooldown(0, 60 * minute, 1000));
        assertTrue(SiegeClock.onCooldown(1000, 60 * minute, 1000 + 60 * minute - 1));
        assertFalse(SiegeClock.onCooldown(1000, 60 * minute, 1000 + 60 * minute));
        assertFalse(SiegeClock.onCooldown(1000, 0, 1001));
    }

    @Test
    void graceOnlyAfterABootWithNobodyOnline() {
        assertEquals(SiegeClock.RESUME_GRACE_MILLIS, SiegeClock.resumeGrace(false));
        assertEquals(0L, SiegeClock.resumeGrace(true));
    }

    @Test
    void aSiegeWithoutGraceCanBeDefendedRightAway() {
        // Sieges started after boot pass zero, so another siege's resume grace never holds them.
        Siege s = siege();
        long t = START + 5000;
        assertEquals(CONTINUE, SiegeClock.capture(s, 0, 2, t, ABSENT, 0L));
        assertEquals(DEFENDED, SiegeClock.capture(s, 0, 2, t + SiegeClock.HOLD_OFF_MILLIS, ABSENT, 0L));
    }

    @Test
    void bothOpenPhasesKeepTheSaveTimeFresh() {
        assertTrue(SiegeClock.keepsSaveFresh(Siege.Phase.CAPTURING));
        assertTrue(SiegeClock.keepsSaveFresh(Siege.Phase.BREACHED));
        assertFalse(SiegeClock.keepsSaveFresh(Siege.Phase.FAILED));
        assertFalse(SiegeClock.keepsSaveFresh(Siege.Phase.DEFENDED));
        assertFalse(SiegeClock.keepsSaveFresh(Siege.Phase.EXPIRED));
    }

    @Test
    void crashLateInABreachKeepsOnlyTheTimeThatWasLeft() {
        long minute = 60_000L;
        long breachStart = 10 * minute;
        long endsAt = breachStart + 30 * minute;
        // Saved each minute, the last save before the crash was at minute 29.
        long savedAt = breachStart + 29 * minute;
        long boot = savedAt + 10 * minute;
        assertEquals(boot + minute, SiegeClock.resumedEnd(endsAt, savedAt, boot, 0));
        // With the save time stuck at the breach start, the whole breach would run again.
        assertEquals(boot + 30 * minute, SiegeClock.resumedEnd(endsAt, breachStart, boot, 0));
    }

    @Test
    void holdOffAndResumeGraceAreSettings() {
        Siege s = siege();
        long t = START + 1000;
        assertEquals(CONTINUE, SiegeClock.capture(s, 0, 2, t, ABSENT, 0L, 5_000L));
        assertEquals(CONTINUE, SiegeClock.capture(s, 0, 2, t + 4_999, ABSENT, 0L, 5_000L));
        assertEquals(DEFENDED, SiegeClock.capture(s, 0, 2, t + 5_000, ABSENT, 0L, 5_000L));
        assertEquals(10_000L, SiegeClock.resumeGrace(false, 10_000L));
        assertEquals(0L, SiegeClock.resumeGrace(true, 10_000L));
    }
}
