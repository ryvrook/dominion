package dev.ryvrook.dominion.market;

import dev.ryvrook.dominion.model.Listing;

import java.util.UUID;
import java.util.function.DoubleFunction;

/**
 * The market's money and auction rules, kept free of the server so they can be tested on their
 * own. Amounts are rounded to whole cents.
 */
final class MarketRules {
    private MarketRules() {}

    static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    static double roundUp(double amount) {
        return Math.ceil(amount * 100.0 - 1e-6) / 100.0;
    }

    /** Null when the price can be used, otherwise the reason. */
    static String invalidPrice(double price, double max) {
        if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
            return "The price must be more than zero.";
        }
        if (price > max) {
            return "That price is too high.";
        }
        if (round(price) <= 0) {
            return "The price must be at least 0.01.";
        }
        return null;
    }

    /** How a sale total is shared out. Net goes to the seller, tax to the faction bank and burn nowhere. */
    record Split(double net, double tax, double burn) {}

    /**
     * Splits a sale total. The faction tax only applies to a seller with a faction. Relief of 2
     * halves both taxes. Each part is whole cents and the parts always add up to the total, so a
     * misconfigured tax above 100 percent can never pay out more than the buyer paid.
     */
    static Split split(double total, boolean taxed, double taxPercent, double serverTaxPercent, double relief) {
        double t = round(Math.max(0, total));
        double r = Double.isFinite(relief) && relief > 1.0 ? relief : 1.0;
        double tax = taxed ? round(t * clampPercent(taxPercent) / 100.0 / r) : 0;
        tax = Math.min(tax, t);
        double burn = round(t * clampPercent(serverTaxPercent) / 100.0 / r);
        burn = Math.min(burn, round(t - tax));
        double net = round(t - tax - burn);
        return new Split(Math.max(0, net), tax, burn);
    }

    private static double clampPercent(double p) {
        return Double.isFinite(p) ? Math.max(0, p) : 0;
    }

    /**
     * The lowest acceptable bid. Without bids the starting price is enough. With bids the next bid
     * must beat the current one by the step percent and by at least one cent, or one whole unit for
     * an item currency.
     */
    static double minBid(double current, boolean hasBids, double stepPercent, boolean whole) {
        double min = !hasBids ? current
                : Math.max(roundUp(current * (1 + Math.max(0, stepPercent) / 100.0)), round(current + (whole ? 1 : 0.01)));
        if (whole) {
            min = Math.ceil(min - 1e-6);
        }
        return min;
    }

    /**
     * What a bid does. A failed bid only carries the error. Otherwise charge is what the bidder
     * pays now, which for a raise of their own top bid is just the difference, refundTo and refund
     * name the outbid player, and expiresAt is the closing time after any anti snipe extension.
     */
    record Bid(String error, double offer, double charge, boolean boughtOut, UUID refundTo, double refund,
               long expiresAt, boolean extended) {
        static Bid fail(String error) {
            return new Bid(error, 0, 0, false, null, 0, 0, false);
        }

        boolean ok() {
            return error == null;
        }
    }

    static Bid decideBid(Listing l, UUID bidder, double amount, long now, double stepPercent, boolean whole, long antiSnipeMs,
                         DoubleFunction<String> fmt) {
        if (l == null || l.closed() || l.expiresAt() <= now) {
            return Bid.fail("That auction has ended.");
        }
        if (l.kind() != Listing.Kind.AUCTION) {
            return Bid.fail("That listing is not an auction.");
        }
        if (l.seller().equals(bidder)) {
            return Bid.fail("You cannot bid on your own auction.");
        }
        double offer = round(amount);
        UUID previous = l.topBidder();
        double previousBid = l.price();
        boolean boughtOut = l.buyout() > 0 && offer >= l.buyout();
        if (boughtOut) {
            offer = l.buyout();
        } else {
            double min = minBid(previousBid, previous != null, stepPercent, whole);
            if (offer < min) {
                return Bid.fail("The lowest bid you can place is " + fmt.apply(min) + ".");
            }
        }
        boolean raise = bidder.equals(previous);
        double charge = raise ? round(offer - previousBid) : offer;
        UUID refundTo = previous != null && !raise ? previous : null;
        double refund = refundTo == null ? 0 : previousBid;
        long expires = l.expiresAt();
        boolean extended = false;
        if (!boughtOut && antiSnipeMs > 0 && expires - now < antiSnipeMs) {
            expires += antiSnipeMs;
            extended = true;
        }
        return new Bid(null, offer, Math.max(0, charge), boughtOut, refundTo, refund, expires, extended);
    }

    /** What closing an expired listing does. */
    enum Expiry { RETURN_ITEMS, REFUND_ORDER, AWARD_WINNER }

    static Expiry expiry(Listing l) {
        return switch (l.kind()) {
            case SALE -> Expiry.RETURN_ITEMS;
            case ORDER -> Expiry.REFUND_ORDER;
            case AUCTION -> l.topBidder() == null ? Expiry.RETURN_ITEMS : Expiry.AWARD_WINNER;
        };
    }

    /** The money still held for an open buy order. */
    static double orderEscrow(Listing order) {
        return round(order.price() * order.quantity());
    }
}
