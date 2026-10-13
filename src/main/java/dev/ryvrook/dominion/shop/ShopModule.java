package dev.ryvrook.dominion.shop;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.command.PlayerCmd;
import dev.ryvrook.dominion.service.ShopService;

/** Admin shops with infinite stock and faction shops stocked by officers. */
public final class ShopModule implements DominionModule {
    private ShopServiceImpl service;

    public ShopModule() {}

    @Override
    public String name() {
        return "shop";
    }

    @Override
    public void init(DominionContext ctx) {
        service = new ShopServiceImpl(ctx);
        service.load();
        ctx.register(ShopService.class, service);
    }

    @Override
    public void start(DominionContext ctx) {
        ShopServiceImpl shops = service;
        ctx.bus().subscribe(shops::onEvent);
        for (PlayerCmd cmd : new ShopCommands(ctx, shops).build()) {
            // Children attach bottom up before the root is registered and finalised.
            cmd.seal();
            ctx.plugin().getCommandRegistry().registerCommand(cmd);
        }
    }

    @Override
    public void shutdown(DominionContext ctx) {
        if (service != null) {
            service.saveAll();
        }
    }
}
