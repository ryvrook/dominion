package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.command.Call;
import dev.ryvrook.dominion.command.PlayerCmd;
import dev.ryvrook.dominion.config.CommandNames;
import dev.ryvrook.dominion.config.Perms;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.FactionService;
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
        PlayerCmd root = new PlayerCmd("shop", "Open the server shop.");
        root.handler(c -> shops.openAdminShop(c.player(), null));
        PlayerCmd withCategory = new PlayerCmd("Open the server shop on a category.");
        RequiredArg<String> category = withCategory.withRequiredArg("category", "Shop category", ArgTypes.STRING);
        withCategory.handler(c -> shops.openAdminShop(c.player(), c.arg(category)));
        root.variant(withCategory);

        root.sub(new PlayerCmd("sell", "Sell the item in your hand to the server shop."))
                .handler(c -> c.reply(shops.sellHeld(c.uuid())));
        return root;
    }

    /** The editor command name from commands.shopEditRoot, read once at registration. */
    private String shopEditRoot() {
        String name = CommandNames.name(ctx.settings().text("commands.shopEditRoot"), CommandNames.DEFAULT_SHOP_EDIT);
        CommandNames.registeredShopEdit(name);
        return name;
    }

    /** /fashop edit, /fashop set and /fashop remove. Every sub command checks the permission itself. */
    private PlayerCmd admin() {
        PlayerCmd root = new PlayerCmd(shopEditRoot(), "Edit the server shop.").permission(Perms.admin());
        root.handler(this::adminUsage);

        root.sub(new PlayerCmd("edit", "Open the shop editor.").permission(Perms.admin()))
                .handler(c -> shops.openAdminEditor(c.player()));

        PlayerCmd set = root.sub(new PlayerCmd("set", "Add or change a shop entry.").permission(Perms.admin()));
        RequiredArg<String> setCategory = set.withRequiredArg("category", "Shop category", ArgTypes.STRING);
        RequiredArg<String> setItem = set.withRequiredArg("itemId", "Item id", ArgTypes.STRING);
        RequiredArg<Double> setBuy = set.withRequiredArg("buy", "Buy price, 0 for not sold", ArgTypes.DOUBLE);
        RequiredArg<Double> setSell = set.withRequiredArg("sell", "Sell price, 0 for not bought", ArgTypes.DOUBLE);
        set.handler(c -> c.reply(shops.adminSet(c.arg(setCategory), c.arg(setItem), c.arg(setBuy), c.arg(setSell))));

        PlayerCmd remove = root.sub(new PlayerCmd("remove", "Remove a shop entry.").permission(Perms.admin()));
        RequiredArg<String> removeCategory = remove.withRequiredArg("category", "Shop category", ArgTypes.STRING);
        RequiredArg<String> removeItem = remove.withRequiredArg("itemId", "Item id", ArgTypes.STRING);
        remove.handler(c -> c.reply(shops.adminRemove(c.arg(removeCategory), c.arg(removeItem))));
        return root;
    }

    private void adminUsage(Call c) {
        c.send(Msg.info("Shop admin commands"));
        for (String line : new String[] {"edit", "set <category> <itemId> <buy> <sell>", "remove <category> <itemId>"}) {
            c.send(Msg.colored(" /fashop " + line, Msg.GOLD));
        }
    }

    /** /fshop [tag] and /fshop manage. */
    private PlayerCmd faction() {
        PlayerCmd root = new PlayerCmd("fshop", "Open your faction shop or another faction's shop.");
        root.handler(c -> {
            Faction f = factions().factionOf(c.uuid());
            if (f == null) {
                c.bad("You are not in a faction. Use /fshop with a faction tag to visit another shop.");
                return;
            }
            shops.openFactionShop(c.player(), f);
        });
        PlayerCmd withTag = new PlayerCmd("Open a faction's shop.");
        RequiredArg<String> tag = withTag.withRequiredArg("tag", "Faction tag", ArgTypes.STRING);
        withTag.handler(c -> {
            Faction f = factions().byTag(c.arg(tag));
            if (f == null || f.isSystem()) {
                c.bad("There is no faction called " + c.arg(tag) + ".");
                return;
            }
            shops.openFactionShop(c.player(), f);
        });
        root.variant(withTag);

        root.sub(new PlayerCmd("manage", "Manage your faction shop."))
                .handler(c -> shops.openFactionShopManager(c.player()));
        return root;
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }
}
