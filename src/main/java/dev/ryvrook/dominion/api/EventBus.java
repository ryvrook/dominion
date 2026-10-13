package dev.ryvrook.dominion.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Minimal synchronous event bus. Listeners must not throw. */
public final class EventBus {
    private final List<Consumer<DominionEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<DominionEvent> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<DominionEvent> listener) {
        listeners.remove(listener);
    }

    public void publish(DominionEvent event) {
        for (Consumer<DominionEvent> l : listeners) {
            try {
                l.accept(event);
            } catch (Exception ignored) {
                // a listener failure must never break the caller
            }
        }
    }
}
