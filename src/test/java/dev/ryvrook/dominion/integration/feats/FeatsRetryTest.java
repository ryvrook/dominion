package dev.ryvrook.dominion.integration.feats;

import dev.ryvrook.dominion.integration.StatKeys;
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

    @Test
    void anOlderFeatsGetsOnlyTheOriginalTenKeys() {
        assertTrue(FeatsBridge.keeps("1.7.2", StatKeys.JOINED));
        assertTrue(FeatsBridge.keeps("1.8.0", StatKeys.TUTORIAL_DONE));
        assertTrue(FeatsBridge.keeps(null, StatKeys.JOINED), "an unknown version is taken as new enough");
        assertTrue(FeatsBridge.keeps("1.7.0", StatKeys.CLAIMS));
        assertFalse(FeatsBridge.keeps("1.7.0", StatKeys.JOINED));
        assertFalse(FeatsBridge.keeps("1.5.1", StatKeys.POWER_MAX));
        for (String key : StatKeys.ALL) {
            assertEquals(StatKeys.ORIGINAL.contains(key), FeatsBridge.keeps("1.6.0", key), key);
        }
    }
}
