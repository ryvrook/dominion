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

    public ProgressModule() {}

    @Override
    public String name() {
        return "progress";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new ProgressServiceImpl(ctx);
        ctx.register(ProgressService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        service.tick();
        ctx.everyMinute(service::tick);
        FarmGuard guard = new FarmGuard(ctx);
        ctx.everyMinute(() -> guard.sweep(Time.now()));
        ctx.onPlayerQuit(guard::forget);
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
            registry.registerSystem(new GatherSystems.Place(service, guard));
            registry.registerSystem(new GatherSystems.Drop(service, guard));
            registry.registerSystem(new GatherSystems.Break(service, ctx, guard));
            registry.registerSystem(new GatherSystems.Pickup(service, guard));
        } catch (Exception e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not register mission counters");
        }
    }
}
