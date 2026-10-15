package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.shop.ShopBody.Btn;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.PageEvent;

import java.util.List;

/** The admin shop editor. Categories on the left, entries with price fields on the right. */
final class AdminEditorPage extends ShopPage {
    static final String PERMISSION = "dominion.admin";

    private String category;
    /** A category created in the page that has no entries yet, so it is not saved. */
    private String pending;

    AdminEditorPage(PlayerRef player, DominionContext ctx, ShopServiceImpl shops, String category) {
        super(player, ctx, shops);
        this.category = category;
    }

    @Override
    protected void render(UICommandBuilder c, UIEventBuilder events) {
        c.append(ShopBody.PAGE);
        c.set("#PageTitle.Text", Messages.get(ShopText.LABEL_EDITOR));
        c.set("#Heading.Text", Messages.get(ShopText.LABEL_EDITOR));
        c.set("#Subheading.Text", Messages.get(shops.adminEnabled() ? ShopText.EDITOR_OPEN : ShopText.EDITOR_CLOSED));
        c.set("#Footer.Text", Messages.get(ShopText.EDITOR_FOOTER));

        List<String> categories = shops.adminCategories();
        if (pending != null && shops.categoryName(pending) != null) {
            pending = null;
        }
        if (category == null || (shops.categoryName(category) == null && !category.equals(pending))) {
            category = pending != null ? pending : categories.isEmpty() ? null : categories.get(0);
        }
        ShopBody tabs = new ShopBody(c, events, "#Tabs");
        for (String name : categories) {
            tabs.tab(name, String.valueOf(shops.entryCount(name)), name.equalsIgnoreCase(category), "cat", name);
        }
        if (pending != null) {
            tabs.tab(pending, Messages.get(ShopText.TAB_NEW), pending.equals(category), "cat", pending);
        }

        ShopBody body = new ShopBody(c, events, "#Body");
        body.section(Messages.get(ShopText.SECTION_NEW_CATEGORY));
        body.form(new String[] {Messages.get(ShopText.LABEL_NAME)}, null, Btn.of(Messages.get(ShopText.BUTTON_CREATE), "newcat", ""));
        if (category == null) {
            body.text(Messages.get(ShopText.EDITOR_START), Msg.MUTED);
        } else {
            body.section(Messages.get(ShopText.SECTION_ADD_ITEM, "category", category));
            body.form(new String[] {Messages.get(ShopText.LABEL_ITEM_ID), Messages.get(ShopText.LABEL_BUY), Messages.get(ShopText.LABEL_SELL)},
                    null, Btn.of(Messages.get(ShopText.BUTTON_ADD), "add", ""));
            body.section(category);
            List<ShopServiceImpl.AdminEntry> entries = shops.entries(category);
            if (entries.isEmpty()) {
                body.text(Messages.get(ShopText.EDITOR_CATEGORY_UNSAVED), Msg.MUTED);
            } else {
                body.row(null, Messages.get(ShopText.LABEL_ITEM), Msg.MUTED,
                        new String[] {Messages.get(ShopText.LABEL_BUY_PRICE), Messages.get(ShopText.LABEL_SELL_PRICE), ""}, null);
            }
            for (ShopServiceImpl.AdminEntry e : entries) {
                String display = Names.of(e.itemId());
                String label = display.equalsIgnoreCase(e.itemId().replace('_', ' ')) ? e.itemId() : display + "  " + e.itemId();
                body.row(e.itemId(), label, null,
                        new String[] {e.buy() > 0 ? money(e.buy()) : Messages.get(ShopText.LABEL_NOT_SOLD),
                                e.sell() > 0 ? money(e.sell()) : Messages.get(ShopText.LABEL_NOT_BOUGHT), ""},
                        new String[] {plain(e.buy()), plain(e.sell())},
                        Btn.of(Messages.get(ShopText.BUTTON_SAVE), "save", e.itemId()), Btn.of(Messages.get(ShopText.BUTTON_REMOVE), "remove", e.itemId()));
            }
            if (!entries.isEmpty()) {
                body.buttons(null, Btn.of(Messages.get(ShopText.BUTTON_DELETE_CATEGORY), "delcat", category));
            }
        }

        c.set("#F1.Visible", true);
        c.set("#F1.Text", Messages.get(ShopText.BUTTON_CLOSE));
        ShopBody.on(events, "#F1", "close", "");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", Messages.get(ShopText.BUTTON_OPEN_SHOP));
        ShopBody.on(events, "#F2", "shop", "");
        c.set("#F3.Visible", true);
        c.set("#F3.Text", Messages.get(ShopText.BUTTON_SEED));
        ShopBody.on(events, "#F3", "seed", "");
    }

    @Override
    protected boolean onAction(PageEvent event) {
        if (!playerRef.hasPermission(PERMISSION)) {
            reply(Result.fail(Messages.get(ShopText.EDITOR_NO_PERMISSION)));
            close();
            return false;
        }
        switch (event.action) {
            case "cat" -> category = event.value();
            case "newcat" -> {
                String name = event.text();
                if (name.isEmpty() || name.length() > 24 || !name.matches("[A-Za-z0-9 _-]+")) {
                    reply(Result.fail(Messages.get(ShopText.CATEGORY_NAME_RULES, "max", 24)));
                } else if (shops.categoryName(name) != null) {
                    category = shops.categoryName(name);
                } else {
                    pending = name;
                    category = name;
                    reply(Result.pass(Messages.get(ShopText.CATEGORY_READY, "category", name)));
                }
            }
            case "add" -> {
                Double buy = event.text2().isEmpty() ? Double.valueOf(0) : parsePrice(event.text2());
                Double sell = event.text3().isEmpty() ? Double.valueOf(0) : parsePrice(event.text3());
                if (event.text().isEmpty()) {
                    reply(Result.fail(Messages.get(ShopText.TYPE_ITEM_ID)));
                } else if (buy == null || sell == null) {
                    reply(Result.fail(Messages.get(ShopText.PRICES_NUMBERS)));
                } else {
                    reply(shops.adminSet(category, event.text(), buy, sell));
                }
            }
            case "save" -> {
                Double buy = event.text().isEmpty() ? Double.valueOf(0) : parsePrice(event.text());
                Double sell = event.text2().isEmpty() ? Double.valueOf(0) : parsePrice(event.text2());
                if (buy == null || sell == null) {
                    reply(Result.fail(Messages.get(ShopText.PRICES_NUMBERS)));
                } else {
                    reply(shops.adminSet(category, event.value(), buy, sell));
                }
            }
            case "remove" -> reply(shops.adminRemove(category, event.value()));
            case "delcat" -> reply(shops.removeCategory(event.value()));
            case "seed" -> reply(shops.seedDefaults());
            case "shop" -> {
                shops.openAdminShop(playerRef, category);
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String plain(double price) {
        if (price <= 0) {
            return "0";
        }
        return price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price);
    }
}
