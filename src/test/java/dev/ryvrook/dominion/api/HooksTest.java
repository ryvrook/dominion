package dev.ryvrook.dominion.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class HooksTest {
    private final List<String> seen = new ArrayList<>();
    private final Consumer<DominionApi> enable = api -> seen.add("enable");
    private final Runnable disable = () -> seen.add("disable");

    @AfterEach
    void clear() {
        DominionApi.removeHooks(enable, disable);
        ApiHolder.disable();
        ApiHolder.set(null);
    }

    static DominionApi stub() {
        return (DominionApi) Proxy.newProxyInstance(DominionApi.class.getClassLoader(), new Class<?>[] {DominionApi.class},
                (p, m, a) -> m.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(p, m, a) : null);
    }

    @Test
    void enableRunsOnceEveryModuleStartedAndDisableOnStop() {
        DominionApi.onEnable(enable);
        DominionApi.onDisable(disable);
        DominionApi api = stub();
        ApiHolder.set(api);
        assertTrue(DominionApi.isPresent());
        assertSame(api, DominionApi.find());
        assertEquals(List.of(), seen, "setting the instance during setup runs no hook");
        ApiHolder.enable();
        ApiHolder.enable();
        assertEquals(List.of("enable"), seen);
        ApiHolder.disable();
        ApiHolder.disable();
        assertEquals(List.of("enable", "disable"), seen);
    }

    @Test
    void aHookAddedWhileUpRunsAtOnce() {
        ApiHolder.set(stub());
        ApiHolder.enable();
        DominionApi.onEnable(enable);
        assertEquals(List.of("enable"), seen);
    }

    @Test
    void aFailingHookDoesNotStopTheOthers() {
        Consumer<DominionApi> bad = api -> { throw new IllegalStateException("boom"); };
        DominionApi.onEnable(bad);
        DominionApi.onEnable(enable);
        try {
            ApiHolder.set(stub());
            ApiHolder.enable();
            assertEquals(List.of("enable"), seen);
        } finally {
            DominionApi.removeHooks(bad, null);
        }
    }

    @Test
    void noHookRunsWithoutAnInstance() {
        DominionApi.onEnable(enable);
        ApiHolder.enable();
        assertFalse(ApiHolder.enabled());
        assertNull(DominionApi.find());
        assertEquals(List.of(), seen);
    }

    @Test
    void newerMethodsAnswerSafelyOnAnOlderStandIn() {
        DominionApi api = stub();
        assertTrue(api.factions().isEmpty());
        assertTrue(api.sieges().isEmpty());
        assertTrue(api.sendFactionChat(null, "x", "y").failed());
        assertTrue(api.disband(null, "test").failed());
    }
}
