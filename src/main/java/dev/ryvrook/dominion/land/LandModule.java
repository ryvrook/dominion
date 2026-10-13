package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.ClaimService;

/** Land ownership, claiming rules, zones, raid eligibility, territory tracking and decay. */
public final class LandModule implements DominionModule {
    private ClaimServiceImpl service;

    public LandModule() {}

    @Override
    public String name() {
        return "land";
    }

    @Override
    public void init(DominionContext ctx) {
        Outage.boot();
        service = new ClaimServiceImpl(ctx);
        ctx.register(ClaimService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.everyHalfSecond(service::territoryTick);
        ctx.daily(service::decayTick);
        ctx.onPlayerQuit(service::forget);
        ctx.bus().subscribe(service::onEvent);
    }
}
