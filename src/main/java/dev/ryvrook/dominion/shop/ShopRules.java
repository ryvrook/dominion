package dev.ryvrook.dominion.shop;

import dev.ryvrook.dominion.text.Messages;

/**
 * The shop's price rules, kept free of the server so they can be tested on their own.
 */
final class ShopRules {
    private ShopRules() {}

    /**
     * The highest outpost sell boost the admin shop honours. Boosts from several outposts multiply,
     * so without a cap an item the shop only buys could be sold for any amount.
     */
    static final double MAX_SELL_BOOST = 10.0;

    static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    /** A usable boost, 1 when the value is missing, not finite or not positive, and never above the cap. */
    static double boost(double raw) {
        if (!Double.isFinite(raw) || raw <= 0) {
            return 1.0;
        }
        return Math.min(raw, MAX_SELL_BOOST);
    }

    /**
     * The sell price of one item after the boost. It never rises above the lowest price the shop
     * charges for the same item, so buying and selling back can never turn a profit. A lowest buy
     * price of zero or less means the shop does not sell the item.
     */
    static double unitSell(double sell, double boost, double lowestBuy) {
        double unit = Math.max(0, sell) * boost(boost);
        return lowestBuy > 0 ? Math.min(unit, lowestBuy) : unit;
    }

    /** What the shop pays for a number of items, in whole cents, or whole units for an item currency. */
    static double saleTotal(double unit, int quantity, boolean whole) {
        double total = round(unit * Math.max(0, quantity));
        return whole ? Math.floor(total + 1e-9) : total;
    }

    /** What the shop charges for a number of items. */
    static double buyTotal(double unit, int quantity) {
        return round(unit * Math.max(0, quantity));
    }

    /** A readable problem with a price, or null. Zero is allowed when allowZero is set. */
    static String priceProblem(double price, boolean allowZero, boolean whole, double max) {
        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0 || (!allowZero && price <= 0)) {
            return Messages.get(allowZero ? ShopText.PRICE_NEGATIVE : ShopText.PRICE_NOT_POSITIVE);
        }
        if (price > max) {
            return Messages.get(ShopText.PRICE_TOO_HIGH);
        }
        if (price > 0 && round(price) <= 0) {
            return Messages.get(ShopText.PRICE_TOO_SMALL);
        }
        if (whole && Math.abs(price - Math.rint(price)) > 1e-6) {
            return Messages.get(ShopText.PRICE_WHOLE);
        }
        return null;
    }
}
