package dev.ryvrook.dominion.market;

import dev.ryvrook.dominion.model.Listing;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketRulesTest {
    private static final long HOUR = 3_600_000L;

    private static String fmt(double a) {
        return String.format(Locale.ROOT, "%.2f", a);
    }

    private static long cents(double v) {
        return Math.round(v * 100.0);
    }

    private static Listing auction(UUID seller, double start, double buyout, long expires) {
        return new Listing(UUID.randomUUID(), Listing.Kind.AUCTION, seller, "seller", null, "Ore_Iron", 5, null,
                start, buyout, 0, expires);
    }

    @Test
    void pricesMustBePositiveFiniteAndUnderTheCap() {
        assertNull(MarketRules.invalidPrice(0.01, 1e9));
        assertNotNull(MarketRules.invalidPrice(0, 1e9));
        assertNotNull(MarketRules.invalidPrice(-2, 1e9));
        assertNotNull(MarketRules.invalidPrice(Double.NaN, 1e9));
        assertNotNull(MarketRules.invalidPrice(0.004, 1e9));
        assertNotNull(MarketRules.invalidPrice(2e9, 1e9));
    }

    @Test
    void splitPartsAlwaysAddUpToTheTotal() {
        Random rnd = new Random(3);
        for (int i = 0; i < 20_000; i++) {
            double total = rnd.nextInt(10_000_000) / 100.0;
            double tax = rnd.nextInt(5) == 0 ? rnd.nextDouble() * 300 : rnd.nextDouble() * 20;
            double server = rnd.nextInt(5) == 0 ? rnd.nextDouble() * 300 : rnd.nextDouble() * 20;
            double relief = rnd.nextBoolean() ? 1 : 1 + rnd.nextDouble() * 4;
            MarketRules.Split s = MarketRules.split(total, rnd.nextBoolean(), tax, server, relief);
            assertTrue(s.net() >= 0 && s.tax() >= 0 && s.burn() >= 0);
            assertEquals(cents(total), cents(s.net()) + cents(s.tax()) + cents(s.burn()), "total " + total + " tax " + tax + " server " + server);
        }
    }

    @Test
    void taxAboveAHundredPercentCannotPayOutMoreThanWasPaid() {
        MarketRules.Split s = MarketRules.split(100, true, 150, 50, 1);
        assertEquals(100, s.tax());
        assertEquals(0, s.burn());
        assertEquals(0, s.net());
    }

    @Test
    void reliefDividesBothTaxesAndNoFactionMeansNoFactionTax() {
        MarketRules.Split s = MarketRules.split(100, true, 10, 4, 2);
        assertEquals(5, s.tax());
        assertEquals(2, s.burn());
        assertEquals(93, s.net());
        MarketRules.Split none = MarketRules.split(100, false, 10, 4, 1);
        assertEquals(0, none.tax());
        assertEquals(96, none.net());
        assertEquals(MarketRules.split(100, true, 10, 0, 1), MarketRules.split(100, true, 10, 0, 0.5));
    }

    @Test
    void minimumBidRules() {
        assertEquals(10, MarketRules.minBid(10, false, 5, false));
        assertEquals(10.5, MarketRules.minBid(10, true, 5, false));
        assertEquals(10.01, MarketRules.minBid(10, true, 0, false));
        assertEquals(11, MarketRules.minBid(10, true, 0, true));
        assertEquals(11, MarketRules.minBid(10, true, 5, true));
        assertEquals(10.11, MarketRules.minBid(10.1, true, 0.05, false));
    }

    @Test
    void bidsAreRefusedOnClosedOwnAndWrongListings() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();
        Listing a = auction(seller, 10, 0, HOUR);
        assertEquals("You cannot bid on your own auction.", MarketRules.decideBid(a, seller, 20, 0, 5, false, 0, MarketRulesTest::fmt).error());
        assertEquals("That auction has ended.", MarketRules.decideBid(a, bidder, 20, HOUR, 5, false, 0, MarketRulesTest::fmt).error());
        assertEquals("That auction has ended.", MarketRules.decideBid(null, bidder, 20, 0, 5, false, 0, MarketRulesTest::fmt).error());
        Listing sale = new Listing(UUID.randomUUID(), Listing.Kind.SALE, seller, "s", null, "x", 1, null, 1, 0, 0, HOUR);
        assertEquals("That listing is not an auction.", MarketRules.decideBid(sale, bidder, 20, 0, 5, false, 0, MarketRulesTest::fmt).error());
        a.setTopBid(bidder, "b", 10);
        assertEquals("The lowest bid you can place is 10.50.",
                MarketRules.decideBid(a, UUID.randomUUID(), 10.4, 0, 5, false, 0, MarketRulesTest::fmt).error());
        a.close();
        assertFalse(MarketRules.decideBid(a, UUID.randomUUID(), 50, 0, 5, false, 0, MarketRulesTest::fmt).ok());
    }

    @Test
    void raisingYourOwnBidOnlyChargesTheDifference() {
        UUID bidder = UUID.randomUUID();
        Listing a = auction(UUID.randomUUID(), 10, 0, HOUR);
        a.setTopBid(bidder, "b", 12);
        MarketRules.Bid b = MarketRules.decideBid(a, bidder, 15, 0, 5, false, 0, MarketRulesTest::fmt);
        assertTrue(b.ok());
        assertEquals(3, b.charge());
        assertNull(b.refundTo());
    }

    @Test
    void anOfferAboveTheBuyoutPaysOnlyTheBuyout() {
        UUID previous = UUID.randomUUID();
        Listing a = auction(UUID.randomUUID(), 10, 50, HOUR);
        a.setTopBid(previous, "p", 20);
        MarketRules.Bid b = MarketRules.decideBid(a, UUID.randomUUID(), 80, 0, 5, false, 0, MarketRulesTest::fmt);
        assertTrue(b.boughtOut());
        assertEquals(50, b.offer());
        assertEquals(50, b.charge());
        assertEquals(previous, b.refundTo());
        assertEquals(20, b.refund());
        assertFalse(b.extended());
    }

    @Test
    void lateBidsExtendTheAuction() {
        Listing a = auction(UUID.randomUUID(), 10, 0, HOUR);
        long window = 5 * 60_000L;
        MarketRules.Bid early = MarketRules.decideBid(a, UUID.randomUUID(), 10, 0, 5, false, window, MarketRulesTest::fmt);
        assertFalse(early.extended());
        assertEquals(HOUR, early.expiresAt());
        MarketRules.Bid late = MarketRules.decideBid(a, UUID.randomUUID(), 10, HOUR - 60_000L, 5, false, window, MarketRulesTest::fmt);
        assertTrue(late.extended());
        assertEquals(HOUR + window, late.expiresAt());
    }

    @Test
    void expiryHandsItemsBackRefundsOrdersAndAwardsWinners() {
        UUID seller = UUID.randomUUID();
        Listing a = auction(seller, 10, 0, HOUR);
        assertEquals(MarketRules.Expiry.RETURN_ITEMS, MarketRules.expiry(a));
        a.setTopBid(UUID.randomUUID(), "w", 12);
        assertEquals(MarketRules.Expiry.AWARD_WINNER, MarketRules.expiry(a));
        Listing order = new Listing(UUID.randomUUID(), Listing.Kind.ORDER, seller, "s", null, "x", 7, null, 1.25, 0, 0, HOUR);
        assertEquals(MarketRules.Expiry.REFUND_ORDER, MarketRules.expiry(order));
        assertEquals(8.75, MarketRules.orderEscrow(order));
    }

    /**
     * Simulates many auctions with random bidders the way the service applies each decision. Money
     * held by bidders plus the escrowed top bid never changes until the auction closes, and at the
     * close the winner's last bid is exactly what the seller side receives.
     */
    @Test
    void randomAuctionsConserveMoney() {
        Random rnd = new Random(11);
        for (int round = 0; round < 300; round++) {
            UUID seller = UUID.randomUUID();
            List<UUID> bidders = new ArrayList<>();
            Map<UUID, Long> wallets = new HashMap<>();
            for (int i = 0; i < 4; i++) {
                UUID u = UUID.randomUUID();
                bidders.add(u);
                wallets.put(u, (long) rnd.nextInt(100_000));
            }
            long start = wallets.values().stream().mapToLong(Long::longValue).sum();
            double buyout = rnd.nextBoolean() ? 0 : 50 + rnd.nextInt(500);
            Listing a = auction(seller, 1 + rnd.nextInt(20), buyout, HOUR);
            long escrow = 0;
            long sellerSide = 0;
            for (int step = 0; step < 40 && !a.closed(); step++) {
                UUID who = bidders.get(rnd.nextInt(bidders.size()));
                double offer = a.price() + rnd.nextInt(3000) / 100.0;
                MarketRules.Bid b = MarketRules.decideBid(a, who, offer, step, 5, rnd.nextBoolean(), 0, MarketRulesTest::fmt);
                if (!b.ok()) {
                    continue;
                }
                long charge = cents(b.charge());
                if (wallets.get(who) < charge) {
                    continue;
                }
                wallets.merge(who, -charge, Long::sum);
                escrow += charge;
                if (b.refundTo() != null) {
                    wallets.merge(b.refundTo(), cents(b.refund()), Long::sum);
                    escrow -= cents(b.refund());
                }
                a.setTopBid(who, "b", b.offer());
                assertEquals(cents(a.price()), escrow, "escrow must equal the top bid");
                if (b.boughtOut()) {
                    a.close();
                }
            }
            if (a.topBidder() != null) {
                MarketRules.Split s = MarketRules.split(a.price(), true, 5, 2, 1);
                sellerSide = cents(s.net()) + cents(s.tax()) + cents(s.burn());
                assertEquals(escrow, sellerSide);
            }
            long now = wallets.values().stream().mapToLong(Long::longValue).sum();
            assertEquals(start, now + escrow);
        }
    }

    /** Partial fills of a buy order pay out exactly the money that was held for it. */
    @Test
    void orderFillsAndRefundsSpendExactlyTheEscrow() {
        Random rnd = new Random(5);
        for (int round = 0; round < 2000; round++) {
            double each = (1 + rnd.nextInt(100_000)) / 100.0;
            int amount = 1 + rnd.nextInt(5000);
            long held = cents(MarketRules.round(each * amount));
            int left = amount;
            long paid = 0;
            while (left > 0 && rnd.nextInt(4) != 0) {
                int got = 1 + rnd.nextInt(left);
                paid += cents(MarketRules.round(each * got));
                left -= got;
            }
            paid += cents(MarketRules.round(each * left));
            assertEquals(held, paid);
        }
    }
}
