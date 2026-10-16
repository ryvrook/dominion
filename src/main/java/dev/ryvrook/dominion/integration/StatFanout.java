package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.api.DominionEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The one bus listener for the counters. It maps each event once and hands the credits to every linked sink.
 * With no sink linked it returns at once, so a server without TrackerAPI and Feats pays one list read per event.
 * Runs on whatever thread published the event, often a world thread, and does nothing there beyond the adds.
 * Registered as a service, so the tutorial can send its one credit through {@link #credit} without an event.
 */
public final class StatFanout implements Consumer<DominionEvent> {
    private final StatMapper.Facts facts;
    private final List<StatSink> sinks = new CopyOnWriteArrayList<>();

    public StatFanout(StatMapper.Facts facts) {
        this.facts = facts;
    }

    public void add(StatSink sink) {
        if (sink != null && !sinks.contains(sink)) {
            sinks.add(sink);
        }
    }

    public void remove(StatSink sink) {
        sinks.remove(sink);
    }

    public int sinks() {
        return sinks.size();
    }

    @Override
    public void accept(DominionEvent event) {
        if (sinks.isEmpty()) {
            return;
        }
        List<StatMapper.Credit> credits;
        try {
            credits = StatMapper.credits(event, facts);
        } catch (RuntimeException e) {
            return;
        }
        for (StatMapper.Credit c : credits) {
            credit(c.player(), c.key(), c.amount());
        }
    }

    /** Hands one credit to every linked sink. Safe from any thread. */
    public void credit(UUID player, String key, long amount) {
        if (player == null || key == null || amount <= 0) {
            return;
        }
        for (StatSink sink : sinks) {
            try {
                sink.add(player, key, amount);
            } catch (RuntimeException | LinkageError ignored) {
                // a sibling problem must never reach the code that published the event
            }
        }
    }
}
