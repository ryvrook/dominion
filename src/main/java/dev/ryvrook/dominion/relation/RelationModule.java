package dev.ryvrook.dominion.relation;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.RelationService;

/** Diplomacy between factions. */
public final class RelationModule implements DominionModule {
    private RelationServiceImpl service;

    public RelationModule() {}

    @Override
    public String name() {
        return "relation";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new RelationServiceImpl(ctx);
        ctx.register(RelationService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.everyMinute(service::tick);
    }
}
