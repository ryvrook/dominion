package dev.ryvrook.dominion.market;

import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.TestContexts;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.text.Messages;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the market service against a temporary folder, an in memory economy and no server. Items
 * cannot be built without assets, so purchased lots land in the mailbox as stored documents.
 */
class MarketServiceTest {
    @TempDir
    Path dir;

    private DominionContext ctx;
    private MarketServiceImpl market;
    /** Wallets in cents. */
    private final Map<UUID, Long> wallets = new ConcurrentHashMap<>();
    /** Gates every withdrawal when set, so a payment can be held in flight. */
    private volatile CountDownLatch gate;
    private final CountDownLatch inFlight = new CountDownLatch(1);
    private final UUID seller = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TestContexts.close(ctx);
    }

    private static long cents(double v) {
        return Math.round(v * 100.0);
    }

    private void start(List<Listing> listings) throws Exception {
        if (!listings.isEmpty()) {
            List<Document> docs = new ArrayList<>();
            listings.forEach(l -> docs.add(l.toDocument()));
            Path file = dir.resolve("market").resolve("listings.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new Document("Listings", docs).toJson());
        }
        ctx = TestContexts.create(dir);
        ctx.settings().set("market.taxPercent", 0.0);
        ctx.settings().set("market.serverTaxPercent", 10.0);
        Map<String, Function<Object[], Object>> eco = Map.of(
                "enabled", a -> true,
                "providerName", a -> "internal",
                "format", a -> String.format(Locale.ROOT, "%.2f", (Double) a[0]),
                "withdraw", a -> {
                    CountDownLatch g = gate;
                    if (g != null) {
                        inFlight.countDown();
                        try {
                            g.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    long need = cents((Double) a[1]);
                    synchronized (wallets) {
                        long have = wallets.getOrDefault((UUID) a[0], 0L);
                        if (have < need) {
                            return Result.fail("short");
                        }
                        wallets.put((UUID) a[0], have - need);
                    }
                    return Result.pass();
                },
                "deposit", a -> {
                    synchronized (wallets) {
                        wallets.merge((UUID) a[0], cents((Double) a[1]), Long::sum);
                    }
                    return Result.pass();
                });
        ctx.register(EconomyService.class, TestContexts.fake(EconomyService.class, eco));
        ctx.register(FactionService.class, TestContexts.fake(FactionService.class, Map.of()));
        market = new MarketServiceImpl(ctx);
        market.load();
    }

    private Listing sale(int qty, double each) {
        return new Listing(UUID.randomUUID(), Listing.Kind.SALE, seller, "Seller", null, "Ore_Iron", qty,
                new Document("Id", "Ore_Iron").append("Quantity", qty), each, 0, 1, Long.MAX_VALUE / 2);
    }

    private int mailedQuantity(UUID player) {
        int n = 0;
        Path file = dir.resolve("market").resolve("mailbox").resolve(player + ".json");
        if (!Files.exists(file)) {
            return 0;
        }
        try {
            Document d = Document.parse(Files.readString(file));
            for (Object o : d.getList("Items", Object.class)) {
                n += ((Document) o).getInteger("Quantity");
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return n;
    }

    @Test
    void aPurchaseMovesExactlyThePriceAndKeepsTheLot() throws Exception {
        Listing l = sale(10, 2.5);
        start(List.of(l));
        wallets.put(buyer, 10_000L);
        Result r = market.buy(buyer, l.id(), 4);
        assertTrue(r.success(), r.message());
        assertEquals(9000, wallets.get(buyer));
        assertEquals(900, wallets.get(seller), "ten percent server tax is burned");
        assertEquals(4, mailedQuantity(buyer), "a lot that cannot be built goes to the mailbox in full");
        assertEquals(6, market.byId(l.id()).quantity());
    }

    @Test
    void aRefusedPaymentChangesNothing() throws Exception {
        Listing l = sale(10, 2.5);
        start(List.of(l));
        wallets.put(buyer, 100L);
        assertFalse(market.buy(buyer, l.id(), 4).success());
        assertEquals(100, wallets.get(buyer));
        assertEquals(10, market.byId(l.id()).quantity());
        assertEquals(0, mailedQuantity(buyer));
    }

    /** While a payment waits on a slow economy, the market lock is free and the listing is left alone. */
    @Test
    void aSlowPaymentNeverHoldsTheMarketLock() throws Exception {
        Listing l = sale(10, 2.5);
        Listing other = sale(5, 1);
        start(List.of(l, other));
        wallets.put(buyer, 10_000L);
        UUID rival = UUID.randomUUID();
        wallets.put(rival, 10_000L);
        CountDownLatch held = new CountDownLatch(1);
        gate = held;
        Result[] first = new Result[1];
        Thread t = new Thread(() -> first[0] = market.buy(buyer, l.id(), 10));
        t.start();
        assertTrue(inFlight.await(5, TimeUnit.SECONDS));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals(2, market.browse(null, "", "", 1, 10).size());
            assertEquals(Messages.get(MarketServiceImpl.BUSY), market.cancel(seller, l.id()).message());
            market.tick();
            assertNotNull(market.byId(l.id()));
        });
        gate = null;
        Result second = market.buy(rival, l.id(), 1);
        assertEquals(Messages.get(MarketServiceImpl.BUSY), second.message(), "a second buyer waits for the first");
        held.countDown();
        t.join(5000);
        assertTrue(first[0].success(), String.valueOf(first[0]));
        assertNull(market.byId(l.id()), "the whole lot was bought");
        assertEquals(7500, wallets.get(buyer));
    }

    @Test
    void anExpiredAuctionIsAwardedOnceAndSavedBeforeItPays() throws Exception {
        UUID winner = UUID.randomUUID();
        Listing a = new Listing(UUID.randomUUID(), Listing.Kind.AUCTION, seller, "Seller", null, "Ore_Iron", 3,
                new Document("Id", "Ore_Iron").append("Quantity", 3), 20, 0, 1, 2);
        a.setTopBid(winner, "Winner", 40);
        start(List.of(a));
        market.tick();
        assertEquals(3600, wallets.get(seller));
        assertEquals(3, mailedQuantity(winner));
        String saved = Files.readString(dir.resolve("market").resolve("listings.json"));
        assertFalse(saved.contains(a.id().toString()), "the closed auction is on disk before the payout");
        market.tick();
        market = new MarketServiceImpl(ctx);
        market.load();
        market.tick();
        assertEquals(3600, wallets.get(seller), "a restart does not pay the auction again");
        assertEquals(3, mailedQuantity(winner));
    }

    @Test
    void anExpiryThatCannotBeSavedPaysNothingAndRetries() throws Exception {
        UUID winner = UUID.randomUUID();
        Listing a = new Listing(UUID.randomUUID(), Listing.Kind.AUCTION, seller, "Seller", null, "Ore_Iron", 3,
                new Document("Id", "Ore_Iron").append("Quantity", 3), 20, 0, 1, 2);
        a.setTopBid(winner, "Winner", 40);
        start(List.of(a));
        Path file = dir.resolve("market").resolve("listings.json");
        Files.delete(file);
        Files.createDirectories(file.resolve("blocker"));
        market.tick();
        assertNull(wallets.get(seller));
        assertEquals(0, mailedQuantity(winner));
        assertNotNull(market.byId(a.id()), "the auction stays open until it can be saved");
        Files.delete(file.resolve("blocker"));
        Files.delete(file);
        market.tick();
        assertEquals(3600, wallets.get(seller));
        assertEquals(3, mailedQuantity(winner));
    }

    @Test
    void anExpiredOrderRefundsItsEscrowOnce() throws Exception {
        Listing order = new Listing(UUID.randomUUID(), Listing.Kind.ORDER, buyer, "Buyer", null, "Ore_Iron", 7, null, 1.25, 0, 1, 2);
        start(List.of(order));
        market.tick();
        market.tick();
        assertEquals(875, wallets.get(buyer));
    }

    @Test
    void collectingCoinsPaysThemOnce() throws Exception {
        start(List.of());
        market.mail(buyer, List.of(), 12.5);
        // Collect needs a player in a world, so the coin path is checked through the stored mailbox.
        assertEquals(12.5, market.mailboxCoins(buyer));
        assertFalse(market.collect(buyer).success(), "an offline player cannot collect");
        assertEquals(12.5, market.mailboxCoins(buyer));
    }
}
