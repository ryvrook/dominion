package dev.ryvrook.dominion.progress;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.util.Time;

import java.util.logging.Level;

/** Value, leaderboard, upgrades, missions, levels and strikes. */
public final class ProgressModule implements DominionModule {
    private ProgressServiceImpl service;
    private GatherFeed feed;

    public ProgressModule() {}

    @Override
    public String name() {
        return "progress";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new ProgressServiceImpl(ctx);
        ctx.register(ProgressService.class, service);
        feed = new GatherFeed(ctx, service);
        ctx.register(GatherFeed.class, feed);
    }

    @Override
    public void start(DominionContext ctx) {
        service.tick();
        ctx.everyMinute(service::tick);
        FarmGuard guard = new FarmGuard(ctx);
        feed.guard(guard);
        ctx.everyMinute(() -> guard.sweep(Time.now()));
        // Drop memory is not cleared on quit, or a player could drop crops, log out and let a friend collect them.
        ctx.bus().subscribe(event -> {
            switch (event) {
                case DominionEvent.FactionDisbanded d -> {
                    service.forget(d.factionId());
                    service.invalidate();
                }
                case DominionEvent.FactionCreated c -> service.invalidate();
                default -> { }
            }
        });
        try {
            var registry = ctx.plugin().getEntityStoreRegistry();
            // Registered even when TrackerAPI feeds the counters, so they take over the moment it stops.
            registry.registerSystem(new GatherSystems.Place(feed));
            registry.registerSystem(new GatherSystems.Drop(feed));
            registry.registerSystem(new GatherSystems.Break(feed));
            registry.registerSystem(new GatherSystems.Pickup(feed));
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not register mission counters");
        }
    }
}
