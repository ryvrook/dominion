package dev.ryvrook.dominion.siege;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.SiegeService;

/** Siege banners, capture, breach windows and the raid log. */
public final class SiegeModule implements DominionModule {
    private SiegeServiceImpl service;

    public SiegeModule() {}

    @Override
    public String name() {
        return "siege";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new SiegeServiceImpl(ctx);
        service.load();
        ctx.register(SiegeService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        service.restoreBreaches();
        ctx.everySecond(service::tick);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (service != null) {
            service.save();
        }
    }
}
