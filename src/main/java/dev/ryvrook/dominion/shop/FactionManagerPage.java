package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.service.VaultService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.shop.ShopBody.Btn;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.PageEvent;

import java.util.List;
import java.util.UUID;

/**
 * The faction shop manager for officers. Stock from a vault slot or from the hand at a price, or
 * take entries back.
 */
final class FactionManagerPage extends ShopPage {
    private final UUID factionId;

    FactionManagerPage(PlayerRef player, DominionContext ctx, ShopServiceImpl shops, UUID factionId) {
        super(player, ctx, shops);
        this.factionId = factionId;
    }

    private Faction faction() {
        return ctx.service(FactionService.class).byId(factionId);
    }

    @Override
    protected void render(UICommandBuilder c, UIEventBuilder events) {
        c.append(ShopBody.PAGE);
        c.set("#Sidebar.Visible", false);
        c.set("#PageTitle.Text", Messages.get(ShopText.LABEL_FACTION_SHOP));
        c.set("#F1.Visible", true);
        c.set("#F1.Text", Messages.get(ShopText.BUTTON_CLOSE));
        ShopBody.on(events, "#F1", "close", "");
        ShopBody body = new ShopBody(c, events, "#Body");
        Faction f = faction();
        if (f == null || !f.isMember(uuid())) {
            c.set("#Heading.Text", Messages.get(ShopText.LABEL_FACTION_SHOP));
            body.text(Messages.get(ShopText.NO_LONGER_MEMBER), Msg.MUTED);
            return;
        }
        if (!shops.isOfficer(uuid(), f)) {
            c.set("#Heading.Text", Messages.get(ShopText.LABEL_FACTION_SHOP));
            body.text(Messages.get(ShopText.MANAGE_OFFICERS), Msg.MUTED);
            return;
        }
        List<ShopServiceImpl.Stock> stock = shops.stock(factionId);
        int max = shops.maxFactionEntries();
        c.set("#Heading.Text", Messages.get(ShopText.HEADING_MANAGE, "faction", f.tag()));
        c.set("#Subheading.Text", Messages.get(ShopText.ENTRIES_USED, "used", stock.size(), "max", max));
        c.set("#Footer.Text", Messages.get(ShopText.FOOTER_SALES));
        c.set("#F2.Visible", true);
        c.set("#F2.Text", Messages.get(ShopText.BUTTON_VIEW));
        ShopBody.on(events, "#F2", "view", "");

        if (!shops.factionEnabled()) {
            body.text(Messages.get(ShopText.SHOPS_OFF_TAKE_BACK), Msg.WARN);
        } else {
            vaultSection(body, f);
            body.section(Messages.get(ShopText.SECTION_STOCK_HELD));
            body.text(Messages.get(ShopText.STOCK_HELD_HINT), Msg.MUTED);
            body.form(new String[] {Messages.get(ShopText.LABEL_PRICE_EACH)}, null, Btn.of(Messages.get(ShopText.BUTTON_STOCK_HELD), "stock", ""));
        }
        body.section(Messages.get(ShopText.SECTION_IN_SHOP));
        if (stock.isEmpty()) {
            body.text(Messages.get(ShopText.SHOP_EMPTY), Msg.MUTED);
        } else {
            body.row(null, Messages.get(ShopText.LABEL_ITEM), Msg.MUTED, new String[] {Messages.get(ShopText.LABEL_IN_STOCK),
                    Messages.get(ShopText.LABEL_PRICE_EACH), Messages.get(ShopText.LABEL_STOCKED_BY)}, null);
        }
        for (ShopServiceImpl.Stock s : stock) {
            body.row(s.itemId(), Names.of(s.itemId()), null,
                    new String[] {String.valueOf(s.quantity()), money(s.price()), s.stockedByName()}, null,
                    Btn.of(Messages.get(ShopText.BUTTON_TAKE_BACK), "unstock", s.id().toString()));
        }
    }

    /** Most vault slots listed at once, so a large vault cannot make the page too long. */
    static final int MAX_VAULT_ROWS = 40;

    /** Lists the filled vault slots with a price field and a stock button each. */
    private void vaultSection(ShopBody body, Faction f) {
        body.section(Messages.get(ShopText.SECTION_FROM_VAULT));
        VaultService vault = ctx.serviceOrNull(VaultService.class);
        if (vault == null) {
            body.text(Messages.get(ShopText.VAULT_UNAVAILABLE), Msg.MUTED);
            return;
        }
        if (!ctx.service(FactionService.class).can(uuid(), FactionAction.VAULT)) {
            body.text(Messages.get(ShopText.VAULT_RANK), Msg.MUTED);
            return;
        }
        int pages = vault.pages(f);
        int slots = Math.max(1, ctx.settings().integer("vault.slotsPerPage"));
        int shown = 0;
        int more = 0;
        for (int page = 1; page <= pages; page++) {
            for (int slot = 1; slot <= slots; slot++) {
                ItemStack item = vault.peekSlot(f, page, slot);
                if (item == null || ItemStack.isEmpty(item)) {
                    continue;
                }
                if (shown >= MAX_VAULT_ROWS) {
                    more++;
                    continue;
                }
                if (shown == 0) {
                    body.text(Messages.get(ShopText.VAULT_HINT), Msg.MUTED);
                    body.row(null, Messages.get(ShopText.LABEL_ITEM), Msg.MUTED, new String[] {Messages.get(ShopText.LABEL_PAGE),
                            Messages.get(ShopText.LABEL_SLOT), Messages.get(ShopText.LABEL_AMOUNT)}, null);
                }
                body.row(item.getItemId(), Names.of(item.getItemId()), null,
                        new String[] {String.valueOf(page), String.valueOf(slot), String.valueOf(item.getQuantity())},
                        new String[] {""}, Btn.of(Messages.get(ShopText.BUTTON_STOCK), "vstock", page + ":" + slot + ":" + item.getItemId()));
                shown++;
            }
        }
        if (shown == 0) {
            body.text(Messages.get(ShopText.VAULT_EMPTY), Msg.MUTED);
        } else if (more > 0) {
            body.text(Messages.get(more == 1 ? ShopText.MORE_SLOT : ShopText.MORE_SLOTS, "count", more), Msg.MUTED);
        }
    }

    @Override
    protected boolean onAction(PageEvent event) {
        switch (event.action) {
            case "stock" -> {
                Double price = parsePrice(event.text());
                if (price == null) {
                    reply(Result.fail(Messages.get(ShopText.ENTER_PRICE)));
                } else {
                    reply(shops.factionStockHeld(uuid(), price));
                }
            }
            case "vstock" -> {
                Double price = parsePrice(event.text());
                int[] where = pageSlot(event.value());
                if (where == null) {
                    reply(Result.fail(Messages.get(ShopText.BAD_VAULT_SLOT)));
                } else if (price == null) {
                    reply(Result.fail(Messages.get(ShopText.ENTER_PRICE)));
                } else {
                    reply(shops.factionStock(uuid(), where[0], where[1], price, itemOf(event.value())));
                }
            }
            case "unstock" -> reply(shops.factionUnstock(uuid(), event.value()));
            case "view" -> {
                Faction f = faction();
                if (f != null) {
                    shops.openFactionShop(playerRef, f);
                }
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Parses a page and slot sent as page:slot or page:slot:itemId. */
    static int[] pageSlot(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The item id the row showed, or null when the value carries none. */
    static String itemOf(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", 3);
        return parts.length == 3 && !parts[2].isBlank() ? parts[2] : null;
    }
}
