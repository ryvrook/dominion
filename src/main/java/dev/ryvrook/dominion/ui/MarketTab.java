package dev.ryvrook.dominion.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.market.MarketHandoff;
import dev.ryvrook.dominion.market.MarketText;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.model.Relation;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.RelationService;
import dev.ryvrook.dominion.text.Messages;
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
            {"newest", UiText.SORT_NEWEST}, {"price_asc", UiText.SORT_CHEAPEST}, {"price_desc", UiText.SORT_PRICIEST},
            {"ending", UiText.SORT_ENDING}
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

    /** The hand off to Coinage's market while it is active, else null. */
    private MarketHandoff handoff() {
        MarketHandoff h = ctx.serviceOrNull(MarketHandoff.class);
        return h != null && h.active() ? h : null;
    }

    /**
     * While Coinage runs the market, the tab shows a button to its page and Dominion's own mailbox, which still
     * holds what Dominion's market owes the player. Dominion's listing tabs are hidden.
     */
    private boolean buildHandoff(UICommandBuilder c, Body body, MarketService m) {
        if (handoff() == null) {
            return false;
        }
        Btn open = Btn.of(Messages.get(MarketText.COINAGE_OPEN), "mkt_coinage");
        if (MAILBOX.equals(sub)) {
            body.buttons(open, pill(Messages.get(UiText.TAB_MAILBOX), MAILBOX));
            buildMailbox(c, body, m);
            return true;
        }
        panel.title(c, Messages.get(UiText.MARKET), "");
        body.text(Messages.get(MarketText.COINAGE_MARKET), Msg.MUTED);
        if (!count().isEmpty()) {
            body.text(Messages.get(MarketText.COINAGE_MAILBOX), Msg.MUTED);
        }
        body.buttons(open, pill(Messages.get(UiText.TAB_MAILBOX), MAILBOX));
        return true;
    }

    String count() {
        MarketService m = market();
        if (m == null) {
            return "";
        }
        int waiting = m.mailbox(panel.uuid()).size();
        return waiting > 0 || m.mailboxCoins(panel.uuid()) > 0 ? Messages.get(UiText.MAIL) : "";
    }

    void build(UICommandBuilder c, Body body, Faction f) {
        MarketService m = market();
        if (m == null) {
            panel.title(c, Messages.get(UiText.MARKET), "");
            body.text(Messages.get(UiText.MARKET_CLOSED), Msg.MUTED);
            return;
        }
        if (buildHandoff(c, body, m)) {
            return;
        }
        body.buttons(pill(Messages.get(UiText.TAB_BROWSE), BROWSE), pill(Messages.get(UiText.TAB_SELL), SELL),
                pill(Messages.get(UiText.TAB_ORDERS), ORDERS), pill(Messages.get(UiText.TAB_MINE), MINE),
                pill(Messages.get(UiText.TAB_AUCTIONS), AUCTIONS), pill(Messages.get(UiText.TAB_MAILBOX), MAILBOX));
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
        panel.title(c, Messages.get(UiText.MARKET), Messages.get(UiText.LISTINGS_PAGE, "total", total, "page", page + 1, "pages", pages));
        body.field(Messages.get(UiText.SEARCH_ITEMS), search, Btn.of(Messages.get(UiText.SEARCH), "mkt_search"),
                Btn.of(Messages.get(UiText.CLEAR), "mkt_clear"));
        List<Btn> sorts = new ArrayList<>();
        for (String[] s : SORTS) {
            String label = Messages.get(s[1]);
            sorts.add(Btn.of(s[0].equals(sort) ? "[ " + label + " ]" : label, "mkt_sort", s[0]));
        }
        body.buttons(sorts.toArray(new Btn[0]));
        body.itemRow(null, Messages.get(UiText.COL_ITEM), Msg.MUTED, new String[] {Messages.get(UiText.COL_AMOUNT),
                Messages.get(UiText.COL_PRICE_EACH), Messages.get(UiText.COL_SELLER)}, null, null, null);
        List<Listing> rows = m.browse(Listing.Kind.SALE, search, sort, page + 1, pageSize());
        for (Listing l : rows) {
            String row = body.itemRow(l.itemId(), ItemNames.of(l.itemId()), null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), seller(l)},
                    "", Btn.of(Messages.get(UiText.BUY), "mkt_buy", l.id().toString()), null);
            colorSeller(c, row, l);
        }
        if (rows.isEmpty()) {
            body.text(search.isEmpty() ? Messages.get(UiText.NOTHING_FOR_SALE) : Messages.get(UiText.NOTHING_MATCHES), Msg.MUTED);
        } else {
            body.text(Messages.get(UiText.BROWSE_HINT), Msg.MUTED);
        }
        paging(body, pages);
    }

    private void buildSell(UICommandBuilder c, Body body) {
        panel.title(c, Messages.get(UiText.TAB_SELL), Messages.get(UiText.SELL_SUBTITLE));
        body.text(Messages.get(UiText.SELL_HINT, "hours", auctionHours()));
        body.form(new String[] {Messages.get(UiText.FIELD_PRICE), Messages.get(UiText.FIELD_AMOUNT)},
                Btn.of(Messages.get(UiText.TAB_SELL), "mkt_sell"), Btn.of(Messages.get(UiText.AUCTION), "mkt_auction"));
        double tax = ctx.settings().decimal("market.taxPercent") + ctx.settings().decimal("market.serverTaxPercent");
        if (tax > 0) {
            body.text(Messages.get(UiText.SALES_TAX, "percent", Msg.number(tax)), Msg.MUTED);
        }
        body.text(Messages.get(UiText.LISTINGS_EXPIRE, "hours", ctx.settings().integer("market.listingHours")), Msg.MUTED);
    }

    private void buildOrders(UICommandBuilder c, Body body, MarketService m) {
        int total = m.count(Listing.Kind.ORDER, "");
        int pages = Math.max(1, (total + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, pages - 1));
        panel.title(c, Messages.get(UiText.BUY_ORDERS), Messages.get(UiText.ORDERS_PAGE, "total", total, "page", page + 1, "pages", pages));
        body.section(Messages.get(UiText.PLACE_AN_ORDER));
        body.text(Messages.get(UiText.ORDER_HINT), Msg.MUTED);
        body.form(new String[] {Messages.get(UiText.FIELD_ITEM_ID), Messages.get(UiText.FIELD_PRICE), Messages.get(UiText.FIELD_AMOUNT)},
                Btn.of(Messages.get(UiText.PLACE_BUTTON), "mkt_order"));
        body.section(Messages.get(UiText.OPEN_ORDERS));
        body.itemRow(null, Messages.get(UiText.COL_ITEM), Msg.MUTED, new String[] {Messages.get(UiText.COL_WANTED),
                Messages.get(UiText.COL_PAYS_EACH), Messages.get(UiText.COL_BUYER)}, null, null, null);
        List<Listing> rows = m.browse(Listing.Kind.ORDER, "", "newest", page + 1, pageSize());
        for (Listing l : rows) {
            String row = body.itemRow(l.itemId(), ItemNames.of(l.itemId()), null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), seller(l)},
                    "", Btn.of(Messages.get(UiText.FULFIL), "mkt_fulfil", l.id().toString()), null);
            colorSeller(c, row, l);
        }
        if (rows.isEmpty()) {
            body.text(Messages.get(UiText.NO_ORDERS), Msg.MUTED);
        } else {
            body.text(Messages.get(UiText.FULFIL_HINT), Msg.MUTED);
        }
        paging(body, pages);
    }

    private void buildMine(UICommandBuilder c, Body body, MarketService m) {
        List<Listing> mine = m.mine(panel.uuid());
        panel.title(c, Messages.get(UiText.YOUR_LISTINGS), Messages.get(UiText.COUNT_OF_MAX, "count", mine.size(),
                "max", ctx.settings().integer("market.maxListingsPerPlayer")));
        if (mine.isEmpty()) {
            body.text(Messages.get(UiText.NOTHING_LISTED), Msg.MUTED);
            return;
        }
        body.itemRow(null, Messages.get(UiText.COL_ITEM), Msg.MUTED, new String[] {Messages.get(UiText.COL_AMOUNT),
                Messages.get(UiText.COL_PRICE), Messages.get(UiText.COL_ENDS_IN)}, null, null, null);
        long now = Time.now();
        for (Listing l : mine) {
            String id = l.id().toString();
            String kind = switch (l.kind()) {
                case SALE -> l.storefront() ? Messages.get(UiText.KIND_PINNED_SALE) : Messages.get(UiText.KIND_SALE);
                case ORDER -> Messages.get(UiText.KIND_ORDER);
                case AUCTION -> Messages.get(UiText.KIND_AUCTION);
            };
            Btn pin = l.kind() == Listing.Kind.SALE
                    ? Btn.of(l.storefront() ? Messages.get(UiText.UNPIN) : Messages.get(UiText.PIN), "mkt_pin", id) : null;
            body.itemRow(l.itemId(), kind + "  " + ItemNames.of(l.itemId()), l.storefront() ? Msg.GOLD : null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), Time.remaining(l.expiresAt() - now)},
                    null, Btn.of(Messages.get(UiText.CANCEL), "mkt_cancel", id), pin);
        }
        body.text(Messages.get(UiText.PINNED_HINT), Msg.MUTED);
    }

    private void buildAuctions(UICommandBuilder c, Body body, MarketService m) {
        int total = m.count(Listing.Kind.AUCTION, "");
        int pages = Math.max(1, (total + pageSize() - 1) / pageSize());
        page = Math.max(0, Math.min(page, pages - 1));
        panel.title(c, Messages.get(UiText.AUCTIONS), Messages.get(UiText.AUCTIONS_PAGE, "total", total, "page", page + 1, "pages", pages));
        body.itemRow(null, Messages.get(UiText.COL_ITEM), Msg.MUTED, new String[] {Messages.get(UiText.COL_AMOUNT),
                Messages.get(UiText.COL_TOP_BID), Messages.get(UiText.COL_ENDS_IN)}, null, null, null);
        long now = Time.now();
        List<Listing> rows = m.browse(Listing.Kind.AUCTION, "", "ending", page + 1, pageSize());
        for (Listing l : rows) {
            String name = l.topBidderName() == null || l.topBidderName().isBlank() ? ItemNames.of(l.itemId())
                    : Messages.get(UiText.AUCTION_LED_BY, "item", ItemNames.of(l.itemId()), "bidder", l.topBidderName());
            body.itemRow(l.itemId(), name, panel.uuid().equals(l.topBidder()) ? Msg.GOOD : null,
                    new String[] {String.valueOf(l.quantity()), money(l.price()), Time.remaining(l.expiresAt() - now)},
                    "", Btn.of(Messages.get(UiText.BID), "mkt_bid", l.id().toString()), null);
        }
        if (rows.isEmpty()) {
            body.text(Messages.get(UiText.NO_AUCTIONS), Msg.MUTED);
        } else {
            double step = ctx.settings().decimal("market.auctionMinStepPercent");
            body.text(Messages.get(UiText.BID_HINT, "percent", Msg.number(step)), Msg.MUTED);
        }
        paging(body, pages);
    }

    private void buildMailbox(UICommandBuilder c, Body body, MarketService m) {
        List<ItemStack> items = m.mailbox(panel.uuid());
        double coins = m.mailboxCoins(panel.uuid());
        panel.title(c, Messages.get(UiText.MAILBOX), Messages.get(UiText.STACKS_WAITING, "count", items.size()));
        body.stat(Messages.get(UiText.COINS_WAITING), money(coins), Msg.GOLD);
        body.buttons(Btn.of(Messages.get(UiText.COLLECT), "mkt_collect"));
        if (items.isEmpty()) {
            body.text(Messages.get(UiText.NO_ITEMS_WAITING), Msg.MUTED);
        }
        int shown = 0;
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            body.itemRow(stack.getItemId(), ItemNames.of(stack.getItemId()), null,
                    new String[] {String.valueOf(stack.getQuantity()), "", ""}, null, null, null);
            if (++shown >= 40) {
                body.text(Messages.get(UiText.MORE_ITEMS_WAITING), Msg.MUTED);
                break;
            }
        }
        body.text(Messages.get(UiText.MAILBOX_HINT), Msg.MUTED);
    }

    private void paging(Body body, int pages) {
        body.buttons(page > 0 ? Btn.of(Messages.get(UiText.PREVIOUS), "mkt_prev") : null,
                page < pages - 1 ? Btn.of(Messages.get(UiText.NEXT), "mkt_next") : null);
    }

    /** Handles market buttons. Returns null when the action is not ours. */
    Boolean handle(PageEvent e) {
        if ("tab".equals(e.action) && ID.equals(e.value())) {
            // While Coinage runs the market the tab opens its page, unless Dominion's mailbox still holds something.
            MarketHandoff h = market() == null ? null : handoff();
            return h != null && count().isEmpty() && h.open(panel.uuid()) ? Boolean.FALSE : null;
        }
        if ("mkt_coinage".equals(e.action)) {
            MarketHandoff h = handoff();
            if (h != null && h.open(panel.uuid())) {
                return false;
            }
            panel.reply(Result.fail(Messages.get(MarketText.COINAGE_FAILED)));
            return true;
        }
        if (!e.action.startsWith("mkt_")) {
            return null;
        }
        MarketService m = market();
        if (m == null) {
            panel.reply(Result.fail(Messages.get(UiText.MARKET_CLOSED)));
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
                    panel.reply(Result.fail(Messages.get(UiText.LISTING_GONE)));
                } else {
                    int amount = text.isEmpty() ? l.quantity() : parseInt(text);
                    panel.reply(amount <= 0 ? Result.fail(Messages.get(UiText.WHOLE_NUMBER_TO_BUY)) : m.buy(me, l.id(), amount));
                }
            }
            case "mkt_sell", "mkt_auction" -> {
                double price = parseDouble(text);
                if (price <= 0) {
                    panel.reply(Result.fail(Messages.get(UiText.PRICE_ABOVE_ZERO)));
                } else if ("mkt_sell".equals(e.action)) {
                    int amount = e.text2().isEmpty() ? 0 : parseInt(e.text2());
                    panel.reply(amount < 0 ? Result.fail(Messages.get(UiText.WHOLE_NUMBER_OR_EMPTY))
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
                    panel.reply(Result.fail(Messages.get(UiText.ITEM_ID_FIRST)));
                } else if (price <= 0) {
                    panel.reply(Result.fail(Messages.get(UiText.ORDER_PRICE_FIRST)));
                } else if (amount <= 0) {
                    panel.reply(Result.fail(Messages.get(UiText.ORDER_AMOUNT_FIRST)));
                } else {
                    panel.reply(m.placeOrder(me, item, price, amount));
                }
            }
            case "mkt_fulfil" -> {
                Listing l = listing(m, e.value());
                if (l == null) {
                    panel.reply(Result.fail(Messages.get(UiText.ORDER_GONE)));
                } else {
                    int amount = text.isEmpty() ? l.quantity() : parseInt(text);
                    panel.reply(amount <= 0 ? Result.fail(Messages.get(UiText.WHOLE_NUMBER_TO_DELIVER))
                            : m.fulfilOrder(me, l.id(), amount));
                }
            }
            case "mkt_cancel" -> {
                UUID id = parseId(e.value());
                panel.reply(id == null ? Result.fail(Messages.get(UiText.LISTING_GONE)) : m.cancel(me, id));
            }
            case "mkt_pin" -> {
                Listing l = listing(m, e.value());
                panel.reply(l == null ? Result.fail(Messages.get(UiText.LISTING_GONE)) : m.setStorefront(me, l.id(), !l.storefront()));
            }
            case "mkt_bid" -> {
                double amount = parseDouble(text);
                UUID id = parseId(e.value());
                if (id == null) {
                    panel.reply(Result.fail(Messages.get(UiText.AUCTION_OVER)));
                } else if (amount <= 0) {
                    panel.reply(Result.fail(Messages.get(UiText.BID_FIRST)));
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
        return l.sellerName() == null || l.sellerName().isBlank() ? Messages.get(UiText.UNKNOWN_SELLER) : l.sellerName();
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
