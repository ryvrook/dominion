package dev.ryvrook.dominion.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ApiTest {

    @Test
    void resultFlags() {
        assertTrue(Result.pass().success());
        assertEquals("", Result.pass().message());
        assertTrue(Result.fail("no").failed());
        assertEquals("no", Result.fail("no").message());
        assertFalse(Result.pass("yes").failed());
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthers() {
        EventBus bus = new EventBus();
        List<DominionEvent> seen = new ArrayList<>();
        bus.subscribe(e -> { throw new IllegalStateException("boom"); });
        Consumer<DominionEvent> keep = seen::add;
        bus.subscribe(keep);
        DominionEvent e = new DominionEvent.FactionCreated(UUID.randomUUID());
        bus.publish(e);
        assertEquals(List.of(e), seen);
        bus.unsubscribe(keep);
        bus.publish(e);
        assertEquals(1, seen.size());
    }

    @Test
    void holderThrowsUntilSet() {
        ApiHolder.set(null);
        assertThrows(IllegalStateException.class, ApiHolder::get);
    }
}
