package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.service.ProgressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The feed both the own systems and the TrackerAPI bridge call, so both count the same way. */
class GatherFeedTest {
    private final Fixtures fx = Fixtures.create();
    private final List<String> credits = new ArrayList<>();
    private final UUID ann = UUID.randomUUID();
    private final GatherFeed feed = new GatherFeed(fx.ctx, TestContexts.fake(ProgressService.class, Map.of("advance", a -> {
        credits.add(a[1] + " " + a[2]);
        return null;
    })));

    @AfterEach
    void close() {
        fx.close();
    }

    @Test
    void doesNothingBeforeTheModuleStarted() {
        feed.broken(ann, "w", "Ore_Iron_Stone", 1, 2, 3, true, 0);
        assertTrue(credits.isEmpty());
    }

    @Test
    void oreAndTrunksCountUnlessAPlayerPlacedThem() {
        feed.guard(new FarmGuard(() -> 60_000L, p -> null));
        feed.broken(ann, "w", "Ore_Iron_Stone", 1, 2, 3, true, 1000);
        feed.broken(ann, "w", "Rock_Stone", 1, 2, 3, true, 1000);
        feed.placed("w", "Wood_Oak_Trunk", 4, 5, 6, 1000);
        feed.broken(UUID.randomUUID(), "w", "Wood_Oak_Trunk", 4, 5, 6, true, 2000);
        feed.broken(ann, "w", "Wood_Oak_Trunk", 7, 5, 6, false, 2000);
        assertEquals(List.of("mine 1", "chop 1"), credits, "a trunk any player placed earns nothing");
    }

    @Test
    void pickupsCountLessWhatThePlayerDropped() {
        feed.guard(new FarmGuard(() -> 60_000L, p -> null));
        feed.dropped(ann, "Fish_Salmon_Item", 2, 1000);
        feed.dropped(ann, "Rock_Stone", 9, 1000);
        feed.pickedUp(ann, "Fish_Salmon_Item", 5, 2000);
        feed.pickedUp(ann, "Plant_Crop_Wheat_Item", 0, 2000);
        feed.pickedUp(ann, "Rock_Stone", 9, 2000);
        assertEquals(List.of("fish 3", "farm 1"), credits);
    }

    @Test
    void theTrackerSwitchIsAFlag() {
        assertFalse(feed.external());
        feed.useTracker(true);
        assertTrue(feed.external());
        feed.useTracker(false);
        assertFalse(feed.external());
    }
}
