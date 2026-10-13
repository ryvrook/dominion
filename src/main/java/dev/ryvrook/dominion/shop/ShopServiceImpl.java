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
            if (market != null && s.stockedBy() != null) {
                market.mail(s.stockedBy(), Items.split(s.item(), s.itemId(), s.quantity()), 0);
                Msg.send(Players.online(s.stockedBy()), Msg.info("The " + Names.of(s.itemId()) + " you stocked in the shop of "
                        + gone.tag() + " went to your market mailbox."));
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
            return Result.fail("Category names use letters, numbers, spaces, dashes or underscores and up to "
                    + MAX_CATEGORY_LENGTH + " characters.");
        }
        if (itemId == null || itemId.isBlank()) {
            return Result.fail("Give the id of the item to list.");
        }
        if (!Items.exists(itemId.trim())) {
            return Result.fail("There is no item called " + itemId.trim() + ".");
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
            return Result.fail("Give the item a buy price, a sell price or both.");
        }
        if (buy > 0 && sell > buy) {
            return Result.fail("The sell price cannot be higher than the buy price, or players could buy and sell for profit.");
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
        return Result.pass((existed ? "Updated " : "Added ") + Names.of(id) + " in " + catName + ". " + priceWords(buy, sell));
    }

    @Override
    public Result adminRemove(String category, String itemId) {
        if (category == null || itemId == null) {
            return Result.fail("Give a category and an item id.");
        }
        synchronized (lock) {
            Category c = catalogue.get(key(category));
            if (c == null) {
                return Result.fail("There is no category called " + category + ".");
            }
            AdminEntry removed = c.entries.remove(key(itemId.trim()));
            if (removed == null) {
                return Result.fail(itemId + " is not listed in " + c.name + ".");
            }
            boolean dropped = c.entries.isEmpty();
            if (dropped) {
                catalogue.remove(key(c.name));
            }
            saveAdmin();
            return Result.pass("Removed " + Names.of(removed.itemId()) + " from " + c.name + "."
                    + (dropped ? " The category was empty so it was removed too." : ""));
        }
    }

    Result removeCategory(String category) {
        synchronized (lock) {
            Category c = category == null ? null : catalogue.remove(key(category));
            if (c == null) {
                return Result.fail("There is no category called " + category + ".");
            }
            saveAdmin();
            return Result.pass("Removed the category " + c.name + " and its " + c.entries.size()
                    + (c.entries.size() == 1 ? " entry." : " entries."));
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
            return Result.fail(missing > 0 && missing == seed.length ? "None of the starter items exist on this server."
                    : "The starter catalogue is already in place.");
        }
        return Result.pass("Added " + added + " starter entries." + (missing > 0 ? " " + missing + " items do not exist on this server and were skipped." : ""));
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
            return Result.fail("The admin shop is closed.");
        }
        if (qty < 1 || qty > MAX_QUANTITY) {
            return Result.fail("Buy between 1 and " + MAX_QUANTITY + " at a time.");
        }
        AdminEntry e = entry(category, itemId);
        if (e == null || e.buy() <= 0) {
            return Result.fail("That item is not for sale.");
        }
        if (!Items.exists(e.itemId())) {
            return Result.fail("That item no longer exists on this server.");
        }
        PlayerRef p = Players.online(buyer);
        if (p == null) {
            return Result.fail("You need to be online to buy from the shop.");
        }
        double total = round(e.buy() * qty);
        String what = qty + " " + Names.of(e.itemId());
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            List<ItemStack> stacks = Items.split(null, e.itemId(), qty);
            if (quantity(stacks) != qty) {
                return Result.fail("That item could not be made right now.");
            }
            Result paid = economy().withdraw(buyer, total, "Admin shop purchase of " + what);
            if (paid.failed()) {
                return Result.fail(paid.message());
            }
            List<ItemStack> left = giveSafely(store, ref, stacks);
            String extra = "";
            if (!left.isEmpty()) {
                MarketService market = ctx.serviceOrNull(MarketService.class);
                if (market != null) {
                    market.mail(buyer, left, 0);
                    extra = " Your inventory was full so the rest went to your market mailbox.";
                } else {
                    int n = left.stream().mapToInt(ItemStack::getQuantity).sum();
                    double refund = round(e.buy() * n);
                    economy().deposit(buyer, refund, "Admin shop refund");
                    extra = " Your inventory was full so " + n + " were refunded.";
                }
            }
            return Result.pass("You bought " + what + " for " + fmt(total) + "." + extra);
        });
        return await(p, f, "Buying " + what + ".");
    }

    /** Sells up to qty of an item from the player's inventory at the admin sell price. */
    Result sellAdmin(UUID seller, String category, String itemId, int qty) {
        if (!adminEnabled()) {
            return Result.fail("The admin shop is closed.");
        }
        if (qty < 1 || qty > MAX_QUANTITY) {
            return Result.fail("Sell between 1 and " + MAX_QUANTITY + " at a time.");
        }
        AdminEntry e = entry(category, itemId);
        if (e == null || e.sell() <= 0) {
            return Result.fail("The shop does not buy that item.");
        }
        PlayerRef p = Players.online(seller);
        if (p == null) {
            return Result.fail("You need to be online to sell to the shop.");
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            ItemContainer inv = Items.inventory(store, ref);
            int have = inv == null ? 0 : Items.count(inv, e.itemId());
            if (have <= 0) {
                return Result.fail("You have no " + Names.of(e.itemId()) + " to sell.");
            }
            List<ItemStack> taken = Items.take(inv, e.itemId(), Math.min(qty, have));
            return payForSale(seller, store, ref, taken, e);
        });
        return await(p, f, "Selling.");
    }

    /** Sells the stack in the player's hand at the admin sell price. */
    Result sellHeld(UUID seller) {
        if (!adminEnabled()) {
            return Result.fail("The admin shop is closed.");
        }
        PlayerRef p = Players.online(seller);
        if (p == null) {
            return Result.fail("You need to be online to sell to the shop.");
        }
        CompletableFuture<Result> f = Items.onWorld(p, (store, ref) -> {
            Items.Held held = Items.held(store, ref);
            if (held == null) {
                return Result.fail("Hold the item you want to sell in your hand.");
            }
            if (held.stack().isBroken()) {
                return Result.fail("The shop does not buy broken items.");
            }
            AdminEntry e = sellableEntry(held.stack().getItemId());
            if (e == null) {
                return Result.fail("The shop does not buy " + Names.of(held.stack().getItemId()) + ".");
            }
            ItemStack taken = Items.takeHeld(held, 0);
            if (taken == null) {
                return Result.fail("That item could not be taken from your hand.");
            }
            return payForSale(seller, store, ref, List.of(taken), e);
        });
        return await(p, f, "Selling.");
    }

    /** Pays for taken items, giving them back when the payment fails. World thread only. */
    private Result payForSale(UUID seller, com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                              com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                              List<ItemStack> taken, AdminEntry e) {
        int got = taken.stream().mapToInt(ItemStack::getQuantity).sum();
        if (got <= 0) {
            return Result.fail("Those items could not be taken from your inventory.");
        }
        double total = round(boostedUnitSell(e, sellBoost(seller)) * got);
        if (wholeOnly()) {
            // An item currency pays whole units, so a boosted fraction is dropped.
            total = Math.floor(total + 1e-9);
        }
        Result r;
        try {
            r = economy().deposit(seller, total, "Admin shop sale of " + got + " " + Names.of(e.itemId()));
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
            return Result.fail(r.message().isBlank() ? "You could not be paid so your items were returned." : r.message());
        }
        return Result.pass("You sold " + got + " " + Names.of(e.itemId()) + " for " + fmt(total) + ".");
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
            return Result.fail("Faction shops are turned off on this server.");
        }
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!isOfficer(actor, f)) {
            return Result.fail("Only officers and the leader can stock the faction shop.");
        }
        if (!factions().can(actor, FactionAction.VAULT)) {
            return Result.fail("Your rank cannot use the faction vault.");
        }
        dev.ryvrook.dominion.model.Member member = f.member(actor);
        if (member != null && member.onHold(Time.now())) {
            return Result.fail("New members cannot use the vault yet. Try again in " + Time.remaining(member.holdUntil() - Time.now()) + ".");
        }
        VaultService vault = ctx.serviceOrNull(VaultService.class);
        if (vault == null) {
            return Result.fail("The faction vault is not available on this server.");
        }
        int pages = vault.pages(f);
        if (vaultPage < 1 || vaultPage > pages) {
            return Result.fail(pages == 1 ? "Your vault has one page." : "Your vault has pages 1 to " + pages + ".");
        }
        if (slot < 1) {
            return Result.fail("Pick a vault slot from 1 upwards.");
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
                return Result.fail("Your faction shop already holds " + list.size() + " entries. The limit is " + max + ".");
            }
            ItemStack peek = vault.peekSlot(f, vaultPage, slot);
            if (peek == null || ItemStack.isEmpty(peek)) {
                return Result.fail("Vault page " + vaultPage + " slot " + slot + " is empty.");
            }
            if (expectedItemId != null && !expectedItemId.equals(peek.getItemId())) {
                return Result.fail("Vault page " + vaultPage + " slot " + slot + " changed. Check the list and try again.");
            }
            if (peek.isBroken()) {
                return Result.fail("Broken items cannot be stocked.");
            }
            ItemStack taken = vault.takeSlot(f, vaultPage, slot);
            if (taken == null || ItemStack.isEmpty(taken)) {
                return Result.fail("That vault slot could not be taken. The vault may be sealed by a breach.");
            }
            s = new Stock(UUID.randomUUID(), taken.getItemId(), Items.toDocument(taken), taken.getQuantity(), each,
                    actor, actorName, Time.now());
            list.add(s);
            saveFaction(f.id());
        }
        String what = s.quantity() + " " + Names.of(s.itemId());
        factions().audit(f, actor, actorName, "shop", "Stocked " + what + " from vault page " + vaultPage + " slot " + slot
                + " at " + fmt(each) + " each");
        ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
        return Result.pass("Stocked " + what + " from the vault at " + fmt(each) + " each.");
    }

    @Override
    public Result factionStockHeld(UUID actor, double price) {
        if (!factionEnabled()) {
            return Result.fail("Faction shops are turned off on this server.");
        }
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!isOfficer(actor, f)) {
            return Result.fail("Only officers and the leader can stock the faction shop.");
        }
        String bad = priceProblem(price, false);
        if (bad != null) {
            return Result.fail(bad);
        }
        double each = round(price);
        PlayerRef p = Players.online(actor);
        if (p == null) {
            return Result.fail("You need to be online to stock the shop.");
        }
        String actorName = name(actor);
        CompletableFuture<Result> fut = Items.onWorld(p, (store, ref) -> {
            Items.Held held = Items.held(store, ref);
            if (held == null) {
                return Result.fail("Hold the item you want to stock in your hand.");
            }
            if (held.stack().isBroken()) {
                return Result.fail("Broken items cannot be stocked.");
            }
            Stock s;
            synchronized (lock) {
                List<Stock> list = stockList(f.id());
                int max = maxFactionEntries();
                if (list.size() >= max) {
                    return Result.fail("Your faction shop already holds " + list.size() + " entries. The limit is " + max + ".");
                }
                ItemStack taken = Items.takeHeld(held, 0);
                if (taken == null) {
                    return Result.fail("That item could not be taken from your hand.");
                }
                s = new Stock(UUID.randomUUID(), taken.getItemId(), Items.toDocument(taken), taken.getQuantity(), each,
                        actor, actorName, Time.now());
                list.add(s);
                saveFaction(f.id());
            }
            String what = s.quantity() + " " + Names.of(s.itemId());
            factions().audit(f, actor, actorName, "shop", "Stocked " + what + " at " + fmt(each) + " each");
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            return Result.pass("Stocked " + what + " in the faction shop at " + fmt(each) + " each.");
        });
        return await(p, fut, "Stocking the shop.");
    }

    @Override
    public Result factionUnstock(UUID actor, String entryId) {
        Faction f = factions().factionOf(actor);
        if (f == null) {
            return Result.fail("You are not in a faction.");
        }
        if (!isOfficer(actor, f)) {
            return Result.fail("Only officers and the leader can take items out of the faction shop.");
        }
        PlayerRef p = Players.online(actor);
        if (p == null) {
            return Result.fail("You need to be online to take items out of the shop.");
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
                    return Result.fail("That entry is no longer in the shop.");
                }
                s = list.get(index);
                List<ItemStack> left = giveSafely(store, ref, Items.split(s.item(), s.itemId(), s.quantity()));
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
                return Result.fail("Your inventory is full. Make some room and try again.");
            }
            String what = returned + " " + Names.of(s.itemId());
            factions().audit(f, actor, actorName, "shop", "Took " + what + " out of the faction shop");
            ctx.bus().publish(new DominionEvent.FactionChanged(f.id()));
            return Result.pass("Took " + what + " out of the faction shop."
                    + (mailed ? " Some did not fit and went to your market mailbox." : ""));
        });
        return await(p, fut, "Taking the items back.");
    }

    Result buyStock(UUID buyer, UUID factionId, String entryId, int qty) {
        if (!factionEnabled()) {
            return Result.fail("Faction shops are turned off on this server.");
        }
        if (qty < 1 || qty > MAX_QUANTITY) {
            return Result.fail("Buy between 1 and " + MAX_QUANTITY + " at a time.");
        }
        Faction f = factions().byId(factionId);
        if (f == null) {
            return Result.fail("That faction no longer exists.");
        }
        PlayerRef p = Players.online(buyer);
        if (p == null) {
            return Result.fail("You need to be online to buy from a shop.");
        }
        String buyerName = name(buyer);
        CompletableFuture<Result> fut = Items.onWorld(p, (store, ref) -> {
            Stock s;
            int bought;
            double total;
            boolean mailed = false;
            synchronized (lock) {
                List<Stock> list = stockList(f.id());
                int index = find(list, entryId);
                if (index < 0) {
                    return Result.fail("That item is no longer in stock.");
                }
                s = list.get(index);
                int n = Math.min(qty, s.quantity());
                total = round(s.price() * n);
                String what = n + " " + Names.of(s.itemId());
                List<ItemStack> stacks = Items.split(s.item(), s.itemId(), n);
                if (quantity(stacks) != n) {
                    return Result.fail("That item could not be made right now.");
                }
                Result paid = economy().withdraw(buyer, total, "Bought " + what + " from the shop of " + f.tag());
                if (paid.failed()) {
                    return Result.fail(paid.message());
                }
                Result credit = economy().bankCredit(f, total, buyerName + " bought " + what + " from the faction shop");
                if (credit.failed()) {
                    economy().deposit(buyer, total, "Faction shop refund");
                    return Result.fail("The faction bank could not take the payment so nothing was bought.");
                }
                List<ItemStack> left = giveSafely(store, ref, stacks);
                int leftQty = left.stream().mapToInt(ItemStack::getQuantity).sum();
                MarketService market = ctx.serviceOrNull(MarketService.class);
                if (leftQty > 0 && market != null) {
                    market.mail(buyer, left, 0);
                    mailed = true;
                    leftQty = 0;
                }
                if (leftQty > 0) {
                    // Without a mailbox the items that did not fit stay in the shop and are paid back.
                    double refund = round(s.price() * leftQty);
                    if (economy().bankCharge(f, refund, "Faction shop refund to " + buyerName).success()) {
                        economy().deposit(buyer, refund, "Faction shop refund");
                        total = round(total - refund);
                    } else {
                        leftQty = 0;
                    }
                }
                bought = n - leftQty;
                int remaining = s.quantity() - bought;
                if (remaining > 0) {
                    list.set(index, s.withQuantity(remaining));
                } else {
                    list.remove(index);
                }
                saveFaction(f.id());
            }
            if (bought <= 0) {
                return Result.fail("Your inventory is full. Make some room and try again.");
            }
            String what = bought + " " + Names.of(s.itemId());
            traded(s, bought, total, buyer);
            if (s.stockedBy() != null && !s.stockedBy().equals(buyer)) {
                Msg.send(Players.online(s.stockedBy()), Msg.good(buyerName + " bought " + what + " from your faction shop for " + fmt(total) + "."));
            }
            return Result.pass("You bought " + what + " from " + f.tag() + " for " + fmt(total) + "."
                    + (mailed ? " Your inventory was full so the rest went to your market mailbox." : ""));
        });
        return await(p, fut, "Buying.");
    }

    /**
     * The sell price of one item after the outpost boost. The boost never lifts it above the price
     * the shop charges for the same item, so buying and selling back can never turn a profit. The
     * cap is the lowest buy price the item has in any category, which includes this entry.
     */
    private double boostedUnitSell(AdminEntry e, double boost) {
        double unit = e.sell() * boost;
        double cap = e.buy() > 0 ? e.buy() : Double.MAX_VALUE;
        synchronized (lock) {
            for (Category c : catalogue.values()) {
                AdminEntry other = c.entries.get(key(e.itemId()));
                if (other != null && other.buy() > 0) {
                    cap = Math.min(cap, other.buy());
                }
            }
        }
        return Math.min(unit, cap);
    }

    /** The outpost sell boost of the seller's faction, 1 when there is none or the events module is absent. */
    private double sellBoost(UUID seller) {
        EventsService events = ctx.serviceOrNull(EventsService.class);
        Faction f = factions().factionOf(seller);
        if (events == null || f == null) {
            return 1.0;
        }
        try {
            double b = events.boostFor(f, "sell");
            return Double.isFinite(b) && b > 0 ? b : 1.0;
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

    private void traded(Stock s, int qty, double total, UUID buyer) {
        try {
            ctx.bus().publish(new DominionEvent.MarketTrade(s.id(), s.itemId(), qty, total, buyer, s.stockedBy()));
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop trade event failed");
        }
        ProgressService progress = ctx.serviceOrNull(ProgressService.class);
        if (progress != null) {
            try {
                progress.advance(buyer, "trade", 1);
            } catch (RuntimeException e) {
                ctx.logger().at(Level.WARNING).withCause(e).log("Dominion progress update for a shop trade failed");
            }
        }
    }

    /** Index of the entry whose id or id prefix matches, or -1. Caller holds the lock. */
    private static int find(List<Stock> list, String entryId) {
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
            Msg.send(player, Msg.bad("The admin shop is closed."));
            return;
        }
        String cat = categoryName(category);
        if (category != null && !category.isBlank() && cat == null) {
            Msg.send(player, Msg.warn("There is no shop category called " + category + "."));
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
            Msg.send(player, Msg.bad("That faction does not exist."));
            return;
        }
        if (!factionEnabled()) {
            Msg.send(player, Msg.bad("Faction shops are turned off on this server."));
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
            Msg.send(player, Msg.bad("You are not in a faction."));
            return;
        }
        if (!isOfficer(player.getUuid(), f)) {
            Msg.send(player, Msg.bad("Only officers and the leader can manage the faction shop."));
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
                Msg.send(player, Msg.bad("The shop could not be opened right now."));
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
            return r == null ? Result.fail("You need to be in a world to do that.") : r;
        } catch (RuntimeException e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Dominion shop action failed");
            return Result.fail("Something went wrong and nothing was changed.");
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
            return "Players buy it for " + fmt(buy) + " and sell it for " + fmt(sell) + ".";
        }
        return buy > 0 ? "Players buy it for " + fmt(buy) + " and cannot sell it." : "Players sell it for " + fmt(sell) + " and cannot buy it.";
    }

    String name(UUID player) {
        PlayerRef p = Players.online(player);
        if (p != null && p.getUsername() != null) {
            return p.getUsername();
        }
        String n = ctx.player(player).name();
        return n == null || n.isBlank() ? "Someone" : n;
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
        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0 || (!allowZero && price <= 0)) {
            return allowZero ? "Prices cannot be negative." : "The price must be more than zero.";
        }
        if (price > MAX_PRICE) {
            return "That price is too high.";
        }
        if (price > 0 && round(price) <= 0) {
            return "The price must be at least 0.01.";
        }
        if (wholeOnly() && Math.abs(price - Math.rint(price)) > 1e-6) {
            return "Prices must be whole numbers because the currency is an item.";
        }
        return null;
    }

    static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
