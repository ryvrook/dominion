package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.command.Call;
import dev.ryvrook.dominion.command.PlayerCmd;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;

import java.util.List;

/**
 * Shop commands. The f and fa roots belong to other modules, so the faction shop lives under
 * /fshop and the admin tools under /fashop.
 */
final class ShopCommands {
    private final DominionContext ctx;
    private final ShopServiceImpl shops;

    ShopCommands(DominionContext ctx, ShopServiceImpl shops) {
        this.ctx = ctx;
        this.shops = shops;
    }

    List<PlayerCmd> build() {
        return List.of(shop(), admin(), faction());
    }

    /** /shop [category] and /shop sell. */
    private PlayerCmd shop() {
        PlayerCmd root = new PlayerCmd("shop", Messages.get(ShopText.CMD_SHOP));
        root.handler(c -> shops.openAdminShop(c.player(), null));
        PlayerCmd withCategory = new PlayerCmd(Messages.get(ShopText.CMD_SHOP_CATEGORY));
        RequiredArg<String> category = withCategory.withRequiredArg("category", Messages.get(ShopText.ARG_CATEGORY), ArgTypes.STRING);
        withCategory.handler(c -> shops.openAdminShop(c.player(), c.arg(category)));
        root.variant(withCategory);

        root.sub(new PlayerCmd("sell", Messages.get(ShopText.CMD_SELL)))
                .handler(c -> c.reply(shops.sellHeld(c.uuid())));
        return root;
    }

    /** /fashop edit, /fashop set and /fashop remove. Every sub command checks the permission itself. */
    private PlayerCmd admin() {
        PlayerCmd root = new PlayerCmd("fashop", Messages.get(ShopText.CMD_FASHOP)).permission(AdminEditorPage.PERMISSION);
        root.handler(this::adminUsage);

        root.sub(new PlayerCmd("edit", Messages.get(ShopText.CMD_EDIT)).permission(AdminEditorPage.PERMISSION))
                .handler(c -> shops.openAdminEditor(c.player()));

        PlayerCmd set = root.sub(new PlayerCmd("set", Messages.get(ShopText.CMD_SET)).permission(AdminEditorPage.PERMISSION));
        RequiredArg<String> setCategory = set.withRequiredArg("category", Messages.get(ShopText.ARG_CATEGORY), ArgTypes.STRING);
        RequiredArg<String> setItem = set.withRequiredArg("itemId", Messages.get(ShopText.ARG_ITEM_ID), ArgTypes.STRING);
        RequiredArg<Double> setBuy = set.withRequiredArg("buy", Messages.get(ShopText.ARG_BUY), ArgTypes.DOUBLE);
        RequiredArg<Double> setSell = set.withRequiredArg("sell", Messages.get(ShopText.ARG_SELL), ArgTypes.DOUBLE);
        set.handler(c -> c.reply(shops.adminSet(c.arg(setCategory), c.arg(setItem), c.arg(setBuy), c.arg(setSell))));

        PlayerCmd remove = root.sub(new PlayerCmd("remove", Messages.get(ShopText.CMD_REMOVE)).permission(AdminEditorPage.PERMISSION));
        RequiredArg<String> removeCategory = remove.withRequiredArg("category", Messages.get(ShopText.ARG_CATEGORY), ArgTypes.STRING);
        RequiredArg<String> removeItem = remove.withRequiredArg("itemId", Messages.get(ShopText.ARG_ITEM_ID), ArgTypes.STRING);
        remove.handler(c -> c.reply(shops.adminRemove(c.arg(removeCategory), c.arg(removeItem))));
        return root;
    }

    private void adminUsage(Call c) {
        c.send(Msg.info(Messages.get(ShopText.ADMIN_HELP)));
        for (String line : new String[] {"edit", "set <category> <itemId> <buy> <sell>", "remove <category> <itemId>"}) {
            c.send(Msg.colored(" /fashop " + line, Msg.GOLD));
        }
    }

    /** /fshop [tag] and /fshop manage. */
    private PlayerCmd faction() {
        PlayerCmd root = new PlayerCmd("fshop", Messages.get(ShopText.CMD_FSHOP));
        root.handler(c -> {
            Faction f = factions().factionOf(c.uuid());
            if (f == null) {
                c.bad(Messages.get(ShopText.FSHOP_NO_FACTION));
                return;
            }
            shops.openFactionShop(c.player(), f);
        });
        PlayerCmd withTag = new PlayerCmd(Messages.get(ShopText.CMD_FSHOP_TAG));
        RequiredArg<String> tag = withTag.withRequiredArg("tag", Messages.get(ShopText.ARG_TAG), ArgTypes.STRING);
        withTag.handler(c -> {
            Faction f = factions().byTag(c.arg(tag));
            if (f == null || f.isSystem()) {
                c.bad(Messages.get(ShopText.NO_FACTION_CALLED, "tag", c.arg(tag)));
                return;
            }
            shops.openFactionShop(c.player(), f);
        });
        root.variant(withTag);

        root.sub(new PlayerCmd("manage", Messages.get(ShopText.CMD_MANAGE)))
                .handler(c -> shops.openFactionShopManager(c.player()));
        return root;
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }
}
