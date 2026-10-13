package dev.ryvrook.dominion.nation;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.NationService;

/** Nations of allied factions, stored in nations.json. Nation chat lives in the chat module. */
public final class NationModule implements DominionModule {
    private NationServiceImpl service;

    public NationModule() {}

    @Override
    public String name() {
        return "nation";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new NationServiceImpl(ctx);
        service.load();
        ctx.register(NationService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        service.reconcile();
        ctx.bus().subscribe(service::onEvent);
        ctx.everyMinute(service::saveIfDirty);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (service != null) {
            service.save();
        }
    }
}
