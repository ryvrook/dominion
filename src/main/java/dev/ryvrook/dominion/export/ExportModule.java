package dev.ryvrook.dominion.export;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.api.ApiHolder;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.service.ExportService;

import java.util.function.Consumer;

/** Web map snapshot export and the public API for other mods. */
public final class ExportModule implements DominionModule {
    private ExportServiceImpl service;
    private Consumer<DominionEvent> listener;

    public ExportModule() {}

    @Override
    public String name() {
        return "export";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new ExportServiceImpl(ctx, new MapData(ctx));
        ctx.register(ExportService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ApiHolder.set(new ApiImpl(ctx, new DominionTerritories(service.data())));
        listener = service::onEvent;
        ctx.bus().subscribe(listener);
        ctx.everySecond(service::secondTick);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (listener != null) {
            ctx.bus().unsubscribe(listener);
        }
        if (ctx.settings().bool("export.enabled")) {
            // Called on the server thread before the IO thread stops, so it writes directly.
            service.write(false);
        }
        ApiHolder.set(null);
    }
}
