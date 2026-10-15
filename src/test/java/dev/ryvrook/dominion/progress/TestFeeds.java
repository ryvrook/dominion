package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.service.ProgressService;

import java.util.UUID;
import java.util.function.Function;

/** Builds a started {@link GatherFeed} for tests in other packages. */
public final class TestFeeds {
    private TestFeeds() {}

    /** A feed with a farm guard that remembers placed blocks for a minute and reads factions from the function. */
    public static GatherFeed feed(DominionContext ctx, ProgressService progress, Function<UUID, UUID> factionOf) {
        GatherFeed feed = new GatherFeed(ctx, progress);
        feed.guard(new FarmGuard(() -> 60_000L, factionOf));
        return feed;
    }
}
