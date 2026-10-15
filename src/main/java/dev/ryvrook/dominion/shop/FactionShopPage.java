package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.shop.ShopBody.Btn;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.PageEvent;

import java.util.List;
import java.util.UUID;

/** A faction's shop as seen by a buyer. Payments go to the faction bank. */
final class FactionShopPage extends ShopPage {
    private final UUID factionId;

    FactionShopPage(PlayerRef player, DominionContext ctx, ShopServiceImpl shops, UUID factionId) {
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
        Faction f = faction();
        ShopBody body = new ShopBody(c, events, "#Body");
        c.set("#F1.Visible", true);
        c.set("#F1.Text", Messages.get(ShopText.BUTTON_CLOSE));
        ShopBody.on(events, "#F1", "close", "");
        if (f == null) {
            c.set("#Heading.Text", Messages.get(ShopText.LABEL_FACTION_SHOP));
            body.text(Messages.get(ShopText.FACTION_GONE), Msg.MUTED);
            return;
        }
        c.set("#PageTitle.Text", Messages.get(ShopText.TITLE_FACTION_SHOP, "faction", f.tag()));
        c.set("#Heading.Text", f.label());
        c.set("#Subheading.Text", balanceLine());
        c.set("#Footer.Text", Messages.get(ShopText.FOOTER_PAYMENTS, "faction", f.tag()));
        if (!shops.factionEnabled()) {
            body.text(Messages.get(ShopText.FACTION_SHOPS_OFF), Msg.MUTED);
            return;
        }
        List<ShopServiceImpl.Stock> stock = shops.stock(factionId);
        quantityRow(body);
        body.section(Messages.get(ShopText.SECTION_FOR_SALE));
        if (stock.isEmpty()) {
            body.text(Messages.get(ShopText.NOTHING_FOR_SALE, "faction", f.tag()), Msg.MUTED);
        } else {
            body.row(null, Messages.get(ShopText.LABEL_ITEM), Msg.MUTED,
                    new String[] {Messages.get(ShopText.LABEL_IN_STOCK), Messages.get(ShopText.LABEL_PRICE_EACH), ""}, null);
        }
        for (ShopServiceImpl.Stock s : stock) {
            int n = Math.min(quantity, s.quantity());
            body.row(s.itemId(), Names.of(s.itemId()), null,
                    new String[] {String.valueOf(s.quantity()), money(s.price()), ""}, null,
                    Btn.of(Messages.get(ShopText.BUTTON_BUY, "count", n), "buy", s.id().toString()));
        }
        if (shops.isOfficer(uuid(), f)) {
            c.set("#F2.Visible", true);
            c.set("#F2.Text", Messages.get(ShopText.BUTTON_MANAGE));
            ShopBody.on(events, "#F2", "manage", "");
        }
    }

    @Override
    protected boolean onAction(PageEvent event) {
        switch (event.action) {
            case "buy" -> reply(shops.buyStock(uuid(), factionId, event.value(), quantity));
            case "manage" -> {
                shops.openFactionShopManager(playerRef);
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
