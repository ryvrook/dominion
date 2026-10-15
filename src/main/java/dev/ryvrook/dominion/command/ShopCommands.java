package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.ShopService;
import dev.ryvrook.dominion.text.Messages;

/** The /f entry point to faction shops. The shop module registers /fshop on its own. */
final class ShopCommands extends Section {

    ShopCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd shop = root.sub(new PlayerCmd("shop", Messages.get(CommandText.DESC_OPEN_YOUR_FACTION_SHOP)));
        shop.handler(c -> {
            ShopService s = feature(c, ShopService.class);
            Faction f = s == null ? null : requireFaction(c);
            if (f != null) {
                s.openFactionShop(c.player(), f);
            }
        });
        PlayerCmd shopTag = new PlayerCmd(Messages.get(CommandText.DESC_OPEN_ANOTHER_FACTIONS_SHOP));
        RequiredArg<String> tag = shopTag.withRequiredArg("tag", Messages.get(CommandText.ARG_FACTION_TAG), ArgTypes.STRING);
        shopTag.handler(c -> {
            ShopService s = feature(c, ShopService.class);
            Faction f = s == null ? null : factionByTag(c, c.arg(tag));
            if (f != null && f.isSystem()) {
                c.bad(Messages.get(CommandText.NO_SHOP, "tag", f.tag()));
            } else if (f != null) {
                s.openFactionShop(c.player(), f);
            }
        });
        shop.variant(shopTag);

        shop.sub(new PlayerCmd("manage", Messages.get(CommandText.DESC_STOCK_AND_PRICE_YOUR_FACTION_SHOP))).handler(c -> {
            ShopService s = feature(c, ShopService.class);
            if (s != null && requireFaction(c) != null) {
                s.openFactionShopManager(c.player());
            }
        });
    }
}
