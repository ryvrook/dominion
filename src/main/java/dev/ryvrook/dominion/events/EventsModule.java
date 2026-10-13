package dev.ryvrook.dominion.events;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.EventsService;

/** KOTH hills, outposts, envoys, bounties and the season clock, with the /fe command. */
public final class EventsModule implements DominionModule {
    private EventsServiceImpl service;

    public EventsModule() {}

    @Override
    public String name() {
        return "events";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new EventsServiceImpl(ctx);
        service.load();
        ctx.register(EventsService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        // Kills reach onPlayerKill from the combat death tracking system, so no second death system is registered here.
        // Reads the decay setting once so a value below the minimum is reported at start.
        service.outpostDecay();
        ctx.everySecond(service::tick);
        EventsCommands commands = new EventsCommands(ctx, service);
        ctx.plugin().getCommandRegistry().registerCommand(commands.build());
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (service != null) {
            service.save();
        }
    }
}
