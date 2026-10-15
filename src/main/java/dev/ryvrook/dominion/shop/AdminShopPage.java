package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.shop.ShopBody.Btn;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.PageEvent;

import java.util.List;

/** The server shop. Categories on the left, items with buy and sell buttons on the right. */
final class AdminShopPage extends ShopPage {
    private String category;

    AdminShopPage(PlayerRef player, DominionContext ctx, ShopServiceImpl shops, String category) {
        super(player, ctx, shops);
        this.category = category;
    }

    @Override
    protected void render(UICommandBuilder c, UIEventBuilder events) {
        c.append(ShopBody.PAGE);
        c.set("#PageTitle.Text", Messages.get(ShopText.LABEL_SHOP));
        c.set("#Heading.Text", Messages.get(ShopText.LABEL_SERVER_SHOP));
        c.set("#Subheading.Text", balanceLine());
        c.set("#Footer.Text", Messages.get(ShopText.LABEL_PRICES_EACH));

        List<String> categories = shops.adminCategories();
        if (category == null || shops.categoryName(category) == null) {
            category = categories.isEmpty() ? null : categories.get(0);
        }
        ShopBody tabs = new ShopBody(c, events, "#Tabs");
        for (String name : categories) {
            tabs.tab(name, String.valueOf(shops.entryCount(name)), name.equalsIgnoreCase(category), "cat", name);
        }

        ShopBody body = new ShopBody(c, events, "#Body");
        if (category == null) {
            body.text(Messages.get(ShopText.EMPTY_SHOP), Msg.MUTED);
        } else {
            quantityRow(body);
            body.section(category);
            body.row(null, Messages.get(ShopText.LABEL_ITEM), Msg.MUTED,
                    new String[] {Messages.get(ShopText.LABEL_BUY_PRICE), Messages.get(ShopText.LABEL_SELL_PRICE), ""}, null);
            List<ShopServiceImpl.AdminEntry> entries = shops.entries(category);
            for (ShopServiceImpl.AdminEntry e : entries) {
                Btn buy = e.buy() > 0 ? Btn.of(Messages.get(ShopText.BUTTON_BUY, "count", quantity), "buy", e.itemId()) : null;
                Btn sell = e.sell() > 0 ? Btn.of(Messages.get(ShopText.BUTTON_SELL, "count", quantity), "sell", e.itemId()) : null;
                body.row(e.itemId(), Names.of(e.itemId()), null,
                        new String[] {e.buy() > 0 ? money(e.buy()) : Messages.get(ShopText.LABEL_NOT_SOLD),
                                e.sell() > 0 ? money(e.sell()) : Messages.get(ShopText.LABEL_NOT_BOUGHT), ""},
                        null, buy == null ? sell : buy, buy == null ? null : sell);
            }
            if (entries.isEmpty()) {
                body.text(Messages.get(ShopText.EMPTY_CATEGORY), Msg.MUTED);
            }
        }

        c.set("#F1.Visible", true);
        c.set("#F1.Text", Messages.get(ShopText.BUTTON_SELL_HELD));
        ShopBody.on(events, "#F1", "sellheld", "");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", Messages.get(ShopText.BUTTON_CLOSE));
        ShopBody.on(events, "#F2", "close", "");
    }

    @Override
    protected boolean onAction(PageEvent event) {
        switch (event.action) {
            case "cat" -> category = event.value();
            case "buy" -> reply(shops.buyAdmin(uuid(), category, event.value(), quantity));
            case "sell" -> reply(shops.sellAdmin(uuid(), category, event.value(), quantity));
            case "sellheld" -> reply(shops.sellHeld(uuid()));
            default -> {
                return false;
            }
        }
        return true;
    }
}
