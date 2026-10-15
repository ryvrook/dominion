package dev.ryvrook.dominion.integration.feats;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The add retry after a Feats reload, over plain strings standing in for the API objects. */
class FeatsRetryTest {
    @Test
    void aStaleApiIsSwappedForTheRunningOneAndTriedOnce() {
        List<String> tried = new ArrayList<>();
        AtomicReference<String> linked = new AtomicReference<>("old");
        assertTrue(FeatsBridge.once("old", () -> "new", a -> tried.add(a) && a.equals("new"), linked::set));
        assertEquals(List.of("old", "new"), tried);
        assertEquals("new", linked.get());
    }

    @Test
    void aRefusalFromTheRunningApiIsNotRetried() {
        List<String> tried = new ArrayList<>();
        AtomicReference<String> linked = new AtomicReference<>("same");
        String same = "same";
        assertFalse(FeatsBridge.once(same, () -> same, a -> !tried.add(a), a -> linked.set("relinked")));
        assertEquals(List.of("same"), tried);
        assertEquals("same", linked.get());
        assertFalse(FeatsBridge.once(same, () -> null, a -> false, a -> linked.set("relinked")), "no Feats running");
        assertEquals("same", linked.get());
    }
}
