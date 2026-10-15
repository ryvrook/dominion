package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.ui.Body.Btn;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The market tab of the panel with sub tabs for browsing, selling, buy orders, the player's own
 * listings, auctions and the mailbox. Prices are shown per item.
 */
final class MarketTab {
    static final String ID = "market";

    static final String BROWSE = "browse";
    static final String SELL = "sell";
    static final String ORDERS = "orders";
    static final String MINE = "mine";
    static final String AUCTIONS = "auctions";
    static final String MAILBOX = "mailbox";

    /** Sort keys passed to MarketService.browse, with their pill labels. */
    static final String[][] SORTS = {
            {"newest", "Newest"}, {"price_asc", "Cheapest"}, {"price_desc", "Priciest"}, {"ending", "Ending soon"}
    };

    private final DominionContext ctx;
    private final DominionPanel panel;
    private String sub = BROWSE;
    private String search = "";
    private String sort = "newest";
    private int page;

    MarketTab(DominionContext ctx, DominionPanel panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    /** Listings per page, ui.marketPageSize. */
    private int pageSize() {
        return Math.max(1, ctx.settings().integer("ui.marketPageSize"));
    }

    /** Auction length in hours when none is named, market.auctionHours. */
    private int auctionHours() {
        return Math.max(1, ctx.settings().integer("market.auctionHours"));
    }

    private MarketService market() {
        MarketService m = ctx.serviceOrNull(MarketService.class);
        return m != null && m.enabled() ? m : null;
    }

    boolean available() {
        return market() != null;
    }

    String count() {
        MarketService m = market();
        if (m == null) {
            return "";
        }
        int waiting = m.mailbox(panel.uuid()).size();
        return waiting > 0 || m.mailboxCoins(panel.uuid()) > 0 ? "Mail" : "";
    }

    void build(UICommandBuilder c, Body body, Faction f) {
        MarketService m = market();
        if (m == null) {
            panel.title(c, "Market", "");
            body.text("The market is closed on this server.", Msg.MUTED);
            return;
        }
        body.buttons(pill("Browse", BROWSE), pill("Sell", SELL), pill("Orders", ORDERS), pill("Mine", MINE),
                pill("Auctions", AUCTIONS), pill("Mailbox", MAILBOX));
        switch (sub) {
            case SELL -> buildSell(c, body);
            case ORDERS -> buildOrders(c, body, m);
            case MINE -> buildMine(c, body, m);
            case AUCTIONS -> buildAuctions(c, body, m);
            case MAILBOX -> buildMailbox(c, body, m);
            default -> {
                sub = BROWSE;
                buildBrowse(c, body, m);
            }
        }
    }

    private Btn pill(String label, String id) {
        return Btn.of(id.equals(sub) ? "[ " + label + " ]" : label, "mkt_sub", id);
    }

    private void buildBrowse(UICommandBuilder c, Body body, MarketService m) {
        int total = m.count(Listing.Kind.SALE, search);
        int pages = Math.max(1, (total + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, pages - 1));
        panel.title(c, "Market", total + " listings, page " + (page + 1) + " of " + pages);
        body.field("Search items", search, Btn.of("Search", "mkt_search"), Btn.of("Clear", "mkt_clear"));
        List<Btn> sorts = new ArrayList<>();
        for (String[] s : SORTS) {
            sorts.add(Btn.of(s[0].equals(sort) ? "[ " + s[1] + " ]" : s[1], "mkt_sort", s[0]));
        }
        body.buttons(sorts.toArray(new Btn[0]));
        body.itemRow(null, "Item", Msg.MUTED, new String[] {"Amount", "Price each", "Seller"}, null, null, null);
        List<Listing> rows = m.browse(Listing.Kind.SALE, search, sort, page + 1, pageSize());
        for (Listing l : rows) {
            String row = body.itemRow(l.itemId(), ItemNames.of(l.itemId()), null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), seller(l)},
                    "", Btn.of("Buy", "mkt_buy", l.id().toString()), null);
            colorSeller(c, row, l);
        }
        if (rows.isEmpty()) {
            body.text(search.isEmpty() ? "Nothing is for sale right now." : "Nothing matches your search.", Msg.MUTED);
        } else {
            body.text("Type how many you want in the box next to an item and press Buy. Leave it empty to buy them all.",
                    Msg.MUTED);
        }
        paging(body, pages);
    }

    private void buildSell(UICommandBuilder c, Body body) {
        panel.title(c, "Sell", "List the item in your hand");
        body.text("Hold the item you want to sell in your hand, then type a price for each item and press Sell. "
                + "Leave the amount empty to sell the whole stack. Press Auction instead to put the whole stack up "
                + "for bids for " + auctionHours() + " hours. The price is then the starting bid for the whole stack.");
        body.form(new String[] {"Price", "Amount"}, Btn.of("Sell", "mkt_sell"), Btn.of("Auction", "mkt_auction"));
        double tax = ctx.settings().decimal("market.taxPercent") + ctx.settings().decimal("market.serverTaxPercent");
        if (tax > 0) {
            body.text("Sales pay " + Msg.number(tax) + " percent in tax.", Msg.MUTED);
        }
        body.text("Listings expire after " + ctx.settings().integer("market.listingHours")
                + " hours and unsold items come back through your mailbox.", Msg.MUTED);
    }

    private void buildOrders(UICommandBuilder c, Body body, MarketService m) {
        int total = m.count(Listing.Kind.ORDER, "");
        int pages = Math.max(1, (total + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, pages - 1));
        panel.title(c, "Buy orders", total + " open orders, page " + (page + 1) + " of " + pages);
        body.section("Place an order");
        body.text("Ask for an item by its id, set the price you pay for each one and how many you want. "
                + "The coins are held until sellers fill the order.", Msg.MUTED);
        body.form(new String[] {"Item id", "Price", "Amount"}, Btn.of("Place", "mkt_order"));
        body.section("Open orders");
        body.itemRow(null, "Item", Msg.MUTED, new String[] {"Wanted", "Pays each", "Buyer"}, null, null, null);
        List<Listing> rows = m.browse(Listing.Kind.ORDER, "", "newest", page + 1, pageSize());
        for (Listing l : rows) {
            String row = body.itemRow(l.itemId(), ItemNames.of(l.itemId()), null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), seller(l)},
                    "", Btn.of("Fulfil", "mkt_fulfil", l.id().toString()), null);
            colorSeller(c, row, l);
        }
        if (rows.isEmpty()) {
            body.text("Nobody is asking for anything right now.", Msg.MUTED);
        } else {
            body.text("Carry the items, type how many you can deliver and press Fulfil. Leave it empty to fill the whole order.",
                    Msg.MUTED);
        }
        paging(body, pages);
    }

    private void buildMine(UICommandBuilder c, Body body, MarketService m) {
        List<Listing> mine = m.mine(panel.uuid());
        panel.title(c, "Your listings", mine.size() + " of " + ctx.settings().integer("market.maxListingsPerPlayer"));
        if (mine.isEmpty()) {
            body.text("You have nothing listed. Use the Sell tab to list the item in your hand.", Msg.MUTED);
            return;
        }
        body.itemRow(null, "Item", Msg.MUTED, new String[] {"Amount", "Price", "Ends in"}, null, null, null);
        long now = Time.now();
        for (Listing l : mine) {
            String id = l.id().toString();
            String kind = switch (l.kind()) {
                case SALE -> l.storefront() ? "Pinned sale" : "Sale";
                case ORDER -> "Order";
                case AUCTION -> "Auction";
            };
            Btn pin = l.kind() == Listing.Kind.SALE
                    ? Btn.of(l.storefront() ? "Unpin" : "Pin", "mkt_pin", id) : null;
            body.itemRow(l.itemId(), kind + "  " + ItemNames.of(l.itemId()), l.storefront() ? Msg.GOLD : null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), Time.remaining(l.expiresAt() - now)},
                    null, Btn.of("Cancel", "mkt_cancel", id), pin);
        }
        body.text("Pinned sales show on your faction storefront, which visitors see on your faction page.", Msg.MUTED);
    }

    private void buildAuctions(UICommandBuilder c, Body body, MarketService m) {
        int total = m.count(Listing.Kind.AUCTION, "");
        int pages = Math.max(1, (total + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, pages - 1));
        panel.title(c, "Auctions", total + " running, page " + (page + 1) + " of " + pages);
        body.itemRow(null, "Item", Msg.MUTED, new String[] {"Amount", "Top bid", "Ends in"}, null, null, null);
        long now = Time.now();
        List<Listing> rows = m.browse(Listing.Kind.AUCTION, "", "ending", page + 1, pageSize());
        for (Listing l : rows) {
            String leader = l.topBidderName() == null || l.topBidderName().isBlank() ? "" : "  led by " + l.topBidderName();
            body.itemRow(l.itemId(), ItemNames.of(l.itemId()) + leader, panel.uuid().equals(l.topBidder()) ? Msg.GOOD : null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), Time.remaining(l.expiresAt() - now)},
                    "", Btn.of("Bid", "mkt_bid", l.id().toString()), null);
        }
        if (rows.isEmpty()) {
            body.text("No auctions are running right now.", Msg.MUTED);
        } else {
            double step = ctx.settings().decimal("market.auctionMinStepPercent");
            body.text("Type your bid in the box and press Bid. Each bid must beat the top bid by at least "
                    + Msg.number(step) + " percent. Your coins come back if someone outbids you.", Msg.MUTED);
        }
        paging(body, pages);
    }

    private void buildMailbox(UICommandBuilder c, Body body, MarketService m) {
        List<ItemStack> items = m.mailbox(panel.uuid());
        double coins = m.mailboxCoins(panel.uuid());
        panel.title(c, "Mailbox", items.size() + " stacks waiting");
        body.stat("Coins waiting", money(coins), Msg.GOLD);
        body.buttons(Btn.of("Collect", "mkt_collect"));
        if (items.isEmpty()) {
            body.text("No items are waiting for you.", Msg.MUTED);
        }
        int shown = 0;
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            body.itemRow(stack.getItemId(), ItemNames.of(stack.getItemId()), null,
                    new String[] {String.valueOf(stack.getQuantity()), "", ""}, null, null, null);
            if (++shown >= 40) {
                body.text("More items are waiting. Collect to see the rest.", Msg.MUTED);
                break;
            }
        }
        body.text("Sold items pay out here, and expired or cancelled listings come back here. "
                + "Make room in your inventory before you collect.", Msg.MUTED);
    }

    private void paging(Body body, int pages) {
        body.buttons(page > 0 ? Btn.of("Previous", "mkt_prev") : null,
                page < pages - 1 ? Btn.of("Next", "mkt_next") : null);
    }

    /** Handles market buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        if (!e.action.startsWith("mkt_")) {
            return null;
        }
        MarketService m = market();
        if (m == null) {
            panel.reply(Result.fail("The market is closed on this server."));
            return true;
        }
        UUID me = panel.uuid();
        String text = e.text();
        switch (e.action) {
            case "mkt_sub" -> {
                sub = e.value();
                page = 0;
            }
            case "mkt_search" -> {
                search = text;
                page = 0;
            }
            case "mkt_clear" -> {
                search = "";
                page = 0;
            }
            case "mkt_sort" -> {
                sort = e.value();
                page = 0;
            }
            case "mkt_prev" -> page = Math.max(0, page - 1);
            case "mkt_next" -> page++;
            case "mkt_buy" -> {
                Listing l = listing(m, e.value());
                if (l == null) {
                    panel.reply(Result.fail("That listing is gone."));
                } else {
                    int amount = text.isEmpty() ? l.quantity() : parseInt(text);
                    panel.reply(amount <= 0 ? Result.fail("Type a whole number of items to buy.") : m.buy(me, l.id(), amount));
                }
            }
            case "mkt_sell", "mkt_auction" -> {
                double price = parseDouble(text);
                if (price <= 0) {
                    panel.reply(Result.fail("Type a price above zero first."));
                } else if ("mkt_sell".equals(e.action)) {
                    int amount = e.text2().isEmpty() ? 0 : parseInt(e.text2());
                    panel.reply(amount < 0 ? Result.fail("Type a whole number of items, or leave the amount empty.")
                            : m.sellHeld(me, price, amount));
                } else {
                    panel.reply(m.auctionHeld(me, price, 0, auctionHours()));
                }
            }
            case "mkt_order" -> {
                String item = text;
                double price = parseDouble(e.text2());
                int amount = parseInt(e.text3());
                if (item.isEmpty()) {
                    panel.reply(Result.fail("Type the id of the item you want."));
                } else if (price <= 0) {
                    panel.reply(Result.fail("Type the price you pay for each item."));
                } else if (amount <= 0) {
                    panel.reply(Result.fail("Type how many items you want."));
                } else {
                    panel.reply(m.placeOrder(me, item, price, amount));
                }
            }
            case "mkt_fulfil" -> {
                Listing l = listing(m, e.value());
                if (l == null) {
                    panel.reply(Result.fail("That order is gone."));
                } else {
                    int amount = text.isEmpty() ? l.quantity() : parseInt(text);
                    panel.reply(amount <= 0 ? Result.fail("Type a whole number of items to deliver.")
                            : m.fulfilOrder(me, l.id(), amount));
                }
            }
            case "mkt_cancel" -> {
                UUID id = parseId(e.value());
                panel.reply(id == null ? Result.fail("That listing is gone.") : m.cancel(me, id));
            }
            case "mkt_pin" -> {
                Listing l = listing(m, e.value());
                panel.reply(l == null ? Result.fail("That listing is gone.") : m.setStorefront(me, l.id(), !l.storefront()));
            }
            case "mkt_bid" -> {
                double amount = parseDouble(text);
                UUID id = parseId(e.value());
                if (id == null) {
                    panel.reply(Result.fail("That auction is over."));
                } else if (amount <= 0) {
                    panel.reply(Result.fail("Type your bid in the box first."));
                } else {
                    panel.reply(m.bid(me, id, amount));
                }
            }
            case "mkt_collect" -> panel.reply(m.collect(me));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---- helpers ---------------------------------------------------------

    private String money(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null ? eco.format(amount) : Msg.number(amount);
    }

    private String seller(Listing l) {
        Faction f = l.sellerFaction() == null ? null : ctx.service(FactionService.class).byId(l.sellerFaction());
        if (f != null) {
            return f.tag();
        }
        return l.sellerName() == null || l.sellerName().isBlank() ? "Unknown" : l.sellerName();
    }

    /** Colours the seller column of a row by the viewer's relation to the seller's faction. */
    private void colorSeller(UICommandBuilder c, String row, Listing l) {
        Faction f = l.sellerFaction() == null ? null : ctx.service(FactionService.class).byId(l.sellerFaction());
        if (f == null) {
            return;
        }
        Relation r = ctx.service(RelationService.class).playerToFaction(panel.uuid(), f);
        if (r != null) {
            c.set(row + " #Col3.Style.TextColor", r.color());
        }
    }

    private static Listing listing(MarketService m, String id) {
        UUID uuid = parseId(id);
        Listing l = uuid == null ? null : m.byId(uuid);
        return l == null || l.closed() ? null : l;
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.replace(",", "").replace("_", "").trim());
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    private static double parseDouble(String text) {
        try {
            double d = Double.parseDouble(text.replace(",", "").replace("_", "").trim());
            return Double.isFinite(d) ? d : -1;
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }
}
