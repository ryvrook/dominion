package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.land.SafeZoneGuard.Spot;
import dev.ryvrook.dominion.land.SafeZoneGuard.Verdict;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SafeZoneGuardTest {
    static final String W = "world";
    final SafeZoneGuard guard = new SafeZoneGuard();
    final UUID p = UUID.randomUUID();

    static Spot at(double x, double z) {
        return new Spot(W, x, 64, z, 90f, 0f);
    }

    // ---- the pure rule ------------------------------------------------------

    @Test
    void aTaggedPlayerOneStepInsideIsPushedBack() {
        assertEquals(Verdict.PUSH_BACK, SafeZoneGuard.decide(true, true, true, at(31.5, 10), at(32.4, 10)));
    }

    @Test
    void untaggedDisabledOrOutsideIsAllowed() {
        Spot out = at(31.5, 10);
        Spot in = at(32.4, 10);
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(true, false, true, out, in));
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(false, true, true, out, in));
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(true, true, false, out, in));
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(true, true, true, null, in));
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(true, true, true, out, null));
    }

    @Test
    void anotherWorldOrALongJumpIsATeleportAndIsAllowed() {
        Spot in = at(0, 0);
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(true, true, true, new Spot("nether", 1, 64, 1, 0, 0), in));
        assertEquals(Verdict.ALLOW, SafeZoneGuard.decide(true, true, true, at(SafeZoneGuard.MAX_STEP + 1, 0), in));
        assertEquals(Verdict.PUSH_BACK, SafeZoneGuard.decide(true, true, true, at(SafeZoneGuard.MAX_STEP, 0), in));
    }

    @Test
    void heightDoesNotCountTowardsTheStep() {
        Spot high = new Spot(W, 0, 400, 0, 0, 0);
        assertEquals(Verdict.PUSH_BACK, SafeZoneGuard.decide(true, true, true, high, at(1, 1)));
        assertEquals(1.0, at(0, 0).flatDistance(new Spot(W, 0, 900, 1, 0, 0)), 1e-9);
    }

    // ---- the per player state -----------------------------------------------

    @Test
    void theLastSpotOutsideIsTheAnchorAndATaggedCrossingIsPushedBack() {
        assertEquals(Verdict.ALLOW, guard.observe(p, at(28, 10), false, true, () -> true));
        assertEquals(Verdict.ALLOW, guard.observe(p, at(31, 10), false, true, () -> true));
        assertEquals(at(31, 10), guard.anchor(p));
        assertEquals(Verdict.PUSH_BACK, guard.observe(p, at(33, 10), true, true, () -> true));
        // The anchor stays where it was so the push back goes to the spot outside.
        assertEquals(at(31, 10), guard.anchor(p));
        // Until the teleport lands the player is still inside and is judged again.
        assertEquals(Verdict.PUSH_BACK, guard.observe(p, at(33, 10), true, true, () -> true));
        assertEquals(Verdict.ALLOW, guard.observe(p, at(31, 10), false, true, () -> true));
    }

    @Test
    void aPlayerLetInIsNotPushedOutWhenATagStartsInside() {
        guard.observe(p, at(31, 10), false, true, () -> false);
        assertEquals(Verdict.ALLOW, guard.observe(p, at(33, 10), true, true, () -> false));
        assertEquals(Verdict.ALLOW, guard.observe(p, at(34, 10), true, true, () -> true));
        // Leaving and walking back in while tagged is a new crossing.
        guard.observe(p, at(31, 10), false, true, () -> true);
        assertEquals(Verdict.PUSH_BACK, guard.observe(p, at(33, 10), true, true, () -> true));
    }

    @Test
    void aPlayerWhoJoinedInsideHasNoAnchorAndIsAllowed() {
        assertEquals(Verdict.ALLOW, guard.observe(p, at(33, 10), true, true, () -> true));
        assertNull(guard.anchor(p));
    }

    @Test
    void theRuleTurnedOffLetsTaggedPlayersIn() {
        guard.observe(p, at(31, 10), false, false, () -> true);
        assertEquals(Verdict.ALLOW, guard.observe(p, at(33, 10), true, false, () -> true));
    }

    @Test
    void theTagIsOnlyAskedWhenACrossingIsJudged() {
        AtomicInteger asked = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            guard.observe(p, at(20 + i, 10), false, true, () -> asked.incrementAndGet() > 0);
        }
        assertEquals(0, asked.get());
        guard.observe(p, at(33, 10), true, true, () -> asked.incrementAndGet() < 0);
        guard.observe(p, at(34, 10), true, true, () -> asked.incrementAndGet() < 0);
        assertEquals(1, asked.get());
    }

    @Test
    void forgetDropsTheAnchor() {
        guard.observe(p, at(31, 10), false, true, () -> true);
        guard.forget(p);
        assertNull(guard.anchor(p));
        assertEquals(Verdict.ALLOW, guard.observe(p, at(33, 10), true, true, () -> true));
        assertEquals(Verdict.ALLOW, guard.observe(null, at(33, 10), true, true, () -> true));
    }
}
