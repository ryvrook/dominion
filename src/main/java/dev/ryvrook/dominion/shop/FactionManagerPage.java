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
        c.set("#PageTitle.Text", "Faction shop");
        c.set("#F1.Visible", true);
        c.set("#F1.Text", "Close");
        ShopBody.on(events, "#F1", "close", "");
        ShopBody body = new ShopBody(c, events, "#Body");
        Faction f = faction();
        if (f == null || !f.isMember(uuid())) {
            c.set("#Heading.Text", "Faction shop");
            body.text("You are no longer in this faction.", Msg.MUTED);
            return;
        }
        if (!shops.isOfficer(uuid(), f)) {
            c.set("#Heading.Text", "Faction shop");
            body.text("Only officers and the leader can manage the faction shop.", Msg.MUTED);
            return;
        }
        List<ShopServiceImpl.Stock> stock = shops.stock(factionId);
        int max = shops.maxFactionEntries();
        c.set("#Heading.Text", "Manage the " + f.tag() + " shop");
        c.set("#Subheading.Text", stock.size() + " of " + max + " entries used");
        c.set("#Footer.Text", "Sales are paid into the faction bank.");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", "View shop");
        ShopBody.on(events, "#F2", "view", "");

        if (!shops.factionEnabled()) {
            body.text("Faction shops are turned off on this server. You can still take items back.", Msg.WARN);
        } else {
            vaultSection(body, f);
            body.section("Stock the held item");
            body.text("Hold the stack you want to sell in your hand, then enter the price for each item.", Msg.MUTED);
            body.form(new String[] {"Price each"}, null, Btn.of("Stock held", "stock", ""));
        }
        body.section("In the shop");
        if (stock.isEmpty()) {
            body.text("The shop is empty.", Msg.MUTED);
        } else {
            body.row(null, "Item", Msg.MUTED, new String[] {"In stock", "Price each", "Stocked by"}, null);
        }
        for (ShopServiceImpl.Stock s : stock) {
            body.row(s.itemId(), Names.of(s.itemId()), null,
                    new String[] {String.valueOf(s.quantity()), money(s.price()), s.stockedByName()}, null,
                    Btn.of("Take back", "unstock", s.id().toString()));
        }
    }

    /** Most vault slots listed at once, so a large vault cannot make the page too long. */
    static final int MAX_VAULT_ROWS = 40;

    /** Lists the filled vault slots with a price field and a stock button each. */
    private void vaultSection(ShopBody body, Faction f) {
        body.section("Stock from the vault");
        VaultService vault = ctx.serviceOrNull(VaultService.class);
        if (vault == null) {
            body.text("The faction vault is not available on this server.", Msg.MUTED);
            return;
        }
        if (!ctx.service(FactionService.class).can(uuid(), FactionAction.VAULT)) {
            body.text("Your rank cannot use the faction vault.", Msg.MUTED);
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
                    body.text("Enter the price for each item next to a vault slot and press Stock.", Msg.MUTED);
                    body.row(null, "Item", Msg.MUTED, new String[] {"Page", "Slot", "Amount"}, null);
                }
                body.row(item.getItemId(), Names.of(item.getItemId()), null,
                        new String[] {String.valueOf(page), String.valueOf(slot), String.valueOf(item.getQuantity())},
                        new String[] {""}, Btn.of("Stock", "vstock", page + ":" + slot + ":" + item.getItemId()));
                shown++;
            }
        }
        if (shown == 0) {
            body.text("The vault is empty.", Msg.MUTED);
        } else if (more > 0) {
            body.text(more + (more == 1 ? " more slot is" : " more slots are") + " not shown. Stock some of these first.", Msg.MUTED);
        }
    }

    @Override
    protected boolean onAction(PageEvent event) {
        switch (event.action) {
            case "stock" -> {
                Double price = parsePrice(event.text());
                if (price == null) {
                    reply(Result.fail("Enter a price for each item."));
                } else {
                    reply(shops.factionStockHeld(uuid(), price));
                }
            }
            case "vstock" -> {
                Double price = parsePrice(event.text());
                int[] where = pageSlot(event.value());
                if (where == null) {
                    reply(Result.fail("That vault slot is not valid."));
                } else if (price == null) {
                    reply(Result.fail("Enter a price for each item."));
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
