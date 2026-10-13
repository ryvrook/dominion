package dev.ryvrook.dominion.ui;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.UiService;

/** Pages, the sidebar HUD and territory banners. */
public final class UiModule implements DominionModule {
    private UiServiceImpl service;

    public UiModule() {}

    @Override
    public String name() {
        return "ui";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new UiServiceImpl(ctx);
        ctx.register(UiService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.onPlayerReady(service::onPlayerReady);
        ctx.onPlayerReady(new TutorialOnJoin(ctx, service)::onPlayerReady);
        ctx.onPlayerQuit(service::onPlayerQuit);
        ctx.everySecond(service::tickHud);
        ctx.everySecond(service::tickPanels);
    }
}
