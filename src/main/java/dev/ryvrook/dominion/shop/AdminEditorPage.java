package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.shop.ShopBody.Btn;
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
        c.set("#PageTitle.Text", "Shop editor");
        c.set("#Heading.Text", "Shop editor");
        c.set("#Subheading.Text", shops.adminEnabled() ? "The admin shop is open" : "The admin shop is closed in the settings");
        c.set("#Footer.Text", "A price of 0 means that direction is not offered.");

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
            tabs.tab(pending, "new", pending.equals(category), "cat", pending);
        }

        ShopBody body = new ShopBody(c, events, "#Body");
        body.section("New category");
        body.form(new String[] {"Name"}, null, Btn.of("Create", "newcat", ""));
        if (category == null) {
            body.text("Create a category or press Seed defaults to start with a small catalogue.", Msg.MUTED);
        } else {
            body.section("Add an item to " + category);
            body.form(new String[] {"Item id", "Buy", "Sell"}, null, Btn.of("Add", "add", ""));
            body.section(category);
            List<ShopServiceImpl.AdminEntry> entries = shops.entries(category);
            if (entries.isEmpty()) {
                body.text("This category has no items yet. It is saved once you add one.", Msg.MUTED);
            } else {
                body.row(null, "Item", Msg.MUTED, new String[] {"Buy price", "Sell price", ""}, null);
            }
            for (ShopServiceImpl.AdminEntry e : entries) {
                String display = Names.of(e.itemId());
                String label = display.equalsIgnoreCase(e.itemId().replace('_', ' ')) ? e.itemId() : display + "  " + e.itemId();
                body.row(e.itemId(), label, null,
                        new String[] {e.buy() > 0 ? money(e.buy()) : "Not sold", e.sell() > 0 ? money(e.sell()) : "Not bought", ""},
                        new String[] {plain(e.buy()), plain(e.sell())},
                        Btn.of("Save", "save", e.itemId()), Btn.of("Remove", "remove", e.itemId()));
            }
            if (!entries.isEmpty()) {
                body.buttons(null, Btn.of("Delete category", "delcat", category));
            }
        }

        c.set("#F1.Visible", true);
        c.set("#F1.Text", "Close");
        ShopBody.on(events, "#F1", "close", "");
        c.set("#F2.Visible", true);
        c.set("#F2.Text", "Open shop");
        ShopBody.on(events, "#F2", "shop", "");
        c.set("#F3.Visible", true);
        c.set("#F3.Text", "Seed defaults");
        ShopBody.on(events, "#F3", "seed", "");
    }

    @Override
    protected boolean onAction(PageEvent event) {
        if (!playerRef.hasPermission(PERMISSION)) {
            reply(Result.fail("You need the admin permission to edit the shop."));
            close();
            return false;
        }
        switch (event.action) {
            case "cat" -> category = event.value();
            case "newcat" -> {
                String name = event.text();
                if (name.isEmpty() || name.length() > 24 || !name.matches("[A-Za-z0-9 _-]+")) {
                    reply(Result.fail("Category names use letters, numbers, spaces, dashes or underscores and up to 24 characters."));
                } else if (shops.categoryName(name) != null) {
                    category = shops.categoryName(name);
                } else {
                    pending = name;
                    category = name;
                    reply(Result.pass("Category " + name + " is ready. Add an item to save it."));
                }
            }
            case "add" -> {
                Double buy = event.text2().isEmpty() ? Double.valueOf(0) : parsePrice(event.text2());
                Double sell = event.text3().isEmpty() ? Double.valueOf(0) : parsePrice(event.text3());
                if (event.text().isEmpty()) {
                    reply(Result.fail("Type the id of the item to add."));
                } else if (buy == null || sell == null) {
                    reply(Result.fail("Prices must be numbers."));
                } else {
                    reply(shops.adminSet(category, event.text(), buy, sell));
                }
            }
            case "save" -> {
                Double buy = event.text().isEmpty() ? Double.valueOf(0) : parsePrice(event.text());
                Double sell = event.text2().isEmpty() ? Double.valueOf(0) : parsePrice(event.text2());
                if (buy == null || sell == null) {
                    reply(Result.fail("Prices must be numbers."));
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
