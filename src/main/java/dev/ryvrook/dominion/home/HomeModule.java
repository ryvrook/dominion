package dev.ryvrook.dominion.home;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.HomeService;

import java.util.logging.Level;

/** Faction home, warps, stuck and teleport warmups. */
public final class HomeModule implements DominionModule {
    private HomeServiceImpl service;

    public HomeModule() {}

    @Override
    public String name() {
        return "home";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new HomeServiceImpl(ctx);
        ctx.register(HomeService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        HomeServiceImpl homes = service;
        ctx.everySecond(homes::tick);
        ctx.onPlayerQuit(homes::cancel);
        ctx.bus().subscribe(homes::onEvent);
        try {
            ctx.plugin().getEntityStoreRegistry().registerSystem(new WarmupDamageSystem(homes));
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion could not watch damage for teleport warmups");
        }
    }
}
