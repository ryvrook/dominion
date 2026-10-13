package dev.ryvrook.dominion.power;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.PowerService;

/** Player power, faction power and the minute regen and decay cycle. */
public final class PowerModule implements DominionModule {
    private PowerServiceImpl service;

    public PowerModule() {}

    @Override
    public String name() {
        return "power";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new PowerServiceImpl(ctx);
        ctx.register(PowerService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.everyMinute(service::tick);
    }
}
