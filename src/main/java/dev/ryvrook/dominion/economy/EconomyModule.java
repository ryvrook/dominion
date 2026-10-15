package dev.ryvrook.dominion.economy;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.EconomyService;

/** Player wallets through the chosen provider, faction banks and the daily economy cycle. */
public final class EconomyModule implements DominionModule {
    private EconomyServiceImpl service;

    public EconomyModule() {}

    @Override
    public String name() {
        return "economy";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new EconomyServiceImpl(ctx);
        ctx.register(EconomyService.class, service);
        ctx.register(CoinageSeam.class, service.seam());
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.daily(service::dailyTick);
        ctx.everyMinute(service::stipendTick);
        ctx.everyMinute(service::migrationTick);
        ctx.everyMinute(service::coinageTick);
        ctx.onPlayerReady(player -> service.onReady(player.getUuid()));
        ctx.onPlayerQuit(service::forget);
    }
}
