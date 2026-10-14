package dev.ryvrook.dominion.combat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CombatLogBridgeTest {
    private static final List<ClassLoader> LOADERS = List.of(CombatLogBridgeTest.class.getClassLoader());
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        FakeCombatLogApis.Good.TAGS.clear();
        FakeCombatLogApis.Good.available = true;
    }

    private static CombatLogBridge bridge(Class<?> api) {
        return new CombatLogBridge(api.getName(), LOADERS, "1.7.0");
    }

    @Test
    void theRealApiClassIsNotOnTheTestClasspathSoTheBridgeIsAbsent() {
        CombatLogBridge b = new CombatLogBridge(CombatLogBridge.API_CLASS, LOADERS, null);
        assertFalse(b.present());
        assertFalse(b.available());
        assertFalse(b.tagged(player));
        assertEquals(0L, b.remainingMillis(player));
        assertFalse(b.pluginLoaded());
    }

    @Test
    void aMissingClassOrNoLoadersAnswerFalseAndZero() {
        for (CombatLogBridge b : List.of(new CombatLogBridge("org.example.Nothing", LOADERS, null),
                new CombatLogBridge(null, null, null), new CombatLogBridge("x", List.of(), null))) {
            assertFalse(b.present());
            assertFalse(b.tagged(player));
            assertFalse(b.tagged(null));
            assertEquals(0L, b.remainingMillis(player));
        }
    }

    @Test
    void anApiOfTheKnownShapeIsReadThroughTheHandles() {
        CombatLogBridge b = bridge(FakeCombatLogApis.Good.class);
        assertTrue(b.present());
        assertTrue(b.available());
        assertEquals("1.7.0", b.version());
        assertFalse(b.tagged(player));
        FakeCombatLogApis.Good.TAGS.put(player, 9000L);
        assertTrue(b.tagged(player));
        assertEquals(9000L, b.remainingMillis(player));
        assertFalse(b.tagged(null));
    }

    @Test
    void aNegativeRemainingTimeIsReadAsZero() {
        FakeCombatLogApis.Good.TAGS.put(player, -50L);
        assertEquals(0L, bridge(FakeCombatLogApis.Good.class).remainingMillis(player));
    }

    @Test
    void anApiOfAnotherShapeLeavesTheBridgeAbsentWithoutThrowing() {
        for (Class<?> api : List.of(FakeCombatLogApis.InstanceMethods.class, FakeCombatLogApis.WrongReturnType.class)) {
            CombatLogBridge b = assertDoesNotThrow(() -> bridge(api));
            assertFalse(b.present(), api.getSimpleName());
            assertFalse(b.tagged(player));
            assertEquals(0L, b.remainingMillis(player));
            assertNotNull(b.lastFailure(), api.getSimpleName());
            // The plugin is installed, so the status can say its API could not be read.
            assertTrue(b.pluginLoaded());
        }
    }

    @Test
    void callsThatThrowAnswerFalseAndZeroAndAreCounted() {
        CombatLogBridge b = bridge(FakeCombatLogApis.Throwing.class);
        assertTrue(b.present());
        assertFalse(assertDoesNotThrow(b::available));
        assertFalse(assertDoesNotThrow(() -> b.tagged(player)));
        assertEquals(0L, assertDoesNotThrow(() -> b.remainingMillis(player)));
        assertEquals(3, b.failures());
        assertInstanceOf(NoClassDefFoundError.class, b.lastFailure());
    }
}
