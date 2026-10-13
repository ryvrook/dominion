package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.FactionService;

/** Faction lifecycle, membership, ranks and inactivity. */
public final class FactionModule implements DominionModule {
    private FactionServiceImpl service;

    public FactionModule() {}

    @Override
    public String name() {
        return "faction";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new FactionServiceImpl(ctx);
        ctx.register(FactionService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.onPlayerReady(p -> service.onPlayerSeen(p.getUuid(), p.getUsername(), true));
        ctx.onPlayerQuit(uuid -> service.onPlayerSeen(uuid, null, false));
        ctx.everyMinute(service::inactivityTick);
    }
}
