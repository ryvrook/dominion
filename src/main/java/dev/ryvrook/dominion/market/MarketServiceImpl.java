package dev.ryvrook.dominion.market;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.UiService;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Market board, buy orders, auctions, storefronts and the mailbox.
 *
 * All market state is guarded by one lock, and no economy call is ever made while holding it,
 * because an item currency or a slow VaultUnlocked can take up to two seconds. A purchase or bid
 * marks its listing busy under the lock, pays outside it, then commits or releases the listing
 * under the lock again. Busy listings are left alone by every other action and by the expiry tick.
 * Records are saved before any money or item leaves for good, and a save that fails is retried on
 * the next tick. Inventory work runs on the player's world thread through {@link Items#onWorld},
 * and takes the market lock there for the short check and update. Prices of sales and orders are
 * per item. Auction prices are for the whole lot.
 */
public final class MarketServiceImpl implements MarketService {
    static final int HISTORY_LIMIT = 2000;
    static final int STOREFRONT_LIMIT = 12;
    static final int MAX_ORDER_AMOUNT = 100_000;
    static final double MAX_PRICE = 1e9;
    static final Set<Integer> AUCTION_HOURS = Set.of(1, 6, 12, 24, 48);

    record Trade(String itemId, double price, int quantity, long time) {}

    private final DominionContext ctx;
    private final Object lock = new Object();
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Deque<Trade> history = new ArrayDeque<>();
    private final Path listingsFile;
    private final Path historyFile;
    private final Mailboxes mail;
    private final MissionCredit missions = new MissionCredit();
    /** Listings with a payment in flight. Guarded by the lock. */
    private final java.util.Set<UUID> busy = new java.util.HashSet<>();
    /** True while the listings file holds an older state than memory. Guarded by the lock. */
    private boolean listingsDirty;
    static final String BUSY = "Someone is buying that right now. Try again in a moment.";

    public MarketServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        Path root = ctx.repo().root().resolve("market");
        this.listingsFile = root.resolve("listings.json");
        this.historyFile = root.resolve("history.json");
        this.mail = new Mailboxes(ctx.files(), root.resolve("mailbox"));
    }

    // ---- lifecycle ------------------------------------------------------------

    void load() {
        synchronized (lock) {
            Document d = ctx.files().read(listingsFile);
            if (d != null && d.get("Listings") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Document ld) {
                        try {
                            Listing l = Listing.fromDocument(ld);
                            listings.put(l.id(), l);
                        } catch (RuntimeException e) {
                            ctx.logger().at(Level.WARNING).withCause(e).log("Skipping unreadable market listing");
                        }
                    }
                }
            }
            Document h = ctx.files().read(historyFile);
            if (h != null && h.get("Trades") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Document td) {
                        try {
                            history.addLast(new Trade(td.getString("ItemId"), td.get("Price", Number.class).doubleValue(),
                                    td.get("Quantity", Number.class).intValue(), td.get("Time", Number.class).longValue()));
                        } catch (RuntimeException ignored) {
                            // skip a damaged entry
                        }
                    }
                }
                while (history.size() > HISTORY_LIMIT) {
                    history.removeFirst();
                }
            }
        }
        ctx.logger().at(Level.INFO).log("Dominion market loaded %d listings", listings.size());
    }

    void saveAll() {
        synchronized (lock) {
            saveListings();
            saveHistory();
        }
    }

    void onReady(PlayerRef player) {
        int items;
        double coins;
        List<String> notes;
        synchronized (lock) {
            Mailboxes.Box b = mail.box(player.getUuid());
            items = b.items.size();
            coins = b.coins;
            notes = new ArrayList<>(b.notes);
            if (!notes.isEmpty()) {
                b.notes.clear();
                mail.save(player.getUuid(), b);
            }
        }
        for (String note : notes) {
            Msg.send(player, Msg.warn(note));
        }
        if (items > 0 || coins > 0) {
            Msg.send(player, Msg.info("You have " + (items > 0 ? items + " item stacks" : "")
                    + (items > 0 && coins > 0 ? " and " : "") + (coins > 0 ? fmt(coins) : "")
                    + " waiting in your market mailbox."));
        }
    }

    // ---- queries ----------------------------------------------------------------

    @Override
    public boolean enabled() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return ctx.settings().bool("market.enabled") && eco != null && eco.enabled();
    }

    /**
     * Open listings of a kind, or every kind when kind is null. Search matches the item id or the
     * seller name. Sort is price, price_desc, age (newest first), oldest or name. Pages start at 1.
     */
    @Override
    public List<Listing> browse(Listing.Kind kind, String search, String sort, int page, int pageSize) {
        List<Listing> all = filter(kind, search);
        all.sort(comparator(sort));
        int size = Math.max(1, pageSize);
        int from = (Math.max(1, page) - 1) * size;
        if (from >= all.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(all.subList(from, Math.min(all.size(), from + size)));
    }

    @Override
    public int count(Listing.Kind kind, String search) {
        return filter(kind, search).size();
    }

    private List<Listing> filter(Listing.Kind kind, String search) {
        String q = Items.lower(search).trim();
        long now = Time.now();
        List<Listing> out = new ArrayList<>();
        synchronized (lock) {
            for (Listing l : listings.values()) {
                if (l.closed() || l.expiresAt() <= now || (kind != null && l.kind() != kind)) {
                    continue;
                }
                if (!q.isEmpty() && !Items.lower(l.itemId()).contains(q) && !Items.lower(l.sellerName()).contains(q)
                        && !Items.lower(Items.name(l.itemId())).contains(q)
                        && !Items.lower(Items.displayName(l.itemId())).contains(q)) {
                    continue;
                }
                out.add(l);
            }
        }
        return out;
    }

    private static Comparator<Listing> comparator(String sort) {
        Comparator<Listing> newest = Comparator.comparingLong(Listing::createdAt).reversed();
        return switch (Items.lower(sort)) {
            case "price", "cheap", "cheapest", "price_asc" -> Comparator.comparingDouble(Listing::price).thenComparing(newest);
            case "price_desc", "-price", "expensive" -> Comparator.comparingDouble(Listing::price).reversed().thenComparing(newest);
            case "name", "item" -> Comparator.comparing((Listing l) -> Items.lower(Items.displayName(l.itemId()))).thenComparing(newest);
            case "oldest" -> Comparator.comparingLong(Listing::createdAt);
            case "ending", "expiry" -> Comparator.comparingLong(Listing::expiresAt);
            default -> newest;
        };
    }

    @Override
    public List<Listing> mine(UUID player) {
        List<Listing> out = new ArrayList<>();
        synchronized (lock) {
            for (Listing l : listings.values()) {
                if (!l.closed() && l.seller().equals(player)) {
                    out.add(l);
                }
            }
        }
        out.sort(Comparator.comparingLong(Listing::createdAt).reversed());
        return out;
    }

    @Override
    public List<Listing> storefront(Faction faction) {
        List<Listing> out = new ArrayList<>();
        if (faction == null) {
            return out;
        }
        long now = Time.now();
        synchronized (lock) {
            for (Listing l : listings.values()) {
                if (!l.closed() && l.storefront() && l.expiresAt() > now && faction.isMember(l.seller())) {
                    out.add(l);
                }
            }
        }
        out.sort(Comparator.comparingLong(Listing::createdAt).reversed());
        return out.size() > STOREFRONT_LIMIT ? new ArrayList<>(out.subList(0, STOREFRONT_LIMIT)) : out;
    }

    @Override
    public Listing byId(UUID id) {
        if (id == null) {
            return null;
        }
        synchronized (lock) {
            return listings.get(id);
        }
    }

    // ---- selling ------------------------------------------------------------------

    @Override
    public Result sellHeld(UUID player, double price, int amount) {
        Result pre = preSell(player, price);
        if (pre != null) {
            return pre;
        }
        double each = round(price);
        PlayerRef p = Players.online(player);
        if (p == null) {
            return Result.fail("You need to be online to sell an item.");
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            Items.Held held = Items.held(store, ref);
            if (held == null) {
                return Result.fail("Hold the item you want to sell in your hand.");
            }
            synchronized (lock) {
                Result limit = limitCheck(player);
                if (limit != null) {
                    return limit;
                }
                int have = held.stack().getQuantity();
                int qty = amount <= 0 ? have : Math.min(amount, have);
                // The listing is written to disk before the item leaves the hand, so a failed write loses nothing.
                Listing l = newSale(player, held.stack().withQuantity(qty), each);
                listings.put(l.id(), l);
                if (!saveListings()) {
                    listings.remove(l.id());
                    return Result.fail("The listing could not be saved, so your item stays in your hand.");
                }
                ItemStack taken = Items.takeHeld(held, qty);
                if (taken == null) {
                    close(l);
                    persist();
                    return Result.fail("That item could not be taken from your hand.");
                }
                return listed(qty, l.itemId(), each);
            }
        });
        return await(p, f, "Listing your item.");
    }

    @Override
    public Result sellStack(UUID player, ItemStack stack, double price) {
        if (ItemStack.isEmpty(stack)) {
            return Result.fail("There is nothing to sell.");
        }
        Result pre = preSell(player, price);
        if (pre != null) {
            return pre;
        }
        synchronized (lock) {
            Result limit = limitCheck(player);
            if (limit != null) {
                return limit;
            }
            return createSale(player, stack, round(price));
        }
    }

    private Result preSell(UUID player, double price) {
        if (!enabled()) {
            return Result.fail("The market is disabled.");
        }
        String bad = priceProblem(price);
        if (bad != null) {
            return Result.fail(bad);
        }
        synchronized (lock) {
            return limitCheck(player);
        }
    }

    /** Caller holds the lock. */
    private Result limitCheck(UUID player) {
        int max = ctx.settings().integer("market.maxListingsPerPlayer");
        int open = 0;
        for (Listing l : listings.values()) {
            if (!l.closed() && l.seller().equals(player)) {
                open++;
            }
        }
        return open >= max ? Result.fail("You already have " + open + " open listings. The limit is " + max + ".") : null;
    }

    /** Caller holds the lock. */
    private Result createSale(UUID seller, ItemStack stack, double each) {
        Listing l = newSale(seller, stack, each);
        listings.put(l.id(), l);
        if (!saveListings()) {
            listings.remove(l.id());
            return Result.fail("The listing could not be saved. Try again in a moment.");
        }
        return listed(stack.getQuantity(), stack.getItemId(), each);
    }

    private Listing newSale(UUID seller, ItemStack stack, double each) {
        long now = Time.now();
        long expires = now + ctx.settings().longValue("market.listingHours") * 3_600_000L;
        return new Listing(UUID.randomUUID(), Listing.Kind.SALE, seller, name(seller), factionId(seller),
                stack.getItemId(), stack.getQuantity(), Items.toDocument(stack), each, 0, now, expires);
    }

    private Result listed(int quantity, String itemId, double each) {
        return Result.pass("Listed " + quantity + " " + Items.displayName(itemId) + " for " + fmt(each) + " each.");
    }

    // ---- buying -------------------------------------------------------------------

    @Override
    public Result buy(UUID buyer, UUID listingId, int amount) {
        if (!enabled()) {
            return Result.fail("The market is disabled.");
        }
        Listing l;
        int qty;
        double total;
        synchronized (lock) {
            l = listings.get(listingId);
            if (l == null || l.closed() || l.expiresAt() <= Time.now()) {
                return Result.fail("That listing is no longer available.");
            }
            if (l.kind() != Listing.Kind.SALE) {
                return Result.fail(l.kind() == Listing.Kind.AUCTION ? "Auctions take bids, not purchases."
                        : "Fill buy orders by selling to them.");
            }
            if (l.seller().equals(buyer)) {
                return Result.fail("You cannot buy your own listing.");
            }
            if (busy.contains(l.id())) {
                return Result.fail(BUSY);
            }
            qty = amount <= 0 ? l.quantity() : amount;
            if (qty > l.quantity()) {
                return Result.fail("Only " + l.quantity() + " are left on that listing.");
            }
            total = round(l.price() * qty);
            busy.add(l.id());
        }
        Result paid = withdraw(buyer, total, "Market purchase");
        synchronized (lock) {
            busy.remove(l.id());
            if (paid.success()) {
                shrink(l, qty);
                record(l.itemId(), l.price(), qty);
                persist();
            }
        }
        if (paid.failed()) {
            return Result.fail(paid.message());
        }
        double net = payout(l.seller(), total, "Market sale of " + qty + " " + Items.displayName(l.itemId()));
        deliverLot(buyer, l, qty);
        traded(l, qty, total, buyer, l.seller());
        notify(l.seller(), Msg.good(name(buyer) + " bought " + qty + " " + Items.displayName(l.itemId()) + " from you. You received " + fmt(net) + "."));
        return Result.pass("You bought " + qty + " " + Items.displayName(l.itemId()) + " for " + fmt(total) + ".");
    }

    @Override
    public Result cancel(UUID player, UUID listingId) {
        Listing l;
        synchronized (lock) {
            l = listings.get(listingId);
            if (l == null || l.closed()) {
                return Result.fail("That listing is no longer available.");
            }
            if (!l.seller().equals(player)) {
                return Result.fail("That listing is not yours.");
            }
            if (busy.contains(l.id())) {
                return Result.fail(BUSY);
            }
            if (l.kind() == Listing.Kind.AUCTION && l.topBidder() != null) {
                return Result.fail("An auction cannot be cancelled once it has bids.");
            }
            listings.remove(l.id());
            // Saved before anything is handed back, so a restart can never return the same lot twice.
            if (!saveListings()) {
                listings.put(l.id(), l);
                return Result.fail("The listing could not be saved, so it was not cancelled. Try again in a moment.");
            }
            l.close();
            if (l.kind() != Listing.Kind.ORDER) {
                mail.addDocuments(player, Items.splitDocuments(l.item(), l.itemId(), l.quantity()));
            }
        }
        if (l.kind() == Listing.Kind.ORDER) {
            double refund = MarketRules.orderEscrow(l);
            refundCoins(player, refund, "Market order cancelled");
            return Result.pass("Your order was cancelled and " + fmt(refund) + " was returned.");
        }
        return Result.pass("Your listing was cancelled. The items are in your market mailbox.");
    }

    // ---- buy orders ---------------------------------------------------------------

    @Override
    public Result placeOrder(UUID player, String itemId, double pricePerItem, int amount) {
        if (!enabled()) {
            return Result.fail("The market is disabled.");
        }
        String bad = priceProblem(pricePerItem);
        if (bad != null) {
            return Result.fail(bad);
        }
        if (amount <= 0 || amount > MAX_ORDER_AMOUNT) {
            return Result.fail("Order between 1 and " + Msg.number((long) MAX_ORDER_AMOUNT) + " items.");
        }
        if (!Items.exists(itemId)) {
            return Result.fail("There is no item called " + itemId + ".");
        }
        String id = Items.item(itemId).getId();
        double each = round(pricePerItem);
        double total = round(each * amount);
        synchronized (lock) {
            Result limit = limitCheck(player);
            if (limit != null) {
                return limit;
            }
        }
        Result paid = withdraw(player, total, "Market buy order");
        if (paid.failed()) {
            return Result.fail(paid.message());
        }
        String refused;
        synchronized (lock) {
            Result limit = limitCheck(player);
            refused = limit == null ? null : limit.message();
            if (refused == null) {
                long now = Time.now();
                long expires = now + ctx.settings().longValue("market.listingHours") * 3_600_000L;
                Listing l = new Listing(UUID.randomUUID(), Listing.Kind.ORDER, player, name(player), factionId(player),
                        id, amount, null, each, 0, now, expires);
                listings.put(l.id(), l);
                if (!saveListings()) {
                    listings.remove(l.id());
                    refused = "The order could not be saved, so your money was returned.";
                }
            }
        }
        if (refused != null) {
            refundCoins(player, total, "Market buy order refund");
            return Result.fail(refused);
        }
        return Result.pass("Placed an order for " + amount + " " + Items.displayName(id) + " at " + fmt(each) + " each. "
                + fmt(total) + " is held until it fills.");
    }

    @Override
    public Result fulfilOrder(UUID seller, UUID orderId, int amount) {
        if (!enabled()) {
            return Result.fail("The market is disabled.");
        }
        Listing pre = byId(orderId);
        if (pre == null || pre.closed() || pre.kind() != Listing.Kind.ORDER) {
            return Result.fail("That order is no longer open.");
        }
        if (pre.seller().equals(seller)) {
            return Result.fail("You cannot fill your own order.");
        }
        PlayerRef p = Players.online(seller);
        if (p == null) {
            return Result.fail("You need to be online to fill an order.");
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            Listing l;
            List<ItemStack> taken;
            int got;
            double total;
            double net;
            synchronized (lock) {
                l = listings.get(orderId);
                if (l == null || l.closed() || l.expiresAt() <= Time.now()) {
                    return Result.fail("That order is no longer open.");
                }
                if (busy.contains(l.id())) {
                    return Result.fail(BUSY);
                }
                if (amount > l.quantity()) {
                    return Result.fail("That order only needs " + l.quantity() + " more.");
                }
                ItemContainer inv = Items.inventory(store, ref);
                int have = inv == null ? 0 : Items.count(inv, l.itemId());
                if (have <= 0) {
                    return Result.fail("You have no " + Items.displayName(l.itemId()) + " to sell.");
                }
                int want = amount <= 0 ? Math.min(l.quantity(), have) : amount;
                if (have < want) {
                    return Result.fail("You only carry " + have + " " + Items.displayName(l.itemId()) + ".");
                }
                taken = Items.take(inv, l.itemId(), want);
                got = taken.stream().mapToInt(ItemStack::getQuantity).sum();
                if (got <= 0) {
                    return Result.fail("Those items could not be taken from your inventory.");
                }
                shrink(l, got);
                total = round(l.price() * got);
                record(l.itemId(), l.price(), got);
                persist();
            }
            // The money was held when the order was placed, so the seller is paid from it outside the lock.
            net = payout(seller, total, "Filled a market order for " + got + " " + Items.displayName(l.itemId()));
            deliver(l.seller(), taken);
            traded(l, got, total, l.seller(), seller);
            notify(l.seller(), Msg.good(name(seller) + " filled " + got + " " + Items.displayName(l.itemId()) + " of your order."));
            return Result.pass("You sold " + got + " " + Items.displayName(l.itemId()) + " and received " + fmt(net) + ".");
        });
        return await(p, f, "Filling the order.");
    }

    // ---- auctions -----------------------------------------------------------------

    @Override
    public Result auctionHeld(UUID player, double startingBid, double buyout, int hours) {
        if (!enabled()) {
            return Result.fail("The market is disabled.");
        }
        if (!AUCTION_HOURS.contains(hours)) {
            return Result.fail("Auctions run for 1, 6, 12, 24 or 48 hours.");
        }
        String bad = priceProblem(startingBid);
        if (bad == null && buyout > 0) {
            bad = priceProblem(buyout);
        }
        if (bad != null) {
            return Result.fail(bad);
        }
        double start = round(startingBid);
        double out = buyout > 0 ? round(buyout) : 0;
        if (out > 0 && (out <= start || out > MAX_PRICE)) {
            return Result.fail("The buyout must be higher than the starting bid.");
        }
        PlayerRef p = Players.online(player);
        if (p == null) {
            return Result.fail("You need to be online to start an auction.");
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            Items.Held held = Items.held(store, ref);
            if (held == null) {
                return Result.fail("Hold the item you want to auction in your hand.");
            }
            synchronized (lock) {
                Result limit = limitCheck(player);
                if (limit != null) {
                    return limit;
                }
                ItemStack lot = held.stack();
                long now = Time.now();
                Listing l = new Listing(UUID.randomUUID(), Listing.Kind.AUCTION, player, name(player), factionId(player),
                        lot.getItemId(), lot.getQuantity(), Items.toDocument(lot), start, out, now, now + hours * 3_600_000L);
                // Written to disk before the item leaves the hand, so a failed write loses nothing.
                listings.put(l.id(), l);
                if (!saveListings()) {
                    listings.remove(l.id());
                    return Result.fail("The auction could not be saved, so your item stays in your hand.");
                }
                ItemStack taken = Items.takeHeld(held, 0);
                if (taken == null) {
                    listings.remove(l.id());
                    l.close();
                    persist();
                    return Result.fail("That item could not be taken from your hand.");
                }
                return Result.pass("Your auction for " + taken.getQuantity() + " " + Items.displayName(taken.getItemId())
                        + " runs for " + hours + (hours == 1 ? " hour" : " hours") + ". Bids start at " + fmt(start) + ".");
            }
        });
        return await(p, f, "Starting your auction.");
    }

    @Override
    public Result bid(UUID bidder, UUID auctionId, double amount) {
        if (!enabled()) {
            return Result.fail("The market is disabled.");
        }
        String bad = priceProblem(amount);
        if (bad != null) {
            return Result.fail(bad);
        }
        Listing l;
        MarketRules.Bid b;
        synchronized (lock) {
            l = listings.get(auctionId);
            b = MarketRules.decideBid(l, bidder, amount, Time.now(), ctx.settings().decimal("market.auctionMinStepPercent"),
                    wholeOnly(), ctx.settings().longValue("market.auctionAntiSnipeMinutes") * 60_000L, this::fmt);
            if (!b.ok()) {
                return Result.fail(b.error());
            }
            if (busy.contains(l.id())) {
                return Result.fail("Someone else is bidding right now. Try again in a moment.");
            }
            busy.add(l.id());
        }
        Result paid = b.charge() > 0 ? withdraw(bidder, b.charge(), "Market auction bid") : Result.pass();
        synchronized (lock) {
            busy.remove(l.id());
            if (paid.success()) {
                l.setTopBid(bidder, name(bidder), b.offer());
                if (b.boughtOut()) {
                    listings.remove(l.id());
                    l.close();
                    record(l.itemId(), round(b.offer() / Math.max(1, l.quantity())), l.quantity());
                } else if (b.extended()) {
                    l.setExpiresAt(b.expiresAt());
                }
                // Saved before the outbid refund and the seller's payment, so a restart never pays them twice.
                persist();
            }
        }
        if (paid.failed()) {
            return Result.fail(paid.message());
        }
        if (b.refundTo() != null) {
            refundCoins(b.refundTo(), b.refund(), "Market outbid refund");
        }
        double net = b.boughtOut() ? payout(l.seller(), b.offer(), "Auction of " + l.quantity() + " " + Items.displayName(l.itemId())) : 0;
        UUID previous = b.refundTo();
        double previousBid = b.refund();
        double offer = b.offer();
        boolean boughtOut = b.boughtOut();
        boolean extended = b.extended();
        String what = l.quantity() + " " + Items.displayName(l.itemId());
        if (previous != null) {
            notify(previous, Msg.warn("You were outbid on " + what + ". Your bid of " + fmt(previousBid) + " was returned."));
        }
        if (boughtOut) {
            deliverLot(bidder, l, l.quantity());
            traded(l, l.quantity(), offer, bidder, l.seller());
            notify(l.seller(), Msg.good(name(bidder) + " bought out your auction of " + what + ". You received " + fmt(net) + "."));
            return Result.pass("You bought out the auction of " + what + " for " + fmt(offer) + ".");
        }
        notify(l.seller(), Msg.info(name(bidder) + " bid " + fmt(offer) + " on your auction of " + what + "."));
        String extra = extended ? " The auction was extended by " + ctx.settings().integer("market.auctionAntiSnipeMinutes") + " minutes." : "";
        return Result.pass("Your bid of " + fmt(offer) + " on " + what + " is the highest." + extra);
    }

    // ---- storefronts --------------------------------------------------------------

    @Override
    public Result setStorefront(UUID actor, UUID listingId, boolean pinned) {
        FactionService factions = factions();
        Faction f = factions.factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!factions.can(actor, FactionAction.SETTINGS)) {
            return Result.fail("Your rank cannot change the storefront.");
        }
        Listing l;
        synchronized (lock) {
            l = listings.get(listingId);
            if (l == null || l.closed()) {
                return Result.fail("That listing is no longer available.");
            }
            if (l.kind() == Listing.Kind.ORDER) {
                return Result.fail("Buy orders cannot be pinned to a storefront.");
            }
            if (!f.isMember(l.seller())) {
                return Result.fail("Only listings from your own members can go on the storefront.");
            }
            if (l.storefront() == pinned) {
                return Result.pass(pinned ? "That listing is already on the storefront." : "That listing is not on the storefront.");
            }
            if (pinned && storefront(f).size() >= STOREFRONT_LIMIT) {
                return Result.fail("Your storefront already holds " + STOREFRONT_LIMIT + " listings.");
            }
            l.setStorefront(pinned);
            persist();
        }
        String what = l.quantity() + " " + Items.displayName(l.itemId());
        factions.audit(f, actor, name(actor), "market", (pinned ? "Pinned " : "Unpinned ") + what + " by " + l.sellerName()
                + (pinned ? " to the storefront" : " from the storefront"));
        ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
        return Result.pass(pinned ? "Pinned " + what + " to the storefront." : "Removed " + what + " from the storefront.");
    }

    // ---- mailbox ------------------------------------------------------------------

    @Override
    public List<ItemStack> mailbox(UUID player) {
        List<ItemStack> out = new ArrayList<>();
        synchronized (lock) {
            for (Document d : mail.box(player).items) {
                ItemStack s = Items.fromDocument(d);
                if (s != null && !ItemStack.isEmpty(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    @Override
    public double mailboxCoins(UUID player) {
        synchronized (lock) {
            return mail.box(player).coins;
        }
    }

    /** Puts items and coins into a player's mailbox. Used by the vault when a faction disbands. */
    @Override
    public void mail(UUID player, List<ItemStack> stacks, double coins) {
        synchronized (lock) {
            if (stacks != null && !stacks.isEmpty()) {
                mail.addItems(player, stacks);
            }
            if (coins > 0) {
                mail.addCoins(player, round(coins));
            }
        }
    }

    /** Leaves a note that the player sees once at the next login. */
    @Override
    public void mailNote(UUID player, String text) {
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        synchronized (lock) {
            mail.addNote(player, text.strip());
        }
    }

    @Override
    public Result collect(UUID player) {
        PlayerRef p = Players.online(player);
        if (p == null) {
            return Result.fail("You need to be online to collect your mailbox.");
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            int moved = 0;
            boolean full;
            double coins;
            synchronized (lock) {
                Mailboxes.Box b = mail.box(player);
                if (b.items.isEmpty() && b.coins <= 0) {
                    return Result.fail("Your market mailbox is empty.");
                }
                List<Document> kept = new ArrayList<>();
                for (Document d : b.items) {
                    ItemStack s = Items.fromDocument(d);
                    if (s == null || ItemStack.isEmpty(s)) {
                        kept.add(d);
                        continue;
                    }
                    List<ItemStack> left = Items.give(store, ref, List.of(s));
                    int leftQty = left.stream().mapToInt(ItemStack::getQuantity).sum();
                    moved += s.getQuantity() - leftQty;
                    for (ItemStack rest : left) {
                        kept.add(Items.toDocument(rest));
                    }
                }
                full = !kept.isEmpty();
                b.items.clear();
                b.items.addAll(kept);
                // The coins leave the box only once the emptied box is on disk, so a restart can never pay them twice.
                coins = b.coins;
                b.coins = 0;
                if (!mail.save(player, b)) {
                    b.coins = coins;
                    coins = 0;
                }
            }
            boolean paid = false;
            if (coins > 0) {
                Result r = economy().enabled() ? deposit(player, coins, "Market mailbox") : Result.fail("");
                paid = r.success();
                if (!paid) {
                    synchronized (lock) {
                        mail.addCoins(player, coins);
                    }
                }
            }
            StringBuilder sb = new StringBuilder("Collected ").append(moved).append(moved == 1 ? " item" : " items");
            if (paid) {
                sb.append(" and ").append(fmt(coins));
            }
            sb.append('.');
            if (full) {
                sb.append(" Some items did not fit and are still in your mailbox.");
            }
            if (moved > 0 || paid) {
                return Result.pass(sb.toString());
            }
            return Result.fail(full ? "Your inventory is full. Make some room and try again."
                    : "Your coins cannot be collected right now. They stay in your mailbox.");
        });
        return await(p, f, "Collecting your mailbox.");
    }

    // ---- history ------------------------------------------------------------------

    @Override
    public List<double[]> priceHistory(String itemId, int limit) {
        List<double[]> out = new ArrayList<>();
        if (itemId == null) {
            return out;
        }
        synchronized (lock) {
            for (Trade t : history) {
                if (t.itemId().equalsIgnoreCase(itemId)) {
                    out.add(new double[] { t.time(), t.price() });
                }
            }
        }
        int n = Math.max(0, limit);
        return out.size() > n ? new ArrayList<>(out.subList(out.size() - n, out.size())) : out;
    }

    // ---- tick ---------------------------------------------------------------------

    @Override
    public void tick() {
        long now = Time.now();
        List<Runnable> after = new ArrayList<>();
        synchronized (lock) {
            if (listingsDirty) {
                saveListings();
            }
            mail.retry();
            List<Listing> due = new ArrayList<>();
            for (Listing l : listings.values()) {
                if (!l.closed() && l.expiresAt() <= now && !busy.contains(l.id())) {
                    due.add(l);
                }
            }
            if (due.isEmpty()) {
                return;
            }
            // The closed listings are saved before any item or coin is handed out, so a restart never pays an auction twice.
            for (Listing l : due) {
                listings.remove(l.id());
            }
            if (!saveListings()) {
                for (Listing l : due) {
                    listings.put(l.id(), l);
                }
                ctx.logger().at(Level.WARNING).log("Dominion market could not save before closing %d listings, trying again next minute", due.size());
                return;
            }
            for (Listing l : due) {
                l.close();
                String what = l.quantity() + " " + Items.displayName(l.itemId());
                switch (MarketRules.expiry(l)) {
                    case RETURN_ITEMS -> {
                        mail.addDocuments(l.seller(), Items.splitDocuments(l.item(), l.itemId(), l.quantity()));
                        String text = l.kind() == Listing.Kind.AUCTION
                                ? "Your auction of " + what + " ended with no bids. The items are in your market mailbox."
                                : "Your listing of " + what + " expired. The items are in your market mailbox.";
                        after.add(() -> notify(l.seller(), Msg.info(text)));
                    }
                    case REFUND_ORDER -> {
                        double refund = MarketRules.orderEscrow(l);
                        after.add(() -> {
                            refundCoins(l.seller(), refund, "Market order expired");
                            notify(l.seller(), Msg.info("Your order for " + what + " expired and " + fmt(refund) + " was returned."));
                        });
                    }
                    case AWARD_WINNER -> {
                        UUID winner = l.topBidder();
                        double price = l.price();
                        mail.addDocuments(winner, Items.splitDocuments(l.item(), l.itemId(), l.quantity()));
                        record(l.itemId(), round(price / Math.max(1, l.quantity())), l.quantity());
                        after.add(() -> {
                            double net = payout(l.seller(), price, "Auction of " + what);
                            traded(l, l.quantity(), price, winner, l.seller());
                            notify(winner, Msg.good("You won the auction of " + what + " for " + fmt(price) + ". Collect it from your market mailbox."));
                            notify(l.seller(), Msg.good("Your auction of " + what + " sold to " + l.topBidderName() + ". You received " + fmt(net) + "."));
                        });
                    }
                }
            }
        }
        for (Runnable r : after) {
            try {
                r.run();
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion market tick follow up failed");
            }
        }
    }

    // ---- internals ----------------------------------------------------------------

    /** Removes quantity from a listing, closing it when nothing is left. Caller holds the lock. */
    private void shrink(Listing l, int qty) {
        int left = l.quantity() - qty;
        if (left <= 0) {
            close(l);
            return;
        }
        Listing copy = new Listing(l.id(), l.kind(), l.seller(), l.sellerName(), l.sellerFaction(), l.itemId(), left,
                l.item(), l.price(), l.buyout(), l.createdAt(), l.expiresAt());
        copy.setStorefront(l.storefront());
        l.close();
        listings.put(copy.id(), copy);
    }

    /** Caller holds the lock. */
    private void close(Listing l) {
        l.close();
        listings.remove(l.id());
    }

    /**
     * Pays a seller the total minus the faction tax and the server tax. The faction tax goes to the
     * seller's faction bank and the server tax is burned. Returns what the seller received.
     */
    private double payout(UUID seller, double total, String reason) {
        Faction f = factions().factionOf(seller);
        // Outposts with a tax boost divide both taxes, so a boost of 2 halves them.
        MarketRules.Split split = MarketRules.split(total, f != null, ctx.settings().decimal("market.taxPercent"),
                ctx.settings().decimal("market.serverTaxPercent"), taxBoost(f));
        double tax = split.tax();
        double net = split.net();
        if (net > 0) {
            Result r = deposit(seller, net, reason);
            if (r.failed()) {
                mailCoins(seller, net);
            }
        }
        if (tax > 0) {
            Result r;
            try {
                r = economy().bankCredit(f, tax, "Market tax from " + name(seller));
            } catch (RuntimeException e) {
                r = Result.fail(String.valueOf(e.getMessage()));
            }
            if (r.failed()) {
                // The coins were already paid by the buyer, so they go to the seller instead of vanishing.
                ctx.logger().at(Level.WARNING).log("Dominion market tax of %s for %s was not credited, %s", tax, f.tag(), r.message());
                mailCoins(seller, tax);
                net = round(net + tax);
            }
        }
        return net;
    }

    /** The tax boost of the seller's faction from the events module, never below 1. */
    private double taxBoost(Faction f) {
        EventsService events = f == null ? null : ctx.serviceOrNull(EventsService.class);
        if (events == null) {
            return 1.0;
        }
        try {
            double b = events.boostFor(f, "tax");
            return Double.isFinite(b) && b > 1.0 ? b : 1.0;
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    /** Returns coins to a balance, falling back to the mailbox. Never called with the lock held. */
    private void refundCoins(UUID player, double amount, String reason) {
        if (amount <= 0) {
            return;
        }
        if (deposit(player, amount, reason).failed()) {
            mailCoins(player, amount);
        }
    }

    private void mailCoins(UUID player, double amount) {
        synchronized (lock) {
            mail.addCoins(player, amount);
        }
    }

    /** An economy withdrawal that turns an exception into a refusal. Never called with the lock held. */
    private Result withdraw(UUID player, double amount, String reason) {
        try {
            return economy().withdraw(player, amount, reason);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion market payment failed");
            return Result.fail("The payment could not be made right now.");
        }
    }

    /** An economy deposit that turns an exception into a refusal. Never called with the lock held. */
    private Result deposit(UUID player, double amount, String reason) {
        try {
            return economy().deposit(player, amount, reason);
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion market payout failed");
            return Result.fail("The payment could not be made right now.");
        }
    }

    /** Caller holds the lock. */
    private void record(String itemId, double each, int qty) {
        history.addLast(new Trade(itemId, each, qty, Time.now()));
        while (history.size() > HISTORY_LIMIT) {
            history.removeFirst();
        }
        saveHistory();
    }

    private void traded(Listing l, int qty, double total, UUID buyer, UUID seller) {
        ctx.bus().publish(new DominionEvent.MarketTrade(l.id(), l.itemId(), qty, total, buyer, seller));
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        // Trades between faction mates, repeats with the same partner and cheap trades do not count, so missions cannot be farmed.
        if (progress != null && missions.counts(buyer, factionId(buyer), seller, factionId(seller), total, Time.now())) {
            try {
                // Missions count trades, not items, so one purchase of many items is one trade.
                progress.advance(buyer, "trade", 1);
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion progress update for a trade failed");
            }
        }
        refreshHud(buyer);
        refreshHud(seller);
    }

    private void refreshHud(UUID player) {
        UiService ui = ctx.serviceOrNull(UiService.class);
        PlayerRef p = Players.online(player);
        if (ui != null && p != null) {
            try {
                ui.refreshHud(p);
            } catch (RuntimeException ignored) {
                // the hud refreshes on its own timer anyway
            }
        }
    }

    /**
     * Delivers part of a listing's lot. When the stacks cannot be built right now the stored
     * documents go to the mailbox instead, so a paid lot is never lost.
     */
    private void deliverLot(UUID player, Listing l, int qty) {
        List<ItemStack> stacks = Items.split(l.item(), l.itemId(), qty);
        if (Items.quantity(stacks) == qty) {
            deliver(player, stacks);
            return;
        }
        ctx.logger().at(Level.WARNING).log("Dominion market could not build %d %s, so they went to the mailbox as stored", qty, l.itemId());
        synchronized (lock) {
            mail.addDocuments(player, Items.splitDocuments(l.item(), l.itemId(), qty));
        }
        notify(player, Msg.warn("Your " + Items.displayName(l.itemId()) + " went to your market mailbox."));
    }

    /** Gives items to an online player on their world thread, or sends them to the mailbox. */
    private void deliver(UUID player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        PlayerRef p = Players.online(player);
        if (p == null) {
            mail(player, stacks, 0);
            return;
        }
        Items.onWorld(p, (store, ref) -> Items.give(store, ref, stacks)).whenComplete((left, err) -> {
            if (err != null) {
                ctx.logger().at(Level.WARNING).withCause(err).log("Dominion market delivery failed, using the mailbox");
            }
            List<ItemStack> rest = err != null || left == null ? stacks : left;
            if (!rest.isEmpty()) {
                mail(player, rest, 0);
                Msg.send(p, Msg.warn("Your inventory is full so the items went to your market mailbox."));
            }
        });
    }

    /** Returns the outcome when the world work already ran, otherwise reports it to the player later. */
    private Result await(PlayerRef player, CompletableFuture<Result> f, String pending) {
        if (f.isDone()) {
            return outcome(f);
        }
        f.whenComplete((r, err) -> {
            Result res = outcome(f);
            Msg.send(player, res.success() ? Msg.good(res.message()) : Msg.bad(res.message()));
        });
        return Result.pass(pending);
    }

    private Result outcome(CompletableFuture<Result> f) {
        try {
            Result r = f.join();
            return r == null ? Result.fail("You need to be in a world to do that.") : r;
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion market action failed");
            return Result.fail("Something went wrong and nothing was changed.");
        }
    }

    private void notify(UUID player, com.hypixel.hytale.server.core.Message message) {
        Msg.send(Players.online(player), message);
    }

    /** Writes every listing. Returns false when the file could not be written. Caller holds the lock. */
    private boolean saveListings() {
        List<Document> docs = new ArrayList<>();
        for (Listing l : listings.values()) {
            docs.add(l.toDocument());
        }
        boolean ok = ctx.files().write(listingsFile, new Document("Listings", docs));
        listingsDirty = !ok;
        return ok;
    }

    /** Saves the listings after a change that already happened. A failure is retried on the next tick. Caller holds the lock. */
    private void persist() {
        if (!saveListings()) {
            ctx.logger().at(Level.WARNING).log("Dominion market could not save its listings, trying again next minute");
        }
    }

    private void saveHistory() {
        List<Document> docs = new ArrayList<>(history.size());
        for (Trade t : history) {
            docs.add(new Document("ItemId", t.itemId()).append("Price", t.price()).append("Quantity", t.quantity()).append("Time", t.time()));
        }
        ctx.files().write(historyFile, new Document("Trades", docs));
    }

    private String name(UUID player) {
        PlayerRef p = Players.online(player);
        if (p != null && p.getUsername() != null) {
            return p.getUsername();
        }
        String n = ctx.player(player).name();
        return n == null || n.isBlank() ? "Someone" : n;
    }

    private UUID factionId(UUID player) {
        Faction f = factions().factionOf(player);
        return f == null ? null : f.id();
    }

    private String fmt(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? Msg.number(amount) : eco.format(amount);
    }

    private EconomyService economy() {
        return ctx.service(EconomyService.class);
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    /**
     * An item currency takes whole items, rounding a payment up. A price with cents would take more
     * than the seller receives, so prices must be whole while that provider is active.
     */
    private boolean wholeOnly() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null && "item".equals(eco.providerName());
    }

    private String priceProblem(double price) {
        String bad = invalidPrice(price);
        if (bad == null && wholeOnly() && Math.abs(price - Math.rint(price)) > 1e-6) {
            return "Prices must be whole numbers because the currency is an item.";
        }
        return bad;
    }

    private static String invalidPrice(double price) {
        return MarketRules.invalidPrice(price, MAX_PRICE);
    }

    static double round(double amount) {
        return MarketRules.round(amount);
    }
}
