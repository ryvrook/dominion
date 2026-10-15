package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Time;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The market board, buy orders, auctions, storefront pins and the mailbox. */
final class MarketCommands extends Section {
    /** Large enough to cover every open listing of one kind in a single browse call. */
    private static final int MAX_SCAN = 5000;

    MarketCommands(DominionContext ctx, Confirmations confirmations, Targets targets) {
        super(ctx, confirmations, targets);
    }

    /** The market or null after telling the caller it is missing or switched off. */
    private MarketService market(Call c) {
        MarketService m = feature(c, MarketService.class);
        if (m != null && !m.enabled()) {
            c.bad("The market is turned off on this server.");
            return null;
        }
        return m;
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd market = root.sub(new PlayerCmd("market", "Open the market."));
        market.addAliases("m");
        market.handler(c -> {
            if (market(c) != null) {
                ui().openPanel(c.player(), "market");
            }
        });

        PlayerCmd sell = market.sub(new PlayerCmd("sell", "List the stack in your hand."));
        RequiredArg<Double> sellPrice = sell.withRequiredArg("price", "Price", ArgTypes.DOUBLE);
        sell.handler(c -> sell(c, c.arg(sellPrice), 0));
        PlayerCmd sellSome = new PlayerCmd("List part of the stack in your hand.");
        RequiredArg<Double> sellSomePrice = sellSome.withRequiredArg("price", "Price", ArgTypes.DOUBLE);
        RequiredArg<Integer> sellSomeAmount = sellSome.withRequiredArg("amount", "How many items", ArgTypes.INTEGER);
        sellSome.handler(c -> {
            if (count(c, c.arg(sellSomeAmount))) {
                sell(c, c.arg(sellSomePrice), c.arg(sellSomeAmount));
            }
        });
        sell.variant(sellSome);

        PlayerCmd buy = market.sub(new PlayerCmd("buy", "Buy a listing."));
        RequiredArg<String> buyId = buy.withRequiredArg("id", "Listing id", ArgTypes.STRING);
        buy.handler(c -> buy(c, c.arg(buyId), 0));
        PlayerCmd buySome = new PlayerCmd("Buy part of a listing.");
        RequiredArg<String> buySomeId = buySome.withRequiredArg("id", "Listing id", ArgTypes.STRING);
        RequiredArg<Integer> buySomeAmount = buySome.withRequiredArg("amount", "How many items", ArgTypes.INTEGER);
        buySome.handler(c -> {
            if (count(c, c.arg(buySomeAmount))) {
                buy(c, c.arg(buySomeId), c.arg(buySomeAmount));
            }
        });
        buy.variant(buySome);

        PlayerCmd cancel = market.sub(new PlayerCmd("cancel", "Take down one of your listings, orders or auctions."));
        RequiredArg<String> cancelId = cancel.withRequiredArg("id", "Listing id", ArgTypes.STRING);
        cancel.handler(c -> {
            MarketService m = market(c);
            Listing l = m == null ? null : resolve(c, m, c.arg(cancelId), m.mine(c.uuid()), "listing");
            if (l != null) {
                c.reply(m.cancel(c.uuid(), l.id()));
            }
        });

        PlayerCmd order = market.sub(new PlayerCmd("order", "Place a buy order."));
        RequiredArg<String> orderItem = order.withRequiredArg("itemId", "Item id", ArgTypes.STRING);
        RequiredArg<Double> orderPrice = order.withRequiredArg("price", "Price per item", ArgTypes.DOUBLE);
        RequiredArg<Integer> orderAmount = order.withRequiredArg("amount", "How many items", ArgTypes.INTEGER);
        order.handler(c -> {
            MarketService m = market(c);
            if (m != null && price(c, c.arg(orderPrice)) && count(c, c.arg(orderAmount))) {
                c.reply(m.placeOrder(c.uuid(), c.arg(orderItem).trim(), c.arg(orderPrice), c.arg(orderAmount)));
            }
        });

        PlayerCmd fulfil = market.sub(new PlayerCmd("fulfil", "Sell into a buy order."));
        fulfil.addAliases("fulfill");
        RequiredArg<String> fulfilId = fulfil.withRequiredArg("id", "Order id", ArgTypes.STRING);
        fulfil.handler(c -> fulfil(c, c.arg(fulfilId), 0));
        PlayerCmd fulfilSome = new PlayerCmd("Sell part of a buy order.");
        RequiredArg<String> fulfilSomeId = fulfilSome.withRequiredArg("id", "Order id", ArgTypes.STRING);
        RequiredArg<Integer> fulfilSomeAmount = fulfilSome.withRequiredArg("amount", "How many items", ArgTypes.INTEGER);
        fulfilSome.handler(c -> {
            if (count(c, c.arg(fulfilSomeAmount))) {
                fulfil(c, c.arg(fulfilSomeId), c.arg(fulfilSomeAmount));
            }
        });
        fulfil.variant(fulfilSome);

        market.sub(new PlayerCmd("mine", "List your open listings, orders and auctions.")).handler(c -> {
            MarketService m = market(c);
            if (m == null) {
                return;
            }
            List<Listing> mine = m.mine(c.uuid());
            if (mine.isEmpty()) {
                c.info("You have nothing on the market.");
                return;
            }
            c.info("Your market entries");
            for (Listing l : mine) {
                line(c, l);
            }
        });

        market.sub(new PlayerCmd("mailbox", "Show what is waiting for you.")).handler(c -> {
            MarketService m = market(c);
            if (m == null) {
                return;
            }
            List<ItemStack> items = m.mailbox(c.uuid());
            double coins = m.mailboxCoins(c.uuid());
            if (items.isEmpty() && coins <= 0) {
                c.info("Your mailbox is empty.");
                return;
            }
            c.info("Your mailbox");
            if (coins > 0) {
                c.send(Msg.colored(" " + money(coins), Msg.GOLD));
            }
            Map<String, Integer> totals = new LinkedHashMap<>();
            for (ItemStack stack : items) {
                if (stack != null && !stack.isEmpty()) {
                    totals.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> e : totals.entrySet()) {
                c.send(Msg.colored(" " + e.getValue() + " " + e.getKey(), Msg.TEXT));
            }
            c.send(Msg.muted(" Use /f market collect to take everything."));
        });

        market.sub(new PlayerCmd("collect", "Take everything from your mailbox.")).handler(c -> {
            MarketService m = market(c);
            if (m != null) {
                c.reply(m.collect(c.uuid()));
            }
        });

        registerAuction(root);
        registerStorefront(root);
    }

    private void registerAuction(PlayerCmd root) {
        PlayerCmd auction = root.sub(new PlayerCmd("auction", "Auction the stack in your hand or bid on one."));
        auction.handler(c -> {
            if (market(c) != null) {
                c.info("Use /f auction sell with a starting bid, or /f auction bid with an id and an amount.");
            }
        });

        PlayerCmd sell = auction.sub(new PlayerCmd("sell", "Auction the stack in your hand."));
        RequiredArg<Double> start1 = sell.withRequiredArg("start", "Starting bid", ArgTypes.DOUBLE);
        sell.handler(c -> auction(c, c.arg(start1), 0.0, ctx.settings().integer("market.auctionHours")));
        PlayerCmd sellBuyout = new PlayerCmd("Auction with a buyout price.");
        RequiredArg<Double> start2 = sellBuyout.withRequiredArg("start", "Starting bid", ArgTypes.DOUBLE);
        RequiredArg<Double> buyout2 = sellBuyout.withRequiredArg("buyout", "Buyout price, 0 for none", ArgTypes.DOUBLE);
        sellBuyout.handler(c -> auction(c, c.arg(start2), c.arg(buyout2), ctx.settings().integer("market.auctionHours")));
        sell.variant(sellBuyout);
        PlayerCmd sellHours = new PlayerCmd("Auction with a buyout price and a length.");
        RequiredArg<Double> start3 = sellHours.withRequiredArg("start", "Starting bid", ArgTypes.DOUBLE);
        RequiredArg<Double> buyout3 = sellHours.withRequiredArg("buyout", "Buyout price, 0 for none", ArgTypes.DOUBLE);
        RequiredArg<Integer> hours3 = sellHours.withRequiredArg("hours", "Length in hours", ArgTypes.INTEGER);
        sellHours.handler(c -> auction(c, c.arg(start3), c.arg(buyout3), c.arg(hours3)));
        sell.variant(sellHours);

        PlayerCmd bid = auction.sub(new PlayerCmd("bid", "Bid on an auction."));
        RequiredArg<String> bidId = bid.withRequiredArg("id", "Auction id", ArgTypes.STRING);
        RequiredArg<Double> bidAmount = bid.withRequiredArg("amount", "Your bid", ArgTypes.DOUBLE);
        bid.handler(c -> {
            MarketService m = market(c);
            if (m == null || !price(c, c.arg(bidAmount))) {
                return;
            }
            Listing l = resolve(c, m, c.arg(bidId), scan(m, Listing.Kind.AUCTION), "auction");
            if (l != null) {
                c.reply(m.bid(c.uuid(), l.id(), c.arg(bidAmount)));
            }
        });
    }

    private void registerStorefront(PlayerCmd root) {
        PlayerCmd storefront = root.sub(new PlayerCmd("storefront", "Show your faction's pinned listings."));
        storefront.handler(c -> {
            MarketService m = market(c);
            Faction f = m == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            List<Listing> pinned = m.storefront(f);
            if (pinned.isEmpty()) {
                c.info(f.tag() + " has nothing pinned. Pin a listing with /f storefront pin and its id.");
                return;
            }
            c.info("Storefront of " + f.tag());
            for (Listing l : pinned) {
                line(c, l);
            }
        });
        for (boolean pin : new boolean[] {true, false}) {
            PlayerCmd cmd = storefront.sub(new PlayerCmd(pin ? "pin" : "unpin",
                    pin ? "Pin a listing to the faction storefront." : "Remove a listing from the storefront."));
            RequiredArg<String> id = cmd.withRequiredArg("id", "Listing id", ArgTypes.STRING);
            cmd.handler(c -> {
                MarketService m = market(c);
                Faction f = m == null ? null : requireFaction(c);
                if (f == null) {
                    return;
                }
                List<Listing> candidates = new ArrayList<>(m.storefront(f));
                candidates.addAll(m.mine(c.uuid()));
                candidates.addAll(scan(m, Listing.Kind.SALE));
                Listing l = resolve(c, m, c.arg(id), candidates, "listing");
                if (l != null) {
                    c.reply(m.setStorefront(c.uuid(), l.id(), pin));
                }
            });
        }
    }

    // ---- actions ---------------------------------------------------------------

    /** An amount of zero asks the market to list the whole held stack. */
    private void sell(Call c, Double price, int amount) {
        MarketService m = market(c);
        if (m != null && price(c, price)) {
            c.reply(m.sellHeld(c.uuid(), price, amount));
        }
    }

    private void buy(Call c, String id, int amount) {
        MarketService m = market(c);
        Listing l = m == null ? null : resolve(c, m, id, scan(m, Listing.Kind.SALE), "listing");
        if (l != null) {
            c.reply(m.buy(c.uuid(), l.id(), amount > 0 ? amount : l.quantity()));
        }
    }

    private void fulfil(Call c, String id, int amount) {
        MarketService m = market(c);
        Listing l = m == null ? null : resolve(c, m, id, scan(m, Listing.Kind.ORDER), "order");
        if (l != null) {
            c.reply(m.fulfilOrder(c.uuid(), l.id(), amount > 0 ? amount : l.quantity()));
        }
    }

    private void auction(Call c, Double start, Double buyout, int hours) {
        MarketService m = market(c);
        if (m == null || !price(c, start)) {
            return;
        }
        double b = buyout == null ? 0 : buyout;
        if (b < 0 || Double.isNaN(b) || Double.isInfinite(b)) {
            c.bad("The buyout cannot be negative. Use 0 for no buyout.");
            return;
        }
        if (b > 0 && b <= start) {
            c.bad("The buyout must be higher than the starting bid.");
            return;
        }
        // The market checks the length itself and names the lengths it allows.
        c.reply(m.auctionHeld(c.uuid(), start, b, hours));
    }

    // ---- lookup ----------------------------------------------------------------

    /** Every open listing of one kind. Browse pages start at 1, so one page that covers everything is enough. */
    static List<Listing> scan(MarketService m, Listing.Kind kind) {
        int size = Math.max(1, Math.min(MAX_SCAN, m.count(kind, "") + 1));
        List<Listing> got = m.browse(kind, "", "newest", 1, size);
        return got == null ? new ArrayList<>() : new ArrayList<>(got);
    }

    private Listing resolve(Call c, MarketService m, String typed, List<Listing> candidates, String noun) {
        String text = typed == null ? "" : typed.trim();
        try {
            Listing exact = m.byId(UUID.fromString(text));
            if (exact != null && !exact.closed()) {
                return exact;
            }
        } catch (IllegalArgumentException notFull) {
            // Fall through to prefix matching.
        }
        List<Listing> open = new ArrayList<>();
        for (Listing l : candidates) {
            if (l != null && !l.closed()) {
                open.add(l);
            }
        }
        return IdPrefix.resolve(c, text, open, Listing::id, this::label, noun);
    }

    private String label(Listing l) {
        String what = l.quantity() + " " + l.itemId();
        return switch (l.kind()) {
            case SALE -> what + " for " + money(l.price()) + " from " + l.sellerName();
            case ORDER -> "Wanted " + what + " at " + money(l.price()) + " each by " + l.sellerName();
            case AUCTION -> what + " at " + money(l.price()) + (l.topBidderName() == null ? " with no bids" : " bid by " + l.topBidderName())
                    + (l.buyout() > 0 ? ", buyout " + money(l.buyout()) : "");
        };
    }

    private void line(Call c, Listing l) {
        String kind = l.kind().name().toLowerCase(Locale.ROOT);
        c.send(Message.join(Msg.colored(" " + IdPrefix.shortId(l.id()), Msg.GOLD), Msg.muted("  " + kind + "  "),
                Msg.colored(label(l), Msg.TEXT), Msg.muted(", ends in " + Time.remaining(l.expiresAt() - Time.now()))));
    }

    private String money(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? Msg.number(amount) : eco.format(amount);
    }

    private static boolean price(Call c, Double amount) {
        if (amount == null || amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            c.bad("The price must be more than zero.");
            return false;
        }
        return true;
    }

    private static boolean count(Call c, Integer amount) {
        if (amount == null || amount <= 0) {
            c.bad("The amount must be at least 1.");
            return false;
        }
        return true;
    }
}
