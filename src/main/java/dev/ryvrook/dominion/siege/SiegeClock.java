package dev.ryvrook.dominion.siege;

import dev.ryvrook.dominion.model.Siege;

/**
 * The capture state machine of a siege without any engine or service calls, so it can be tested.
 * The service counts the players at the banner and acts on the step this returns.
 */
final class SiegeClock {
    /** How long defenders must hold the banner alone to break the siege, the default of siege.holdOffSeconds. */
    static final long HOLD_OFF_MILLIS = 30_000L;
    /**
     * After a restart nobody can win or lose a capture by absence until players had time to rejoin.
     * The default of siege.resumeGraceSeconds.
     */
    static final long RESUME_GRACE_MILLIS = 120_000L;

    enum Step { CONTINUE, DEFENDED, FAILED, BREACH }

    private SiegeClock() {}

    /**
     * Advances the presence timers of a capturing siege by one tick and says what happens next.
     * Defenders alone at the banner for HOLD_OFF_MILLIS defend it. No attacker at the banner for
     * absentLimitMillis fails it. Once the capture time is up the walls fall the moment an attacker
     * is at the banner. While now is before resumeGraceUntil both timers stay reset. Only a siege resumed from disk
     * passes a grace, every other siege passes zero.
     */
    static Step capture(Siege siege, int attackers, int defenders, long now, long absentLimitMillis, long resumeGraceUntil) {
        return capture(siege, attackers, defenders, now, absentLimitMillis, resumeGraceUntil, HOLD_OFF_MILLIS);
    }

    /** The same with the defenders' hold off time in milliseconds. */
    static Step capture(Siege siege, int attackers, int defenders, long now, long absentLimitMillis, long resumeGraceUntil,
                        long holdOffMillis) {
        if (now < resumeGraceUntil) {
            siege.setDefenderPresentSince(0);
            siege.setAttackerAbsentSince(0);
            return Step.CONTINUE;
        }
        if (defenders > 0 && attackers == 0) {
            if (siege.defenderPresentSince() == 0) {
                siege.setDefenderPresentSince(now);
            } else if (now - siege.defenderPresentSince() >= Math.max(1_000L, holdOffMillis)) {
                return Step.DEFENDED;
            }
        } else {
            siege.setDefenderPresentSince(0);
        }
        if (attackers == 0) {
            if (siege.attackerAbsentSince() == 0) {
                siege.setAttackerAbsentSince(now);
            } else if (now - siege.attackerAbsentSince() >= Math.max(1_000L, absentLimitMillis)) {
                return Step.FAILED;
            }
        } else {
            siege.setAttackerAbsentSince(0);
        }
        if (now >= siege.phaseEndsAt() && attackers > 0) {
            return Step.BREACH;
        }
        return Step.CONTINUE;
    }

    /**
     * The phase end for a siege loaded after a restart. The time that was left when the file was
     * saved is kept, so server downtime never counts toward a capture or eats into a breach, and the
     * grace is added on top. Without a save time the end is kept but never moved into the past.
     */
    static long resumedEnd(long phaseEndsAt, long savedAt, long now, long grace) {
        long left = savedAt > 0 ? phaseEndsAt - savedAt : phaseEndsAt - now;
        return now + Math.max(0, grace) + Math.max(0, left);
    }

    /** The grace a capture resumed from disk gets. None when players are already online, as after a plugin reload. */
    static long resumeGrace(boolean playersOnline) {
        return resumeGrace(playersOnline, RESUME_GRACE_MILLIS);
    }

    static long resumeGrace(boolean playersOnline, long graceMillis) {
        return playersOnline ? 0L : Math.max(0L, graceMillis);
    }

    /** Phases whose timers are saved every minute while they run. */
    static boolean keepsSaveFresh(Siege.Phase phase) {
        return phase == Siege.Phase.CAPTURING || phase == Siege.Phase.BREACHED;
    }

    /** True while the attacker must wait before sieging the same defender again. */
    static boolean onCooldown(long lastStartedAt, long cooldownMillis, long now) {
        return lastStartedAt > 0 && cooldownMillis > 0 && now - lastStartedAt < cooldownMillis;
    }
}
