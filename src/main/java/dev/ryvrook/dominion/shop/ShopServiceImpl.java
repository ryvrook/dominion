package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.DominionEvent;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.market.Items;
import dev.ryvrook.dominion.model.Faction;
import dev.ryvrook.dominion.model.Rank;
import dev.ryvrook.dominion.service.EconomyService;
import dev.ryvrook.dominion.service.EventsService;
import dev.ryvrook.dominion.service.FactionService;
import dev.ryvrook.dominion.service.MarketService;
import dev.ryvrook.dominion.service.ProgressService;
import dev.ryvrook.dominion.service.ShopService;
import dev.ryvrook.dominion.service.VaultService;
import dev.ryvrook.dominion.model.FactionAction;
import dev.ryvrook.dominion.text.CommonText;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import dev.ryvrook.dominion.util.Time;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Admin shops with infinite stock and faction shops stocked by officers.
 *
 * The admin catalogue lives in shops/admin.json and each faction shop in shops/factionId.json.
 * All shop state is guarded by one lock. Economy calls happen inside it, which is safe because the
 * economy never calls back into the shop. Inventory work runs on the player's world thread through
 * {@link Items#onWorld} and takes the lock there for the short check and update.
 *
 * Officers stock the faction shop either from a vault page and slot through
 * {@link VaultService#takeSlot}, which needs the VAULT permission as well as the officer rank, or
 * from the item in their hand through {@link #factionStockHeld}.
 */
public final class ShopServiceImpl implements ShopService {
    static final double MAX_PRICE = 1e9;
    static final int MAX_QUANTITY = 6400;
    private static final int MAX_CATEGORY_LENGTH = 24;

    /** One admin catalogue line. A buy or sell price of zero means that direction is not offered. */
    record AdminEntry(String itemId, double buy, double sell) {}

    /** A named admin category with its entries in display order. */
    static final class Category {
        final String name;
        final LinkedHashMap<String, AdminEntry> entries = new LinkedHashMap<>();

        Category(String name) {
            this.name = name;
        }
    }

    /** One faction shop entry. Price is per item. The item document keeps the stack metadata. */
    record Stock(UUID id, String itemId, Document item, int quantity, double price, UUID stockedBy, String stockedByName, long time) {
        Stock withQuantity(int q) {
            return new Stock(id, itemId, item, q, price, stockedBy, stockedByName, time);
        }

        Document toDocument() {
            return new Document("Id", id.toString()).append("ItemId", itemId).append("Item", item)
                    .append("Quantity", quantity).append("Price", price)
                    .append("StockedBy", stockedBy == null ? null : stockedBy.toString())
                    .append("StockedByName", stockedByName).append("Time", time);
        }

        static Stock fromDocument(Document d) {
            String by = d.getString("StockedBy");
            return new Stock(UUID.fromString(d.getString("Id")), d.getString("ItemId"),
                    d.get("Item") instanceof Document doc ? doc : null,
                    d.get("Quantity", Number.class).intValue(), d.get("Price", Number.class).doubleValue(),
                    by == null || by.isBlank() ? null : UUID.fromString(by),
                    d.getString("StockedByName") == null ? "" : d.getString("StockedByName"),
                    d.get("Time") instanceof Number n ? n.longValue() : 0L);
        }
    }

    private final DominionContext ctx;
    private final Object lock = new Object();
    private final Path dir;
    private final Path adminFile;
    private final LinkedHashMap<String, Category> catalogue = new LinkedHashMap<>();
    private final Map<UUID, List<Stock>> factionShops = new HashMap<>();
    private final dev.ryvrook.dominion.market.MissionCredit missions = new dev.ryvrook.dominion.market.MissionCredit();

    public ShopServiceImpl(DominionContext ctx) {
        this.ctx = ctx;
        this.dir = ctx.repo().root().resolve("shops");
        this.adminFile = dir.resolve("admin.json");
    }

    // ---- lifecycle ----------------------------------------------------------------

    void load() {
        int entries = 0;
        synchronized (lock) {
            catalogue.clear();
            Document d = ctx.files().read(adminFile);
            if (d != null && d.get("Categories") instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Document cd) || cd.getString("Name") == null) {
                        continue;
                    }
                    Category cat = new Category(cd.getString("Name"));
                    if (cd.get("Entries") instanceof List<?> el) {
                        for (Object e : el) {
                            if (e instanceof Document ed && ed.getString("ItemId") != null) {
                                try {
                                    AdminEntry entry = new AdminEntry(ed.getString("ItemId"),
                                            ed.get("Buy", Number.class).doubleValue(), ed.get("Sell", Number.class).doubleValue());
                                    cat.entries.put(key(entry.itemId()), entry);
                                    entries++;
                                } catch (RuntimeException ex) {
                                    ctx.logger().at(Level.WARNING).log("Skipping unreadable admin shop entry in %s", cat.name);
                                }
                            }
                        }
                    }
                    catalogue.put(key(cat.name), cat);
                }
            }
        }
        ctx.logger().at(Level.INFO).log("Dominion shops loaded %d admin categories with %d entries", catalogue.size(), entries);
    }

    void saveAll() {
        synchronized (lock) {
            saveAdmin();
            for (UUID id : factionShops.keySet()) {
                saveFaction(id);
            }
        }
    }

    /** Returns the stock of a disbanded faction to whoever stocked it through the market mailbox. */
    void onEvent(DominionEvent event) {
        if (!(event instanceof DominionEvent.FactionDisbanded gone) || gone.factionId() == null) {
            return;
        }
        List<Stock> stock;
        synchronized (lock) {
            stock = new ArrayList<>(stockList(gone.factionId()));
            factionShops.remove(gone.factionId());
        }
        if (stock.isEmpty()) {
            ctx.files().delete(factionFile(gone.factionId()));
            return;
        }
        MarketService market = ctx.serviceOrNull(MarketService.class);
        List<Stock> kept = new ArrayList<>();
        for (Stock s : stock) {
            if (s.quantity() <= 0) {
                continue;
            }
            List<ItemStack> stacks = market == null || s.stockedBy() == null ? List.of() : Items.split(s.item(), s.itemId(), s.quantity());
            if (market != null && s.stockedBy() != null && Items.quantity(stacks) == s.quantity()) {
                market.mail(s.stockedBy(), stacks, 0);
                Msg.send(Players.online(s.stockedBy()), Msg.info(Messages.get(ShopText.STOCK_MAILED, "item", Names.of(s.itemId()),
                        "faction", gone.tag())));
            } else {
                kept.add(s);
            }
        }
        if (!kept.isEmpty()) {
            List<Document> docs = new ArrayList<>();
            for (Stock s : kept) {
                docs.add(s.toDocument());
            }
            Path out = dir.resolve(gone.factionId() + ".disbanded.json");
            ctx.files().write(out, new Document("Entries", docs));
            ctx.logger().at(Level.WARNING).log("Dominion kept %d faction shop entries of %s in %s", kept.size(), gone.tag(), out);
        }
        ctx.files().delete(factionFile(gone.factionId()));
    }

    // ---- switches -------------------------------------------------------------------

    boolean adminEnabled() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return ctx.settings().bool("shop.adminEnabled") && eco != null && eco.enabled();
    }

    boolean factionEnabled() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return ctx.settings().bool("shop.factionEnabled") && eco != null && eco.enabled();
    }

    int maxFactionEntries() {
        return Math.max(1, ctx.settings().integer("shop.factionMaxEntries"));
    }

    // ---- admin catalogue ------------------------------------------------------------

    @Override
    public List<String> adminCategories() {
        List<String> out = new ArrayList<>();
        synchronized (lock) {
            for (Category c : catalogue.values()) {
                out.add(c.name);
            }
        }
        return out;
    }

    /** The display name of a category matched without regard to case, or null. */
    String categoryName(String category) {
        if (category == null) {
            return null;
        }
        synchronized (lock) {
            Category c = catalogue.get(key(category));
            return c == null ? null : c.name;
        }
    }

    List<AdminEntry> entries(String category) {
        synchronized (lock) {
            Category c = category == null ? null : catalogue.get(key(category));
            return c == null ? new ArrayList<>() : new ArrayList<>(c.entries.values());
        }
    }

    int entryCount(String category) {
        synchronized (lock) {
            Category c = category == null ? null : catalogue.get(key(category));
            return c == null ? 0 : c.entries.size();
        }
    }

    @Override
    public Result adminSet(String category, String itemId, double buyPrice, double sellPrice) {
        String cat = category == null ? "" : category.trim();
        if (cat.isEmpty() || cat.length() > MAX_CATEGORY_LENGTH || !cat.matches("[A-Za-z0-9 _-]+")) {
            return Result.fail(Messages.get(ShopText.CATEGORY_NAME_RULES, "max", MAX_CATEGORY_LENGTH));
        }
        if (itemId == null || itemId.isBlank()) {
            return Result.fail(Messages.get(ShopText.GIVE_ITEM_ID));
        }
        if (!Items.exists(itemId.trim())) {
            return Result.fail(Messages.get(ShopText.NO_SUCH_ITEM, "item", itemId.trim()));
        }
        String id = Items.item(itemId.trim()).getId();
        String bad = priceProblem(buyPrice, true);
        if (bad == null) {
            bad = priceProblem(sellPrice, true);
        }
        if (bad != null) {
            return Result.fail(bad);
        }
        double buy = round(buyPrice);
        double sell = round(sellPrice);
        if (buy <= 0 && sell <= 0) {
            return Result.fail(Messages.get(ShopText.GIVE_A_PRICE));
        }
        if (buy > 0 && sell > buy) {
            return Result.fail(Messages.get(ShopText.SELL_ABOVE_BUY));
        }
        boolean existed;
        String catName;
        synchronized (lock) {
            Category c = catalogue.computeIfAbsent(key(cat), k -> new Category(cat));
            catName = c.name;
            existed = c.entries.containsKey(key(id));
            c.entries.put(key(id), new AdminEntry(id, buy, sell));
            saveAdmin();
        }
        return Result.pass(Messages.get(existed ? ShopText.ENTRY_UPDATED : ShopText.ENTRY_ADDED, "item", Names.of(id),
                "category", catName, "prices", priceWords(buy, sell)));
    }

    @Override
    public Result adminRemove(String category, String itemId) {
        if (category == null || itemId == null) {
            return Result.fail(Messages.get(ShopText.GIVE_CATEGORY_AND_ITEM));
        }
        synchronized (lock) {
            Category c = catalogue.get(key(category));
            if (c == null) {
                return Result.fail(Messages.get(ShopText.NO_SUCH_CATEGORY, "category", category));
            }
            AdminEntry removed = c.entries.remove(key(itemId.trim()));
            if (removed == null) {
                return Result.fail(Messages.get(ShopText.NOT_LISTED, "item", itemId, "category", c.name));
            }
            boolean dropped = c.entries.isEmpty();
            if (dropped) {
                catalogue.remove(key(c.name));
            }
            saveAdmin();
            return Result.pass(Messages.get(ShopText.ENTRY_REMOVED, "item", Names.of(removed.itemId()), "category", c.name)
                    + (dropped ? " " + Messages.get(ShopText.CATEGORY_DROPPED) : ""));
        }
    }

    Result removeCategory(String category) {
        synchronized (lock) {
            Category c = category == null ? null : catalogue.remove(key(category));
            if (c == null) {
                return Result.fail(Messages.get(ShopText.NO_SUCH_CATEGORY, "category", category));
            }
            saveAdmin();
            return Result.pass(Messages.get(c.entries.size() == 1 ? ShopText.CATEGORY_REMOVED_ONE : ShopText.CATEGORY_REMOVED_MANY,
                    "category", c.name, "count", c.entries.size()));
        }
    }

    /** Adds a small starter catalogue. Existing entries are kept and items missing on this server are skipped. */
    Result seedDefaults() {
        Object[][] seed = {
                {"Ores", "Ore_Copper", 4, 1}, {"Ores", "Ore_Iron", 8, 2}, {"Ores", "Ore_Silver", 14, 4},
                {"Ores", "Ore_Gold", 20, 6}, {"Ores", "Ore_Cobalt", 30, 9}, {"Ores", "Ingredient_Charcoal", 3, 1},
                {"Bars", "Ingredient_Bar_Copper", 12, 4}, {"Bars", "Ingredient_Bar_Iron", 24, 8},
                {"Bars", "Ingredient_Bar_Silver", 40, 13}, {"Bars", "Ingredient_Bar_Gold", 60, 20},
                {"Bars", "Ingredient_Bar_Cobalt", 90, 30}, {"Bars", "Ingredient_Bar_Thorium", 120, 40},
                {"Food", "Plant_Fruit_Apple", 3, 1}, {"Food", "Food_Bread", 6, 1}, {"Food", "Food_Vegetable_Cooked", 6, 1},
                {"Food", "Food_Wildmeat_Cooked", 8, 2}, {"Food", "Food_Fish_Grilled", 8, 2}, {"Food", "Food_Pie_Apple", 15, 3},
                {"Tools", "Tool_Pickaxe_Copper", 60, 0}, {"Tools", "Tool_Hatchet_Copper", 50, 0},
                {"Tools", "Tool_Shovel_Copper", 40, 0}, {"Tools", "Tool_Pickaxe_Iron", 150, 0},
                {"Tools", "Tool_Hatchet_Iron", 130, 0}, {"Tools", "Tool_Shovel_Iron", 110, 0},
        };
        int added = 0;
        int missing = 0;
        synchronized (lock) {
            for (Object[] row : seed) {
                String cat = (String) row[0];
                String id = (String) row[1];
                if (!Items.exists(id)) {
                    missing++;
                    continue;
                }
                String canonical = Items.item(id).getId();
                Category c = catalogue.computeIfAbsent(key(cat), k -> new Category(cat));
                if (c.entries.containsKey(key(canonical))) {
                    continue;
                }
                c.entries.put(key(canonical), new AdminEntry(canonical, ((Integer) row[2]).doubleValue(), ((Integer) row[3]).doubleValue()));
                added++;
            }
            saveAdmin();
        }
        if (added == 0) {
            return Result.fail(Messages.get(missing > 0 && missing == seed.length ? ShopText.SEED_NONE_EXIST
                    : ShopText.SEED_IN_PLACE));
        }
        return Result.pass(Messages.get(ShopText.SEED_ADDED, "count", added)
                + (missing > 0 ? " " + Messages.get(ShopText.SEED_SKIPPED, "count", missing) : ""));
    }

    /** The first category entry that buys this item back, or null. */
    AdminEntry sellableEntry(String itemId) {
        synchronized (lock) {
            for (Category c : catalogue.values()) {
                AdminEntry e = c.entries.get(key(itemId));
                if (e != null && e.sell() > 0) {
                    return e;
                }
            }
        }
        return null;
    }

    private AdminEntry entry(String category, String itemId) {
        synchronized (lock) {
            Category c = category == null ? null : catalogue.get(key(category));
            return c == null || itemId == null ? null : c.entries.get(key(itemId));
        }
    }

    // ---- admin buying and selling ----------------------------------------------------

    Result buyAdmin(UUID buyer, String category, String itemId, int qty) {
        if (!adminEnabled()) {
            return Result.fail(Messages.get(ShopText.ADMIN_CLOSED));
        }
        if (qty < 1 || qty > MAX_QUANTITY) {
            return Result.fail(Messages.get(ShopText.BUY_RANGE, "max", MAX_QUANTITY));
        }
        AdminEntry e = entry(category, itemId);
        if (e == null || e.buy() <= 0) {
            return Result.fail(Messages.get(ShopText.NOT_FOR_SALE));
        }
        if (!Items.exists(e.itemId())) {
            return Result.fail(Messages.get(ShopText.ITEM_GONE));
        }
        PlayerRef p = Players.online(buyer);
        if (p == null) {
            return Result.fail(Messages.get(ShopText.BUY_OFFLINE));
        }
        double total = round(e.buy() * qty);
        String what = Messages.get(ShopText.LOT, "count", qty, "item", Names.of(e.itemId()));
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            List<ItemStack> stacks = Items.split(null, e.itemId(), qty);
            if (quantity(stacks) != qty) {
                return Result.fail(Messages.get(ShopText.CANNOT_MAKE));
            }
            Result paid = economy().withdraw(buyer, total, Messages.get(ShopText.MEMO_ADMIN_PURCHASE, "lot", what));
            if (paid.failed()) {
                return Result.fail(paid.message());
            }
            List<ItemStack> left = giveSafely(store, ref, stacks);
            String extra = "";
            if (!left.isEmpty()) {
                MarketService market = ctx.serviceOrNull(MarketService.class);
                if (market != null) {
                    market.mail(buyer, left, 0);
                    extra = " " + Messages.get(ShopText.REST_MAILED);
                } else {
                    int n = left.stream().mapToInt(ItemStack::getQuantity).sum();
                    double refund = round(e.buy() * n);
                    economy().deposit(buyer, refund, Messages.get(ShopText.MEMO_ADMIN_REFUND));
                    extra = " " + Messages.get(ShopText.REST_REFUNDED, "count", n);
                }
            }
            return Result.pass(Messages.get(ShopText.BOUGHT, "lot", what, "amount", fmt(total)) + extra);
        });
        return await(p, f, Messages.get(ShopText.BUYING_LOT, "lot", what));
    }

    /** Sells up to qty of an item from the player's inventory at the admin sell price. */
    Result sellAdmin(UUID seller, String category, String itemId, int qty) {
        if (!adminEnabled()) {
            return Result.fail(Messages.get(ShopText.ADMIN_CLOSED));
        }
        if (qty < 1 || qty > MAX_QUANTITY) {
            return Result.fail(Messages.get(ShopText.SELL_RANGE, "max", MAX_QUANTITY));
        }
        AdminEntry e = entry(category, itemId);
        if (e == null || e.sell() <= 0) {
            return Result.fail(Messages.get(ShopText.DOES_NOT_BUY_THAT));
        }
        PlayerRef p = Players.online(seller);
        if (p == null) {
            return Result.fail(Messages.get(ShopText.SELL_OFFLINE));
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            ItemContainer inv = Items.inventory(store, ref);
            int have = inv == null ? 0 : Items.count(inv, e.itemId());
            if (have <= 0) {
                return Result.fail(Messages.get(ShopText.NONE_TO_SELL, "item", Names.of(e.itemId())));
            }
            List<ItemStack> taken = Items.take(inv, e.itemId(), Math.min(qty, have));
            return payForSale(seller, store, ref, taken, e);
        });
        return await(p, f, Messages.get(ShopText.SELLING));
    }

    /** Sells the stack in the player's hand at the admin sell price. */
    Result sellHeld(UUID seller) {
        if (!adminEnabled()) {
            return Result.fail(Messages.get(ShopText.ADMIN_CLOSED));
        }
        PlayerRef p = Players.online(seller);
        if (p == null) {
            return Result.fail(Messages.get(ShopText.SELL_OFFLINE));
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            Items.Held held = Items.held(store, ref);
            if (held == null) {
                return Result.fail(Messages.get(ShopText.HOLD_TO_SELL));
            }
            if (held.stack().isBroken()) {
                return Result.fail(Messages.get(ShopText.NO_BROKEN));
            }
            AdminEntry e = sellableEntry(held.stack().getItemId());
            if (e == null) {
                return Result.fail(Messages.get(ShopText.DOES_NOT_BUY, "item", Names.of(held.stack().getItemId())));
            }
            ItemStack taken = Items.takeHeld(held, 0);
            if (taken == null) {
                return Result.fail(Messages.get(ShopText.HAND_TAKE_FAILED));
            }
            return payForSale(seller, store, ref, List.of(taken), e);
        });
        return await(p, f, Messages.get(ShopText.SELLING));
    }

    /** Pays for taken items, giving them back when the payment fails. World thread only. */
    private Result payForSale(UUID seller, com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                              com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                              List<ItemStack> taken, AdminEntry e) {
        int got = taken.stream().mapToInt(ItemStack::getQuantity).sum();
        if (got <= 0) {
            return Result.fail(Messages.get(ShopText.INVENTORY_TAKE_FAILED));
        }
        // An item currency pays whole units, so a boosted fraction is dropped.
        double total = ShopRules.saleTotal(boostedUnitSell(e, sellBoost(seller)), got, wholeOnly());
        Result r;
        try {
            r = economy().deposit(seller, total, Messages.get(ShopText.MEMO_ADMIN_SALE, "count", got, "item", Names.of(e.itemId())));
        } catch (RuntimeException ex) {
            ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion admin shop payment failed");
            r = Result.fail("");
        }
        if (r.failed()) {
            List<ItemStack> left = giveSafely(store, ref, taken);
            if (!left.isEmpty()) {
                MarketService market = ctx.serviceOrNull(MarketService.class);
                if (market != null) {
                    market.mail(seller, left, 0);
                }
            }
            return Result.fail(r.message().isBlank() ? Messages.get(ShopText.NOT_PAID) : r.message());
        }
        return Result.pass(Messages.get(ShopText.SOLD, "count", got, "item", Names.of(e.itemId()), "amount", fmt(total)));
    }

    // ---- faction shops -----------------------------------------------------------------

    /** A copy of a faction's stock in display order. */
    List<Stock> stock(UUID factionId) {
        synchronized (lock) {
            return new ArrayList<>(stockList(factionId));
        }
    }

    boolean isOfficer(UUID player, Faction f) {
        if (f == null || player == null) {
            return false;
        }
        Rank r = f.rankOf(player);
        return r != null && r.atLeast(Rank.OFFICER);
    }

    /** Takes the item in a vault page and slot, both 1 based, and lists it at the price for each item. */
    @Override
    public Result factionStock(UUID actor, int vaultPage, int slot, double price) {
        return factionStock(actor, vaultPage, slot, price, null);
    }

    /**
     * As above, refusing when the slot no longer holds the expected item id, so a slot rearranged in
     * an open vault window is not stocked at the price meant for another item.
     */
    Result factionStock(UUID actor, int vaultPage, int slot, double price, String expectedItemId) {
        if (!factionEnabled()) {
            return Result.fail(Messages.get(ShopText.FACTION_SHOPS_OFF));
        }
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!isOfficer(actor, f)) {
            return Result.fail(Messages.get(ShopText.STOCK_OFFICERS));
        }
        if (!factions().can(actor, FactionAction.VAULT)) {
            return Result.fail(Messages.get(ShopText.VAULT_RANK));
        }
        dev.ryvrook.dominion.model.Member member = f.member(actor);
        if (member != null && member.onHold(Time.now())) {
            return Result.fail(Messages.get(ShopText.NEW_MEMBER_VAULT, "time", Time.remaining(member.holdUntil() - Time.now())));
        }
        VaultService vault = ctx.serviceOrNull(VaultService.class);
        if (vault == null) {
            return Result.fail(Messages.get(ShopText.VAULT_UNAVAILABLE));
        }
        int pages = vault.pages(f);
        if (vaultPage < 1 || vaultPage > pages) {
            return Result.fail(pages == 1 ? Messages.get(ShopText.VAULT_ONE_PAGE) : Messages.get(ShopText.VAULT_PAGE_RANGE, "pages", pages));
        }
        if (slot < 1) {
            return Result.fail(Messages.get(ShopText.PICK_SLOT));
        }
        String bad = priceProblem(price, false);
        if (bad != null) {
            return Result.fail(bad);
        }
        double each = round(price);
        String actorName = name(actor);
        Stock s;
        synchronized (lock) {
            List<Stock> list = stockList(f.id());
            int max = maxFactionEntries();
            if (list.size() >= max) {
                return Result.fail(Messages.get(ShopText.SHOP_FULL, "count", list.size(), "max", max));
            }
            ItemStack peek = vault.peekSlot(f, vaultPage, slot);
            if (peek == null || ItemStack.isEmpty(peek)) {
                return Result.fail(Messages.get(ShopText.SLOT_EMPTY, "page", vaultPage, "slot", slot));
            }
            if (expectedItemId != null && !expectedItemId.equals(peek.getItemId())) {
                return Result.fail(Messages.get(ShopText.SLOT_CHANGED, "page", vaultPage, "slot", slot));
            }
            if (peek.isBroken()) {
                return Result.fail(Messages.get(ShopText.NO_BROKEN_STOCK));
            }
            ItemStack taken = vault.takeSlot(f, vaultPage, slot);
            if (taken == null || ItemStack.isEmpty(taken)) {
                return Result.fail(Messages.get(ShopText.SLOT_TAKE_FAILED));
            }
            s = new Stock(UUID.randomUUID(), taken.getItemId(), Items.toDocument(taken), taken.getQuantity(), each,
                    actor, actorName, Time.now());
            list.add(s);
            saveFaction(f.id());
        }
        String what = lot(s.quantity(), s.itemId());
        factions().audit(f, actor, actorName, "shop", Messages.get(ShopText.AUDIT_STOCKED_VAULT, "lot", what, "page", vaultPage,
                "slot", slot, "price", fmt(each)));
        ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
        return Result.pass(Messages.get(ShopText.STOCKED_VAULT, "lot", what, "price", fmt(each)));
    }

    @Override
    public Result factionStockHeld(UUID actor, double price) {
        if (!factionEnabled()) {
            return Result.fail(Messages.get(ShopText.FACTION_SHOPS_OFF));
        }
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!isOfficer(actor, f)) {
            return Result.fail(Messages.get(ShopText.STOCK_OFFICERS));
        }
        String bad = priceProblem(price, false);
        if (bad != null) {
            return Result.fail(bad);
        }
        double each = round(price);
        PlayerRef p = Players.online(actor);
        if (p == null) {
            return Result.fail(Messages.get(ShopText.STOCK_OFFLINE));
        }
        String actorName = name(actor);
        CompletableFuture<Result> fut = Items.onWorld(p, (store, ref) -> {
            Items.Held held = Items.held(store, ref);
            if (held == null) {
                return Result.fail(Messages.get(ShopText.HOLD_TO_STOCK));
            }
            if (held.stack().isBroken()) {
                return Result.fail(Messages.get(ShopText.NO_BROKEN_STOCK));
            }
            Stock s;
            synchronized (lock) {
                List<Stock> list = stockList(f.id());
                int max = maxFactionEntries();
                if (list.size() >= max) {
                    return Result.fail(Messages.get(ShopText.SHOP_FULL, "count", list.size(), "max", max));
                }
                ItemStack taken = Items.takeHeld(held, 0);
                if (taken == null) {
                    return Result.fail(Messages.get(ShopText.HAND_TAKE_FAILED));
                }
                s = new Stock(UUID.randomUUID(), taken.getItemId(), Items.toDocument(taken), taken.getQuantity(), each,
                        actor, actorName, Time.now());
                list.add(s);
                saveFaction(f.id());
            }
            String what = lot(s.quantity(), s.itemId());
            factions().audit(f, actor, actorName, "shop", Messages.get(ShopText.AUDIT_STOCKED, "lot", what, "price", fmt(each)));
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            return Result.pass(Messages.get(ShopText.STOCKED, "lot", what, "price", fmt(each)));
        });
        return await(p, fut, Messages.get(ShopText.STOCKING));
    }

    @Override
    public Result factionUnstock(UUID actor, String entryId) {
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail(Messages.get(CommonText.NOT_IN_FACTION));
        }
        if (!isOfficer(actor, f)) {
            return Result.fail(Messages.get(ShopText.TAKE_OFFICERS));
        }
        // Stock usually came from the vault, so taking someone else's entry needs the same rights as the vault.
        boolean vaultRights = factions().can(actor, FactionAction.VAULT);
        dev.ryvrook.dominion.model.Member member = f.member(actor);
        boolean onHold = member != null && member.onHold(Time.now());
        PlayerRef p = Players.online(actor);
        if (p == null) {
            return Result.fail(Messages.get(ShopText.TAKE_OFFLINE));
        }
        String actorName = name(actor);
        CompletableFuture<Result> fut = Items.onWorld(p, (store, ref) -> {
            Stock s;
            int returned;
            boolean mailed = false;
            synchronized (lock) {
                List<Stock> list = stockList(f.id());
                int index = find(list, entryId);
                if (index < 0) {
                    return Result.fail(Messages.get(ShopText.ENTRY_GONE));
                }
                s = list.get(index);
                if (!actor.equals(s.stockedBy())) {
                    if (!vaultRights) {
                        return Result.fail(Messages.get(ShopText.TAKE_OWN_ONLY_RANK));
                    }
                    if (onHold) {
                        return Result.fail(Messages.get(ShopText.TAKE_OWN_ONLY_NEW));
                    }
                }
                List<ItemStack> stacks = Items.split(s.item(), s.itemId(), s.quantity());
                if (Items.quantity(stacks) != s.quantity()) {
                    return Result.fail(Messages.get(ShopText.CANNOT_MAKE));
                }
                List<ItemStack> left = giveSafely(store, ref, stacks);
                int leftQty = left.stream().mapToInt(ItemStack::getQuantity).sum();
                MarketService market = ctx.serviceOrNull(MarketService.class);
                if (leftQty > 0 && market != null) {
                    market.mail(actor, left, 0);
                    mailed = true;
                    leftQty = 0;
                }
                returned = s.quantity() - leftQty;
                if (leftQty > 0) {
                    list.set(index, s.withQuantity(leftQty));
                } else {
                    list.remove(index);
                }
                saveFaction(f.id());
            }
            if (returned <= 0) {
                return Result.fail(Messages.get(ShopText.INVENTORY_FULL));
            }
            String what = lot(returned, s.itemId());
            factions().audit(f, actor, actorName, "shop", Messages.get(ShopText.AUDIT_TOOK, "lot", what));
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            return Result.pass(Messages.get(ShopText.TOOK, "lot", what)
                    + (mailed ? " " + Messages.get(ShopText.TOOK_MAILED) : ""));
        });
        return await(p, fut, Messages.get(ShopText.TAKING));
    }

    Result buyStock(UUID buyer, UUID factionId, String entryId, int qty) {
        if (!factionEnabled()) {
            return Result.fail(Messages.get(ShopText.FACTION_SHOPS_OFF));
        }
        if (qty < 1 || qty > MAX_QUANTITY) {
            return Result.fail(Messages.get(ShopText.BUY_RANGE, "max", MAX_QUANTITY));
        }
        Faction f = factions().byId(factionId);
        if (f == null) {
            return Result.fail(Messages.get(ShopText.FACTION_GONE));
        }
        PlayerRef p = Players.online(buyer);
        if (p == null) {
            return Result.fail(Messages.get(ShopText.BUY_OFFLINE_FACTION));
        }
        String buyerName = name(buyer);
        CompletableFuture<Result> fut = Items.onWorld(p, (store, ref) -> {
            Stock s;
            int n;
            List<ItemStack> stacks;
            // The items are reserved under the lock and paid for outside it, so a slow economy never holds the shop lock.
            synchronized (lock) {
                List<Stock> list = stockList(f.id());
                int index = find(list, entryId);
                if (index < 0) {
                    return Result.fail(Messages.get(ShopText.OUT_OF_STOCK));
                }
                s = list.get(index);
                n = Math.min(qty, s.quantity());
                stacks = Items.split(s.item(), s.itemId(), n);
                if (quantity(stacks) != n) {
                    return Result.fail(Messages.get(ShopText.CANNOT_MAKE));
                }
                if (n < s.quantity()) {
                    list.set(index, s.withQuantity(s.quantity() - n));
                } else {
                    list.remove(index);
                }
                saveFaction(f.id());
            }
            double total = round(s.price() * n);
            String reserved = lot(n, s.itemId());
            Result paid = economy().withdraw(buyer, total, Messages.get(ShopText.MEMO_BOUGHT_FROM, "lot", reserved, "faction", f.tag()));
            if (paid.failed()) {
                returnStock(f.id(), s, n);
                return Result.fail(paid.message());
            }
            Result credit = economy().bankCredit(f, total, Messages.get(ShopText.MEMO_BANK_SALE, "buyer", buyerName, "lot", reserved));
            if (credit.failed()) {
                refundBuyer(buyer, total);
                returnStock(f.id(), s, n);
                return Result.fail(Messages.get(ShopText.BANK_REFUSED));
            }
            List<ItemStack> left = giveSafely(store, ref, stacks);
            int leftQty = quantity(left);
            boolean mailed = false;
            double unrefunded = 0;
            MarketService market = ctx.serviceOrNull(MarketService.class);
            if (leftQty > 0 && market != null) {
                market.mail(buyer, left, 0);
                mailed = true;
                leftQty = 0;
            }
            if (leftQty > 0) {
                // Without a mailbox the items that did not fit go back into the shop and are paid back.
                double refund = round(s.price() * leftQty);
                if (economy().bankCharge(f, refund, Messages.get(ShopText.MEMO_REFUND_TO, "buyer", buyerName)).success()) {
                    refundBuyer(buyer, refund);
                    total = round(total - refund);
                } else {
                    // The bank could not pay it back, so the items stay in stock rather than vanish and the shortfall is logged.
                    unrefunded = refund;
                    ctx.logger().at(Level.WARNING).log("Dominion faction shop of %s could not refund %s to %s for %d items that did not fit",
                            f.tag(), fmt(refund), buyerName, leftQty);
                }
                returnStock(f.id(), s, leftQty);
            }
            int bought = n - leftQty;
            String owed = unrefunded > 0 ? " " + Messages.get(ShopText.OWED, "amount", fmt(unrefunded), "faction", f.tag()) : "";
            if (bought <= 0) {
                return Result.fail(Messages.get(ShopText.INVENTORY_FULL) + owed);
            }
            String what = lot(bought, s.itemId());
            traded(s, bought, total, buyer, f);
            if (s.stockedBy() != null && !s.stockedBy().equals(buyer)) {
                Msg.send(Players.online(s.stockedBy()), Msg.good(Messages.get(ShopText.SOLD_FROM_SHOP, "buyer", buyerName, "lot", what, "amount", fmt(total))));
            }
            return Result.pass(Messages.get(ShopText.BOUGHT_FROM, "lot", what, "faction", f.tag(), "amount", fmt(total))
                    + (mailed ? " " + Messages.get(ShopText.REST_MAILED) : "") + owed);
        });
        return await(p, fut, Messages.get(ShopText.BUYING));
    }

    /**
     * Puts reserved items back into a faction shop entry, or back as a new entry when it was emptied
     * meanwhile. When the faction is gone the items go to whoever stocked them through the mailbox.
     */
    private void returnStock(UUID factionId, Stock s, int n) {
        if (n <= 0) {
            return;
        }
        if (ctx.repo().faction(factionId) == null) {
            MarketService market = ctx.serviceOrNull(MarketService.class);
            List<ItemStack> stacks = Items.split(s.item(), s.itemId(), n);
            if (market != null && s.stockedBy() != null && Items.quantity(stacks) == n) {
                market.mail(s.stockedBy(), stacks, 0);
                return;
            }
            ctx.logger().at(Level.SEVERE).log("Dominion could not return %d %s to the shop of a disbanded faction %s", n, s.itemId(), factionId);
            return;
        }
        synchronized (lock) {
            List<Stock> list = stockList(factionId);
            int index = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(s.id())) {
                    index = i;
                }
            }
            if (index >= 0) {
                list.set(index, list.get(index).withQuantity(list.get(index).quantity() + n));
            } else {
                list.add(s.withQuantity(n));
            }
            saveFaction(factionId);
        }
    }

    /** Pays a buyer back, keeping the coins in their mailbox when the economy refuses. */
    private void refundBuyer(UUID buyer, double amount) {
        if (amount <= 0 || economy().deposit(buyer, amount, Messages.get(ShopText.MEMO_FACTION_REFUND)).success()) {
            return;
        }
        MarketService market = ctx.serviceOrNull(MarketService.class);
        if (market != null) {
            market.mail(buyer, List.of(), amount);
            return;
        }
        ctx.logger().at(Level.SEVERE).log("Dominion could not refund %s to %s from a faction shop", fmt(amount), buyer);
    }

    /**
     * The sell price of one item after the outpost boost. The boost never lifts it above the price
     * the shop charges for the same item, so buying and selling back can never turn a profit. The
     * cap is the lowest buy price the item has in any category, which includes this entry.
     */
    private double boostedUnitSell(AdminEntry e, double boost) {
        double cap = e.buy() > 0 ? e.buy() : 0;
        synchronized (lock) {
            for (Category c : catalogue.values()) {
                AdminEntry other = c.entries.get(key(e.itemId()));
                if (other != null && other.buy() > 0) {
                    cap = cap > 0 ? Math.min(cap, other.buy()) : other.buy();
                }
            }
        }
        return ShopRules.unitSell(e.sell(), boost, cap);
    }

    /** The outpost sell boost of the seller's faction, 1 when there is none or the events module is absent. */
    private double sellBoost(UUID seller) {
        EventsService events = ctx.serviceOrNull(EventsService.class);
        Faction f = factions().factionOf(seller);
        if (events == null || f == null) {
            return 1.0;
        }
        try {
            return ShopRules.boost(events.boostFor(f, "sell"));
        } catch (RuntimeException ex) {
            return 1.0;
        }
    }

    private static int quantity(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getQuantity).sum();
    }

    /**
     * Gives stacks one at a time and returns what did not reach the player. A stack whose give
     * throws counts as not given, so the caller refunds or mails it instead of losing it. World thread only.
     */
    private List<ItemStack> giveSafely(com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                                       com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                                       List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            try {
                left.addAll(Items.give(store, ref, List.of(stack)));
            } catch (RuntimeException ex) {
                ctx.logger().at(Level.WARNING).withCause(ex).log("Dominion shop could not give %s", stack.getItemId());
                left.add(stack);
            }
        }
        return left;
    }

    private void traded(Stock s, int qty, double total, UUID buyer, Faction shop) {
        try {
            ctx.bus().publish(new DominionEvent.MarketTrade(s.id(), s.itemId(), qty, total, buyer, s.stockedBy()));
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop trade event failed");
        }
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        Faction own = factions().factionOf(buyer);
        // Buying from your own faction's shop, repeats and cheap trades do not count toward missions.
        if (progress != null && missions.counts(buyer, own == null ? null : own.id(), s.stockedBy(), shop.id(), total, Time.now())) {
            try {
                progress.advance(buyer, "trade", 1);
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion progress update for a shop trade failed");
            }
        }
    }

    /** Index of the entry whose id or id prefix matches, or -1. Caller holds the lock. */
    static int find(List<Stock> list, String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return -1;
        }
        String q = entryId.trim().toLowerCase(Locale.ROOT);
        int match = -1;
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i).id().toString();
            if (id.equals(q)) {
                return i;
            }
            if (q.length() >= 4 && id.startsWith(q)) {
                if (match >= 0) {
                    return -1;
                }
                match = i;
            }
        }
        return match;
    }

    /** The live stock list of a faction, loading it from disk on first use. Caller holds the lock. */
    private List<Stock> stockList(UUID factionId) {
        return factionShops.computeIfAbsent(factionId, id -> {
            List<Stock> out = new ArrayList<>();
            Document d = ctx.files().read(factionFile(id));
            if (d != null && d.get("Entries") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Document sd) {
                        try {
                            Stock s = Stock.fromDocument(sd);
                            if (s.quantity() > 0) {
                                out.add(s);
                            }
                        } catch (RuntimeException e) {
                            ctx.logger().at(Level.WARNING).log("Skipping unreadable faction shop entry for %s", id);
                        }
                    }
                }
            }
            return out;
        });
    }

    // ---- pages ---------------------------------------------------------------------------

    @Override
    public void openAdminShop(PlayerRef player, String category) {
        if (player == null) {
            return;
        }
        if (!adminEnabled()) {
            Msg.send(player, Msg.bad(Messages.get(ShopText.ADMIN_CLOSED)));
            return;
        }
        String cat = categoryName(category);
        if (category != null && !category.isBlank() && cat == null) {
            Msg.send(player, Msg.warn(Messages.get(ShopText.NO_SUCH_SHOP_CATEGORY, "category", category)));
        }
        if (cat == null) {
            List<String> all = adminCategories();
            cat = all.isEmpty() ? null : all.get(0);
        }
        open(player, new AdminShopPage(player, ctx, this, cat));
    }

    @Override
    public void openAdminEditor(PlayerRef player) {
        if (player == null) {
            return;
        }
        List<String> all = adminCategories();
        open(player, new AdminEditorPage(player, ctx, this, all.isEmpty() ? null : all.get(0)));
    }

    @Override
    public void openFactionShop(PlayerRef player, Faction faction) {
        if (player == null) {
            return;
        }
        if (faction == null) {
            Msg.send(player, Msg.bad(Messages.get(CommonText.NO_SUCH_FACTION)));
            return;
        }
        if (!factionEnabled()) {
            Msg.send(player, Msg.bad(Messages.get(ShopText.FACTION_SHOPS_OFF)));
            return;
        }
        open(player, new FactionShopPage(player, ctx, this, faction.id()));
    }

    @Override
    public void openFactionShopManager(PlayerRef player) {
        if (player == null) {
            return;
        }
        Faction f = factions().factionOf(player.getUuid());
        if (f == null) {
            Msg.send(player, Msg.bad(Messages.get(CommonText.NOT_IN_FACTION)));
            return;
        }
        if (!isOfficer(player.getUuid(), f)) {
            Msg.send(player, Msg.bad(Messages.get(ShopText.MANAGE_OFFICERS)));
            return;
        }
        open(player, new FactionManagerPage(player, ctx, this, f.id()));
    }

    /** Opens a page on the player's world thread. */
    void open(PlayerRef player, ShopPage page) {
        Items.onWorld(player, (store, ref) -> {
            Player pc = store.getComponent(ref, Player.getComponentType());
            if (pc == null) {
                return false;
            }
            pc.getPageManager().openCustomPage(ref, store, page);
            return true;
        }).whenComplete((ok, err) -> {
            if (err != null) {
                ctx.logger().at(Level.WARNING).withCause(err).log("Dominion shop page failed to open");
            }
            if (err != null || !Boolean.TRUE.equals(ok)) {
                Msg.send(player, Msg.bad(Messages.get(ShopText.OPEN_FAILED)));
            }
        });
    }

    // ---- internals -----------------------------------------------------------------------

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
            return r == null ? Result.fail(Messages.get(ShopText.NEED_WORLD)) : r;
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop action failed");
            return Result.fail(Messages.get(ShopText.SOMETHING_WRONG));
        }
    }

    /** Caller holds the lock. */
    private void saveAdmin() {
        List<Document> cats = new ArrayList<>();
        for (Category c : catalogue.values()) {
            List<Document> entries = new ArrayList<>();
            for (AdminEntry e : c.entries.values()) {
                entries.add(new Document("ItemId", e.itemId()).append("Buy", e.buy()).append("Sell", e.sell()));
            }
            cats.add(new Document("Name", c.name).append("Entries", entries));
        }
        ctx.files().write(adminFile, new Document("Categories", cats));
    }

    /** Caller holds the lock. */
    private void saveFaction(UUID factionId) {
        List<Stock> list = factionShops.get(factionId);
        if (list == null) {
            return;
        }
        if (list.isEmpty()) {
            ctx.files().delete(factionFile(factionId));
            return;
        }
        List<Document> docs = new ArrayList<>();
        for (Stock s : list) {
            docs.add(s.toDocument());
        }
        ctx.files().write(factionFile(factionId), new Document("Entries", docs));
    }

    private Path factionFile(UUID factionId) {
        return dir.resolve(factionId + ".json");
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    String priceWords(double buy, double sell) {
        if (buy > 0 && sell > 0) {
            return Messages.get(ShopText.PRICES_BOTH, "buy", fmt(buy), "sell", fmt(sell));
        }
        return buy > 0 ? Messages.get(ShopText.PRICES_BUY_ONLY, "buy", fmt(buy)) : Messages.get(ShopText.PRICES_SELL_ONLY, "sell", fmt(sell));
    }

    private static String lot(int count, String itemId) {
        return Messages.get(ShopText.LOT, "count", count, "item", Names.of(itemId));
    }

    String name(UUID player) {
        PlayerRef p = Players.online(player);
        if (p != null && p.getUsername() != null) {
            return p.getUsername();
        }
        String n = ctx.player(player).name();
        return n == null || n.isBlank() ? Messages.get(ShopText.SOMEONE) : n;
    }

    String fmt(double amount) {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco == null ? Msg.number(amount) : eco.format(amount);
    }

    private EconomyService economy() {
        return ctx.service(EconomyService.class);
    }

    private FactionService factions() {
        return ctx.service(FactionService.class);
    }

    private boolean wholeOnly() {
        EconomyService eco = ctx.serviceOrNull(EconomyService.class);
        return eco != null && eco.wholeUnits();
    }

    /** A readable problem with a price, or null. Zero is allowed when allowZero is set. */
    private String priceProblem(double price, boolean allowZero) {
        return ShopRules.priceProblem(price, allowZero, wholeOnly(), MAX_PRICE);
    }

    static double round(double amount) {
        return ShopRules.round(amount);
    }
}
