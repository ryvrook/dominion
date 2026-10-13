package dev.ryvrook.dominion.shield;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.ShieldService;

/** Daily shield windows, the grace period and end of world mode. */
public final class ShieldModule implements DominionModule {
    private ShieldServiceImpl service;

    public ShieldModule() {}

    @Override
    public String name() {
        return "shield";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new ShieldServiceImpl(ctx);
        ctx.register(ShieldService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.everyMinute(service::tick);
    }
}
