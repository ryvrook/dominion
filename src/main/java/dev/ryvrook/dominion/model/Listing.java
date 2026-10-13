package dev.ryvrook.dominion.model;

import org.bson.Document;

import java.util.UUID;

/**
 * A market listing, buy order or auction. Items are stored as the BSON produced by ItemStack.CODEC
 * so metadata and durability survive. Kind is SALE, ORDER or AUCTION.
 */
public final class Listing {
    public enum Kind { SALE, ORDER, AUCTION }

    private final UUID id;
    private final Kind kind;
    private final UUID seller;
    private String sellerName;
    private final UUID sellerFaction;
    private final String itemId;
    private final int quantity;
    private final Document item;
    private double price;
    private double buyout;
    private UUID topBidder;
    private String topBidderName;
    private final long createdAt;
    private long expiresAt;
    private boolean storefront;
    private volatile boolean closed;

    public Listing(UUID id, Kind kind, UUID seller, String sellerName, UUID sellerFaction, String itemId, int quantity,
                   Document item, double price, double buyout, long createdAt, long expiresAt) {
        this.id = id;
        this.kind = kind;
        this.seller = seller;
        this.sellerName = sellerName == null ? "" : sellerName;
        this.sellerFaction = sellerFaction;
        this.itemId = itemId;
        this.quantity = quantity;
        this.item = item;
        this.price = price;
        this.buyout = buyout;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID id() { return id; }
    public Kind kind() { return kind; }
    public UUID seller() { return seller; }
    public String sellerName() { return sellerName; }
    public void setSellerName(String n) { if (n != null) sellerName = n; }
    public UUID sellerFaction() { return sellerFaction; }
    public String itemId() { return itemId; }
    public int quantity() { return quantity; }
    public Document item() { return item; }
    public synchronized double price() { return price; }
    public synchronized void setPrice(double p) { price = p; }
    public synchronized double buyout() { return buyout; }
    public synchronized UUID topBidder() { return topBidder; }
    public synchronized String topBidderName() { return topBidderName; }
    public synchronized void setTopBid(UUID bidder, String name, double amount) { topBidder = bidder; topBidderName = name; price = amount; }
    public long createdAt() { return createdAt; }
    public synchronized long expiresAt() { return expiresAt; }
    public synchronized void setExpiresAt(long t) { expiresAt = t; }
    public synchronized boolean storefront() { return storefront; }
    public synchronized void setStorefront(boolean b) { storefront = b; }
    public boolean closed() { return closed; }
    public void close() { closed = true; }

    public synchronized Document toDocument() {
        return new Document("Id", id.toString()).append("Kind", kind.name()).append("Seller", seller.toString())
                .append("SellerName", sellerName).append("SellerFaction", sellerFaction == null ? null : sellerFaction.toString())
                .append("ItemId", itemId).append("Quantity", quantity).append("Item", item).append("Price", price)
                .append("Buyout", buyout).append("TopBidder", topBidder == null ? null : topBidder.toString())
                .append("TopBidderName", topBidderName).append("CreatedAt", createdAt).append("ExpiresAt", expiresAt)
                .append("Storefront", storefront);
    }

    public static Listing fromDocument(Document d) {
        String sf = d.getString("SellerFaction");
        Listing l = new Listing(UUID.fromString(d.getString("Id")), Kind.valueOf(d.getString("Kind")),
                UUID.fromString(d.getString("Seller")), d.getString("SellerName"), sf == null ? null : UUID.fromString(sf),
                d.getString("ItemId"), d.get("Quantity", Number.class).intValue(), d.get("Item", Document.class),
                d.get("Price", Number.class).doubleValue(), d.get("Buyout") == null ? 0 : d.get("Buyout", Number.class).doubleValue(),
                d.get("CreatedAt", Number.class).longValue(), d.get("ExpiresAt", Number.class).longValue());
        String tb = d.getString("TopBidder");
        l.topBidder = tb == null ? null : UUID.fromString(tb);
        l.topBidderName = d.getString("TopBidderName");
        l.storefront = Boolean.TRUE.equals(d.get("Storefront"));
        return l;
    }
}
