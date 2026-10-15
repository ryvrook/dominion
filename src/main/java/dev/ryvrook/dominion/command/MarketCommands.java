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
import dev.ryvrook.dominion.text.Messages;
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
            c.bad(Messages.get(CommandText.THE_MARKET_IS_TURNED_OFF_ON));
            return null;
        }
        return m;
    }

    @Override
    void register(PlayerCmd root) {
        PlayerCmd market = root.sub(new PlayerCmd("market", Messages.get(CommandText.DESC_OPEN_THE_MARKET)));
        market.addAliases("m");
        market.handler(c -> {
            if (market(c) != null) {
                ui().openPanel(c.player(), "market");
            }
        });

        PlayerCmd sell = market.sub(new PlayerCmd("sell", Messages.get(CommandText.DESC_LIST_THE_STACK_IN_YOUR_HAND)));
        RequiredArg<Double> sellPrice = sell.withRequiredArg("price", Messages.get(CommandText.ARG_PRICE), ArgTypes.DOUBLE);
        sell.handler(c -> sell(c, c.arg(sellPrice), 0));
        PlayerCmd sellSome = new PlayerCmd(Messages.get(CommandText.DESC_LIST_PART_OF_THE_STACK_IN));
        RequiredArg<Double> sellSomePrice = sellSome.withRequiredArg("price", Messages.get(CommandText.ARG_PRICE), ArgTypes.DOUBLE);
        RequiredArg<Integer> sellSomeAmount = sellSome.withRequiredArg("amount", Messages.get(CommandText.ARG_HOW_MANY_ITEMS), ArgTypes.INTEGER);
        sellSome.handler(c -> {
            if (count(c, c.arg(sellSomeAmount))) {
                sell(c, c.arg(sellSomePrice), c.arg(sellSomeAmount));
            }
        });
        sell.variant(sellSome);

        PlayerCmd buy = market.sub(new PlayerCmd("buy", Messages.get(CommandText.DESC_BUY_A_LISTING)));
        RequiredArg<String> buyId = buy.withRequiredArg("id", Messages.get(CommandText.ARG_LISTING_ID), ArgTypes.STRING);
        buy.handler(c -> buy(c, c.arg(buyId), 0));
        PlayerCmd buySome = new PlayerCmd(Messages.get(CommandText.DESC_BUY_PART_OF_A_LISTING));
        RequiredArg<String> buySomeId = buySome.withRequiredArg("id", Messages.get(CommandText.ARG_LISTING_ID), ArgTypes.STRING);
        RequiredArg<Integer> buySomeAmount = buySome.withRequiredArg("amount", Messages.get(CommandText.ARG_HOW_MANY_ITEMS), ArgTypes.INTEGER);
        buySome.handler(c -> {
            if (count(c, c.arg(buySomeAmount))) {
                buy(c, c.arg(buySomeId), c.arg(buySomeAmount));
            }
        });
        buy.variant(buySome);

        PlayerCmd cancel = market.sub(new PlayerCmd("cancel", Messages.get(CommandText.DESC_TAKE_DOWN_ONE_OF_YOUR_LISTINGS)));
        RequiredArg<String> cancelId = cancel.withRequiredArg("id", Messages.get(CommandText.ARG_LISTING_ID), ArgTypes.STRING);
        cancel.handler(c -> {
            MarketService m = market(c);
            Listing l = m == null ? null : resolve(c, m, c.arg(cancelId), m.mine(c.uuid()), "listing");
            if (l != null) {
                c.reply(m.cancel(c.uuid(), l.id()));
            }
        });

        PlayerCmd order = market.sub(new PlayerCmd("order", Messages.get(CommandText.DESC_PLACE_A_BUY_ORDER)));
        RequiredArg<String> orderItem = order.withRequiredArg("itemId", Messages.get(CommandText.ARG_ITEM_ID), ArgTypes.STRING);
        RequiredArg<Double> orderPrice = order.withRequiredArg("price", Messages.get(CommandText.ARG_PRICE_PER_ITEM), ArgTypes.DOUBLE);
        RequiredArg<Integer> orderAmount = order.withRequiredArg("amount", Messages.get(CommandText.ARG_HOW_MANY_ITEMS), ArgTypes.INTEGER);
        order.handler(c -> {
            MarketService m = market(c);
            if (m != null && price(c, c.arg(orderPrice)) && count(c, c.arg(orderAmount))) {
                c.reply(m.placeOrder(c.uuid(), c.arg(orderItem).trim(), c.arg(orderPrice), c.arg(orderAmount)));
            }
        });

        PlayerCmd fulfil = market.sub(new PlayerCmd("fulfil", Messages.get(CommandText.DESC_SELL_INTO_A_BUY_ORDER)));
        fulfil.addAliases("fulfill");
        RequiredArg<String> fulfilId = fulfil.withRequiredArg("id", Messages.get(CommandText.ARG_ORDER_ID), ArgTypes.STRING);
        fulfil.handler(c -> fulfil(c, c.arg(fulfilId), 0));
        PlayerCmd fulfilSome = new PlayerCmd(Messages.get(CommandText.DESC_SELL_PART_OF_A_BUY_ORDER));
        RequiredArg<String> fulfilSomeId = fulfilSome.withRequiredArg("id", Messages.get(CommandText.ARG_ORDER_ID), ArgTypes.STRING);
        RequiredArg<Integer> fulfilSomeAmount = fulfilSome.withRequiredArg("amount", Messages.get(CommandText.ARG_HOW_MANY_ITEMS), ArgTypes.INTEGER);
        fulfilSome.handler(c -> {
            if (count(c, c.arg(fulfilSomeAmount))) {
                fulfil(c, c.arg(fulfilSomeId), c.arg(fulfilSomeAmount));
            }
        });
        fulfil.variant(fulfilSome);

        market.sub(new PlayerCmd("mine", Messages.get(CommandText.DESC_LIST_YOUR_OPEN_LISTINGS_ORDERS_AND))).handler(c -> {
            MarketService m = market(c);
            if (m == null) {
                return;
            }
            List<Listing> mine = m.mine(c.uuid());
            if (mine.isEmpty()) {
                c.info(Messages.get(CommandText.YOU_HAVE_NOTHING_ON_THE_MARKET));
                return;
            }
            c.info(Messages.get(CommandText.YOUR_MARKET_ENTRIES));
            for (Listing l : mine) {
                line(c, l);
            }
        });

        market.sub(new PlayerCmd("mailbox", Messages.get(CommandText.DESC_SHOW_WHAT_IS_WAITING_FOR_YOU))).handler(c -> {
            MarketService m = market(c);
            if (m == null) {
                return;
            }
            List<ItemStack> items = m.mailbox(c.uuid());
            double coins = m.mailboxCoins(c.uuid());
            if (items.isEmpty() && coins <= 0) {
                c.info(Messages.get(CommandText.YOUR_MAILBOX_IS_EMPTY));
                return;
            }
            c.info(Messages.get(CommandText.YOUR_MAILBOX));
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
            c.send(Msg.muted(" " + Messages.get(CommandText.USE_F_MARKET_COLLECT_TO_TAKE)));
        });

        market.sub(new PlayerCmd("collect", Messages.get(CommandText.DESC_TAKE_EVERYTHING_FROM_YOUR_MAILBOX))).handler(c -> {
            MarketService m = market(c);
            if (m != null) {
                c.reply(m.collect(c.uuid()));
            }
        });

        registerAuction(root);
        registerStorefront(root);
    }

    private void registerAuction(PlayerCmd root) {
        PlayerCmd auction = root.sub(new PlayerCmd("auction", Messages.get(CommandText.DESC_AUCTION)));
        auction.handler(c -> {
            if (market(c) != null) {
                c.info(Messages.get(CommandText.USE_F_AUCTION_SELL_WITH_A));
            }
        });

        PlayerCmd sell = auction.sub(new PlayerCmd("sell", Messages.get(CommandText.DESC_AUCTION_SELL)));
        RequiredArg<Double> start1 = sell.withRequiredArg("start", Messages.get(CommandText.ARG_STARTING_BID), ArgTypes.DOUBLE);
        sell.handler(c -> auction(c, c.arg(start1), 0.0, ctx.settings().integer("market.auctionHours")));
        PlayerCmd sellBuyout = new PlayerCmd(Messages.get(CommandText.DESC_AUCTION_WITH_A_BUYOUT_PRICE));
        RequiredArg<Double> start2 = sellBuyout.withRequiredArg("start", Messages.get(CommandText.ARG_STARTING_BID), ArgTypes.DOUBLE);
        RequiredArg<Double> buyout2 = sellBuyout.withRequiredArg("buyout", Messages.get(CommandText.ARG_BUYOUT_PRICE_0_FOR_NONE), ArgTypes.DOUBLE);
        sellBuyout.handler(c -> auction(c, c.arg(start2), c.arg(buyout2), ctx.settings().integer("market.auctionHours")));
        sell.variant(sellBuyout);
        PlayerCmd sellHours = new PlayerCmd(Messages.get(CommandText.DESC_AUCTION_WITH_A_BUYOUT_PRICE_AND));
        RequiredArg<Double> start3 = sellHours.withRequiredArg("start", Messages.get(CommandText.ARG_STARTING_BID), ArgTypes.DOUBLE);
        RequiredArg<Double> buyout3 = sellHours.withRequiredArg("buyout", Messages.get(CommandText.ARG_BUYOUT_PRICE_0_FOR_NONE), ArgTypes.DOUBLE);
        RequiredArg<Integer> hours3 = sellHours.withRequiredArg("hours", Messages.get(CommandText.ARG_LENGTH_IN_HOURS), ArgTypes.INTEGER);
        sellHours.handler(c -> auction(c, c.arg(start3), c.arg(buyout3), c.arg(hours3)));
        sell.variant(sellHours);

        PlayerCmd bid = auction.sub(new PlayerCmd("bid", Messages.get(CommandText.DESC_BID_ON_AN_AUCTION)));
        RequiredArg<String> bidId = bid.withRequiredArg("id", Messages.get(CommandText.ARG_AUCTION_ID), ArgTypes.STRING);
        RequiredArg<Double> bidAmount = bid.withRequiredArg("amount", Messages.get(CommandText.ARG_YOUR_BID), ArgTypes.DOUBLE);
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
        PlayerCmd storefront = root.sub(new PlayerCmd("storefront", Messages.get(CommandText.DESC_SHOW_YOUR_FACTIONS_PINNED_LISTINGS)));
        storefront.handler(c -> {
            MarketService m = market(c);
            Faction f = m == null ? null : requireFaction(c);
            if (f == null) {
                return;
            }
            List<Listing> pinned = m.storefront(f);
            if (pinned.isEmpty()) {
                c.info(Messages.get(CommandText.STOREFRONT_EMPTY, "tag", f.tag()));
                return;
            }
            c.info(Messages.get(CommandText.STOREFRONT_OF, "tag", f.tag()));
            for (Listing l : pinned) {
                line(c, l);
            }
        });
        for (boolean pin : new boolean[] {true, false}) {
            PlayerCmd cmd = storefront.sub(new PlayerCmd(pin ? "pin" : "unpin",
                    pin ? Messages.get(CommandText.DESC_PIN_A_LISTING_TO_THE_FACTION) : Messages.get(CommandText.DESC_REMOVE_A_LISTING_FROM_THE_STOREFRONT)));
            RequiredArg<String> id = cmd.withRequiredArg("id", Messages.get(CommandText.ARG_LISTING_ID), ArgTypes.STRING);
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
            c.bad(Messages.get(CommandText.THE_BUYOUT_CANNOT_BE_NEGATIVE_USE));
            return;
        }
        if (b > 0 && b <= start) {
            c.bad(Messages.get(CommandText.THE_BUYOUT_MUST_BE_HIGHER_THAN));
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
            case SALE -> Messages.get(CommandText.LISTING_SALE, "what", what, "price", money(l.price()), "seller", l.sellerName());
            case ORDER -> Messages.get(CommandText.LISTING_ORDER, "what", what, "price", money(l.price()), "buyer", l.sellerName());
            case AUCTION -> Messages.get(CommandText.LISTING_AUCTION, "what", what, "price", money(l.price()))
                    + " " + (l.topBidderName() == null ? Messages.get(CommandText.AUCTION_NO_BIDS) : Messages.get(CommandText.AUCTION_BID_BY, "name", l.topBidderName()))
                    + (l.buyout() > 0 ? ", " + Messages.get(CommandText.AUCTION_BUYOUT, "price", money(l.buyout())) : "");
        };
    }

    private void line(Call c, Listing l) {
        String kind = switch (l.kind()) {
            case SALE -> Messages.get(CommandText.KIND_SALE);
            case ORDER -> Messages.get(CommandText.KIND_ORDER);
            case AUCTION -> Messages.get(CommandText.KIND_AUCTION);
        };
        c.send(Message.join(Msg.colored(" " + IdPrefix.shortId(l.id()), Msg.GOLD), Msg.muted("  " + kind + "  "),
                Msg.colored(label(l), Msg.TEXT), Msg.muted(", " + Messages.get(CommandText.ENDS_IN, "time", Time.remaining(l.expiresAt() - Time.now())))));
    }

    private String money(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? Msg.number(amount) : eco.format(amount);
    }

    private static boolean price(Call c, Double amount) {
        if (amount == null || amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            c.bad(Messages.get(CommandText.THE_PRICE_MUST_BE_MORE_THAN));
            return false;
        }
        return true;
    }

    private static boolean count(Call c, Integer amount) {
        if (amount == null || amount <= 0) {
            c.bad(Messages.get(CommandText.THE_AMOUNT_MUST_BE_AT_LEAST));
            return false;
        }
        return true;
    }
}
