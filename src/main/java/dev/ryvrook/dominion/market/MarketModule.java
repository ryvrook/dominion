package dev.ryvrook.dominion.market;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.MarketService;

/** Market board, buy orders, auctions, storefronts and the mailbox. */
public final class MarketModule implements DominionModule {
    private MarketServiceImpl service;

    public MarketModule() {}

    @Override
    public String name() {
        return "market";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new MarketServiceImpl(ctx);
        service.load();
        ctx.register(MarketService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        MarketServiceImpl market = service;
        ctx.everyMinute(market::tick);
        ctx.onPlayerReady(market::onReady);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (service != null) {
            service.saveAll();
        }
    }
}
