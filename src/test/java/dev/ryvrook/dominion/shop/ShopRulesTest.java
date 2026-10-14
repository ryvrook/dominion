package dev.ryvrook.dominion.shop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopRulesTest {
    @Test
    void boostsAreCappedAndBadValuesIgnored() {
        assertEquals(1, ShopRules.boost(Double.NaN));
        assertEquals(1, ShopRules.boost(0));
        assertEquals(1, ShopRules.boost(-2));
        assertEquals(1.5, ShopRules.boost(1.5));
        assertEquals(ShopRules.MAX_SELL_BOOST, ShopRules.boost(1000));
        assertEquals(0.5, ShopRules.boost(0.5));
    }

    @Test
    void aBoostNeverLiftsTheSellPriceAboveTheBuyPrice() {
        assertEquals(10, ShopRules.unitSell(8, 2, 10));
        assertEquals(12, ShopRules.unitSell(8, 1.5, 20));
        // Sell only items are capped by the boost limit instead.
        assertEquals(80, ShopRules.unitSell(8, 1e6, 0));
    }

    /** Buying any amount and selling it straight back never makes money, whatever the boost and rounding. */
    @Test
    void buyingAndSellingBackNeverProfits() {
        Random rnd = new Random(2);
        for (int i = 0; i < 50_000; i++) {
            double buy = (1 + rnd.nextInt(100_000)) / 100.0;
            double sell = Math.min(buy, (1 + rnd.nextInt(100_000)) / 100.0);
            double boost = rnd.nextDouble() * 40;
            int qty = 1 + rnd.nextInt(6400);
            boolean whole = rnd.nextBoolean();
            if (whole) {
                buy = Math.max(1, Math.rint(buy));
                sell = Math.min(buy, Math.rint(sell));
            }
            double paid = ShopRules.buyTotal(buy, qty);
            double got = ShopRules.saleTotal(ShopRules.unitSell(sell, boost, buy), qty, whole);
            assertTrue(got <= paid, "buy " + buy + " sell " + sell + " boost " + boost + " qty " + qty);
        }
    }

    @Test
    void itemCurrencySalesPayWholeUnits() {
        assertEquals(7, ShopRules.saleTotal(1.5, 5, true));
        assertEquals(7.5, ShopRules.saleTotal(1.5, 5, false));
        assertEquals(0, ShopRules.saleTotal(1.5, -3, false));
    }

    @Test
    void priceChecks() {
        assertNull(ShopRules.priceProblem(0, true, false, 1e9));
        assertNotNull(ShopRules.priceProblem(0, false, false, 1e9));
        assertNotNull(ShopRules.priceProblem(-1, true, false, 1e9));
        assertNotNull(ShopRules.priceProblem(Double.NaN, true, false, 1e9));
        assertNotNull(ShopRules.priceProblem(2e9, true, false, 1e9));
        assertNotNull(ShopRules.priceProblem(0.001, true, false, 1e9));
        assertNotNull(ShopRules.priceProblem(1.5, false, true, 1e9));
        assertNull(ShopRules.priceProblem(2, false, true, 1e9));
    }

    @Test
    void entriesAreFoundByIdOrAUniquePrefix() {
        List<ShopServiceImpl.Stock> list = new ArrayList<>();
        UUID a = UUID.fromString("aaaa1111-0000-0000-0000-000000000000");
        UUID b = UUID.fromString("aaaa2222-0000-0000-0000-000000000000");
        list.add(new ShopServiceImpl.Stock(a, "x", null, 1, 1, null, "", 0));
        list.add(new ShopServiceImpl.Stock(b, "y", null, 1, 1, null, "", 0));
        assertEquals(0, ShopServiceImpl.find(list, a.toString()));
        assertEquals(1, ShopServiceImpl.find(list, "AAAA2"));
        assertEquals(-1, ShopServiceImpl.find(list, "aaaa"), "an ambiguous prefix finds nothing");
        assertEquals(-1, ShopServiceImpl.find(list, "aaa"), "too short");
        assertEquals(-1, ShopServiceImpl.find(list, null));
        assertEquals(-1, ShopServiceImpl.find(list, " "));
    }

    @Test
    void vaultRowValuesParse() {
        assertEquals(2, FactionManagerPage.pageSlot("2:14:Ore_Iron")[0]);
        assertEquals(14, FactionManagerPage.pageSlot("2:14:Ore_Iron")[1]);
        assertEquals("Ore_Iron", FactionManagerPage.itemOf("2:14:Ore_Iron"));
        assertNull(FactionManagerPage.itemOf("2:14"));
        assertNull(FactionManagerPage.pageSlot("x:1"));
        assertNull(FactionManagerPage.pageSlot("3"));
        assertNull(FactionManagerPage.pageSlot(null));
    }

    @Test
    void pageInputsParse() {
        assertEquals(32, ShopPage.parseQuantity("32"));
        assertEquals(1, ShopPage.parseQuantity("33"));
        assertEquals(1, ShopPage.parseQuantity("abc"));
        assertEquals(1234.5, ShopPage.parsePrice("1,234.5"));
        assertNull(ShopPage.parsePrice("ten"));
        assertNull(ShopPage.parsePrice(" "));
    }
}
