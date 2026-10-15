package dev.ryvrook.dominion.integration.tracker;

import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.model.Fixtures;
import dev.ryvrook.dominion.progress.GatherFeed;
import dev.ryvrook.dominion.progress.TestFeeds;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.trackerapi.api.Header;
import dev.ryvrook.trackerapi.api.TrackerEvent;
import dev.ryvrook.trackerapi.api.event.BlockBroken;
import dev.ryvrook.trackerapi.api.event.BlockPlaced;
import dev.ryvrook.trackerapi.api.event.ItemDropped;
import dev.ryvrook.trackerapi.api.event.ItemPickedUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs one play session through Dominion's own gather path and through the TrackerAPI path and compares the mission
 * credits. The own path makes the feed calls GatherSystems makes for the engine events, the TrackerAPI path gets the
 * records TrackerAPI's block and item hooks build for the same engine events.
 */
class GatherEquivalenceTest {
    private final Fixtures fx = Fixtures.create();
    private final UUID ann = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cid = UUID.randomUUID();
    private final UUID guild = UUID.randomUUID();
    private long seq;

    @AfterEach
    void close() {
        fx.close();
    }

    /** One engine event, as the own system sees it and as TrackerAPI publishes it, null where one side sees nothing. */
    private record Step(Consumer<GatherFeed> own, TrackerEvent tracker) {}

    private Header h(UUID player, long at) {
        return new Header(at, ++seq, "w", player);
    }

    private List<Step> session() {
        List<Step> s = new ArrayList<>();
        // Ann places iron ore by hand and Bob breaks it, no credit.
        s.add(new Step(f -> f.placed("w", "Ore_Iron_Stone", 1, 2, 3, 1000),
                new BlockPlaced(h(ann, 1000), 1, 2, 3, "Ore_Iron_Stone", "Ore_Iron_Stone", false, false)));
        s.add(new Step(f -> f.broken(bob, "w", "Ore_Iron_Stone", 1, 2, 3, true, 2000),
                new BlockBroken(h(bob, 2000), 1, 2, 3, "Ore_Iron_Stone", null, false, false)));
        // Natural ore, a trunk and plain stone.
        s.add(new Step(f -> f.broken(ann, "w", "Ore_Copper_Stone", 9, 2, 3, true, 3000),
                new BlockBroken(h(ann, 3000), 9, 2, 3, "Ore_Copper_Stone", "Tool_Pickaxe_Iron", false, false)));
        s.add(new Step(f -> f.broken(ann, "w", "Wood_Oak_Trunk", 9, 3, 3, true, 3100),
                new BlockBroken(h(ann, 3100), 9, 3, 3, "Wood_Oak_Trunk", null, false, false)));
        s.add(new Step(f -> f.broken(ann, "w", "Rock_Stone", 9, 4, 3, true, 3200),
                new BlockBroken(h(ann, 3200), 9, 4, 3, "Rock_Stone", null, false, false)));
        // A placed item whose block key differs from the item id, then broken again.
        s.add(new Step(f -> f.placed("w", "Wood_Birch_Trunk", 4, 4, 4, 3300),
                new BlockPlaced(h(cid, 3300), 4, 4, 4, "Wood_Birch_Trunk_Item", "Wood_Birch_Trunk", false, false)));
        s.add(new Step(f -> f.broken(cid, "w", "Wood_Birch_Trunk", 4, 4, 4, true, 3400),
                new BlockBroken(h(cid, 3400), 4, 4, 4, "Wood_Birch_Trunk", null, true, false)));
        // Ann drops two salmon and picks five up, three count. Stone drops are not remembered.
        s.add(new Step(f -> f.dropped(ann, "Fish_Salmon_Item", 2, 4000),
                new ItemDropped(h(ann, 4000), "Fish_Salmon_Item", 2, false)));
        s.add(new Step(f -> f.dropped(ann, "Rock_Stone", 9, 4000),
                new ItemDropped(h(ann, 4000), "Rock_Stone", 9, false)));
        s.add(new Step(f -> f.pickedUp(ann, "Fish_Salmon_Item", 5, 5000),
                new ItemPickedUp(h(ann, 5000), "Fish_Salmon_Item", 5, 3)));
        // Bob, Ann's faction mate, drops wheat and Ann picks it up, nothing counts.
        s.add(new Step(f -> f.dropped(bob, "Plant_Crop_Wheat_Item", 4, 6000),
                new ItemDropped(h(bob, 6000), "Plant_Crop_Wheat_Item", 4, false)));
        s.add(new Step(f -> f.pickedUp(ann, "Plant_Crop_Wheat_Item", 4, 6500),
                new ItemPickedUp(h(ann, 6500), "Plant_Crop_Wheat_Item", 4, 4)));
        // Cid's inventory spills wheat on death. The engine drop event Dominion hooks never fires for it.
        s.add(new Step(null, new ItemDropped(h(cid, 7000), "Plant_Crop_Wheat_Item", 3, true)));
        s.add(new Step(f -> f.pickedUp(cid, "Plant_Crop_Wheat_Item", 3, 7500),
                new ItemPickedUp(h(cid, 7500), "Plant_Crop_Wheat_Item", 3, 3)));
        // Cid picks up a stack the engine reports with no quantity.
        s.add(new Step(f -> f.pickedUp(cid, "Fish_Cod_Item", 0, 8000),
                new ItemPickedUp(h(cid, 8000), "Fish_Cod_Item", 1, 1)));
        return s;
    }

    private GatherFeed feed(List<String> credits) {
        ProgressService progress = TestContexts.fake(ProgressService.class, Map.of("advance", a -> {
            credits.add(a[0] + " " + a[1] + " " + a[2]);
            return null;
        }));
        return TestFeeds.feed(fx.ctx, progress, p -> p.equals(cid) ? null : guild);
    }

    @Test
    void bothPathsCountTheSameSession() {
        List<String> own = new ArrayList<>();
        GatherFeed ownFeed = feed(own);
        for (Step step : session()) {
            if (step.own() != null) {
                step.own().accept(ownFeed);
            }
        }
        List<String> tracked = new ArrayList<>();
        GatherFeed trackerFeed = feed(tracked);
        trackerFeed.useTracker(true);
        for (Step step : session()) {
            TrackerBridge.deliver(trackerFeed, step.tracker());
        }
        assertEquals(List.of(ann + " mine 1", ann + " chop 1", ann + " fish 3", cid + " farm 3", cid + " fish 1"), own);
        assertEquals(own, tracked);
    }

    @Test
    void trackerEventsCountNothingWhileTheOwnSystemsCount() {
        List<String> credits = new ArrayList<>();
        GatherFeed f = feed(credits);
        TrackerBridge.deliver(f, new BlockBroken(h(ann, 1000), 9, 2, 3, "Ore_Copper_Stone", null, false, false));
        assertTrue(credits.isEmpty(), "a kind turned off hands counting back, the other kinds must not count twice");
        f.useTracker(true);
        TrackerBridge.deliver(f, new BlockBroken(h(ann, 1000), 9, 2, 3, "Ore_Copper_Stone", null, false, false));
        assertEquals(List.of(ann + " mine 1"), credits);
    }
}
