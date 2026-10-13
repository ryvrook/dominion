package dev.ryvrook.dominion.vault;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.VaultService;

/** Faction item vaults opened as container windows. */
public final class VaultModule implements DominionModule {
    private VaultServiceImpl service;

    public VaultModule() {}

    @Override
    public String name() {
        return "vault";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new VaultServiceImpl(ctx);
        ctx.register(VaultService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ctx.bus().subscribe(service::onEvent);
        ctx.everyMinute(service::saveDirty);
        ctx.onPlayerQuit(service::forget);
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (service != null) {
            service.saveAll();
        }
    }
}
