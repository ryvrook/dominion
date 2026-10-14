package dev.ryvrook.dominion.combat;

import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.util.Time;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CombatTagsTest {
    /** A tag source over a plain map of remaining milliseconds. */
    static final class FakeSource implements CombatTagSource {
        final Map<UUID, Long> left = new HashMap<>();
        boolean installed = true;
        String version = "1.7.0";
        int asked;

        @Override
        public boolean tagged(UUID player) {
            asked++;
            return left.getOrDefault(player, 0L) > 0;
        }

        @Override
        public long remainingMillis(UUID player) {
            asked++;
            return left.getOrDefault(player, 0L);
        }

        @Override
        public boolean present() {
            return installed;
        }

        @Override
        public String version() {
            return version;
        }
    }

    /** A source that fails on every call. */
    static final class BrokenSource implements CombatTagSource {
        @Override
        public boolean tagged(UUID player) {
            throw new IllegalStateException("broken");
        }

        @Override
        public long remainingMillis(UUID player) {
            throw new IllegalStateException("broken");
        }
    }

    Fixtures fx;
    FakeSource own;
    FakeSource external;
    CombatTags tags;
    final UUID a = UUID.randomUUID();
    final UUID b = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fx = Fixtures.create();
        own = new FakeSource();
        external = new FakeSource();
        tags = new CombatTags(fx.settings(), own, external);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void theDefaultIsAutoAndOnlyAutoAndOffAreAccepted() {
        assertEquals("auto", fx.settings().text(CombatTags.KEY));
        assertEquals(List.of("auto", "off"), fx.settings().choicesOf(CombatTags.KEY));
        assertNull(fx.settings().trySet(CombatTags.KEY, "off"));
        assertNull(fx.settings().trySet(CombatTags.KEY, "auto"));
        assertNotNull(fx.settings().trySet(CombatTags.KEY, "on"));
        assertTrue(fx.settings().bool("combat.blockSafeZoneEntry"));
        assertTrue(fx.settings().bool("combat.blockClaimsWhileTagged"));
    }

    @Test
    void aPlayerIsTaggedWhenEitherSourceTagsThem() {
        assertFalse(tags.tagged(a));
        own.left.put(a, 3000L);
        assertTrue(tags.tagged(a));
        external.left.put(b, 8000L);
        assertTrue(tags.tagged(b));
        own.left.clear();
        external.left.clear();
        assertFalse(tags.tagged(a));
        assertFalse(tags.tagged(b));
        assertFalse(tags.tagged(null));
    }

    @Test
    void theRemainingTimeIsTheLongerOfTheTwo() {
        own.left.put(a, 3000L);
        external.left.put(a, 8000L);
        assertEquals(8000L, tags.remainingMillis(a));
        own.left.put(a, 12_000L);
        assertEquals(12_000L, tags.remainingMillis(a));
        assertEquals(0L, tags.remainingMillis(b));
        assertEquals(0L, tags.remainingMillis(null));
    }

    @Test
    void offIgnoresCombatLogAndKeepsTheOwnTag() {
        external.left.put(a, 8000L);
        own.left.put(b, 2000L);
        fx.set(CombatTags.KEY, "off");
        assertFalse(tags.tagged(a));
        assertEquals(0L, tags.remainingMillis(a));
        assertTrue(tags.tagged(b));
        assertEquals(2000L, tags.remainingMillis(b));
        assertFalse(tags.usingExternal());
        // The setting is read live, so switching back applies at once.
        fx.set(CombatTags.KEY, "auto");
        assertTrue(tags.tagged(a));
    }

    @Test
    void anAbsentCombatLogIsNeverAsked() {
        external.installed = false;
        external.left.put(a, 8000L);
        assertFalse(tags.tagged(a));
        assertEquals(0L, tags.remainingMillis(a));
        assertEquals(0, external.asked);
    }

    @Test
    void aBrokenSourceCountsAsNotTaggedWithoutThrowing() {
        CombatTags broken = new CombatTags(fx.settings(), new BrokenSource(), new BrokenSource());
        assertFalse(assertDoesNotThrow(() -> broken.tagged(a)));
        assertEquals(0L, assertDoesNotThrow(() -> broken.remainingMillis(a)));
        CombatTags halfBroken = new CombatTags(fx.settings(), own, new BrokenSource());
        own.left.put(a, 1000L);
        assertTrue(halfBroken.tagged(a));
    }

    @Test
    void theBridgeWhoseReflectionFailedAnswersFalseInsideTheUnion() {
        CombatLogBridge failed = new CombatLogBridge(FakeCombatLogApis.WrongReturnType.class.getName(),
                List.of(getClass().getClassLoader()), "1.7.0");
        CombatTags withFailed = new CombatTags(fx.settings(), own, failed);
        assertFalse(assertDoesNotThrow(() -> withFailed.tagged(a)));
        assertEquals("Dominion found CombatLog 1.7.0 but could not read its API, using its own combat tag", withFailed.status());
    }

    @Test
    void theStatusLineNamesTheSourceInUse() {
        assertEquals("Dominion uses CombatLog 1.7.0 for combat tags", tags.status());
        fx.set(CombatTags.KEY, "off");
        assertEquals("Dominion found CombatLog 1.7.0 but combat.useCombatLog is off, using its own combat tag", tags.status());
        external.installed = false;
        assertEquals("Dominion found no CombatLog, using its own combat tag", tags.status());
        assertEquals("Dominion found no CombatLog, using its own combat tag",
                new CombatTags(fx.settings(), own, new CombatLogBridge(null, null, null)).status());
        external.installed = true;
        external.version = null;
        fx.set(CombatTags.KEY, "auto");
        assertEquals("Dominion uses CombatLog of an unknown version for combat tags", tags.status());
    }

    @Test
    void theOwnTagReadsThePlayerRecord() {
        OwnCombatTag mine = new OwnCombatTag(fx.ctx);
        UUID p = fx.player("Ann");
        assertFalse(mine.tagged(p));
        fx.ctx.player(p).setCombatTagUntil(Time.now() + 10_000L);
        assertTrue(mine.tagged(p));
        long left = mine.remainingMillis(p);
        assertTrue(left > 9000L && left <= 10_000L, String.valueOf(left));
        fx.ctx.player(p).setCombatTagUntil(Time.now() - 1);
        assertFalse(mine.tagged(p));
        assertEquals(0L, mine.remainingMillis(p));
        // A player Dominion has not loaded is not tagged and no record is made for them.
        UUID stranger = UUID.randomUUID();
        assertFalse(mine.tagged(stranger));
        assertNull(fx.repo().playerIfLoaded(stranger));
    }
}
