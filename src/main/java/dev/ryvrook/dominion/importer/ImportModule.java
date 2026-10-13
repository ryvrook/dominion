package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.ImportService;

/** Imports factions and claims from other Hytale land mods. Source data is only ever read. */
public final class ImportModule implements DominionModule {

    public ImportModule() {}

    @Override
    public String name() {
        return "importer";
    }

    @Override
    public void init(DominionContext ctx) {
        ctx.register(ImportService.class, new ImportServiceImpl(ctx));
    }
}
