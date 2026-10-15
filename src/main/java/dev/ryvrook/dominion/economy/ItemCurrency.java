package dev.ryvrook.dominion.economy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.api.Result;
import dev.ryvrook.dominion.text.Messages;
import dev.ryvrook.dominion.text.Msg;
import dev.ryvrook.dominion.util.Players;
import org.bson.Document;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * A wallet made of real items. The balance is the number of eco.itemId items across the hotbar,
 * storage and backpack. Paying removes items and receiving adds them, always on the world thread
 * of the player. Money owed to a player who is offline or has a full inventory is kept in a small
 * file and handed out on the next login or the next payment, so nothing is lost. Fractions of an
 * item are kept the same way until they add up to a whole item.
 */
final class ItemCurrency implements BalanceProvider {
    private static final long WAIT_MILLIS = 2000;
    private static final double EPSILON = 1e-6;

    private final DominionContext ctx;
    private final DoubleFunction<String> format;
    private final Map<UUID, Integer> lastKnown = new ConcurrentHashMap<>();
    private final Map<UUID, Double> owed = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    ItemCurrency(DominionContext ctx, DoubleFunction<String> format) {
        this.ctx = ctx;
        this.format = format;
    }

    @Override
    public String name() {
        return "item";
    }

    @Override
    public boolean wholeUnits() {
        return true;
    }

    private String itemId() {
        return ctx.settings().text("eco.itemId");
    }

    // ---- balance ----------------------------------------------------------

    @Override
    public double balance(UUID player) {
        PlayerRef ref = Players.online(player);
        if (ref == null) {
            return lastKnown.getOrDefault(player, 0);
        }
        String id = itemId();
        Integer n = call(ref, c -> count(c, id), null);
        if (n == null) {
            return lastKnown.getOrDefault(player, 0);
        }
        lastKnown.put(player, n);
        return n;
    }

    @Override
    public double cachedBalance(UUID player) {
        return lastKnown.getOrDefault(player, 0);
    }

    private static int count(ItemContainer c, String id) {
        int n = 0;
        short cap = c.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack s = c.getItemStack(i);
            if (!ItemStack.isEmpty(s) && id.equals(s.getItemId())) {
                n += s.getQuantity();
            }
        }
        return n;
    }

    // ---- paying -----------------------------------------------------------

    /** Whole items a payment takes, rounding a fraction up. */
    static int itemsFor(double amount) {
        if (!(amount > EPSILON)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(amount - EPSILON));
    }

    /** Whole items ready to hand out from an owed amount. */
    static int wholeOwed(double owed) {
        return owed > 0 ? (int) Math.min(Integer.MAX_VALUE, Math.floor(owed + EPSILON)) : 0;
    }

    @Override
    public Result withdraw(UUID player, double amount, String reason) {
        int need = itemsFor(amount);
        if (need <= 0) {
            return Result.pass();
        }
        PlayerRef ref = Players.online(player);
        if (ref == null) {
            return Result.fail(Messages.get(EconomyText.ITEMS_ONLINE));
        }
        String id = itemId();
        int[] have = { -1 };
        Boolean ok = call(ref, c -> {
            int n = count(c, id);
            have[0] = n;
            if (n < need) {
                return false;
            }
            ItemStackTransaction tx = c.removeItemStack(new ItemStack(id, need), true, true);
            boolean done = tx != null && tx.succeeded();
            have[0] = done ? n - need : n;
            return done;
        }, null);
        if (have[0] >= 0) {
            lastKnown.put(player, have[0]);
        }
        if (ok == null) {
            return Result.fail(Messages.get(EconomyText.INVENTORY_UNREADABLE));
        }
        if (!ok) {
            return Result.fail(Messages.get(EconomyText.NEED_CARRY, "amount", format.apply(need), "have", format.apply(Math.max(0, have[0]))));
        }
        double change = need - amount;
        if (change > EPSILON) {
            // A fraction was rounded up to a whole item, so the difference is owed back and paid with the next whole item.
            ensureLoaded();
            synchronized (owed) {
                owed.merge(player, change, Double::sum);
            }
            save();
        }
        return Result.pass();
    }

    @Override
    public Result deposit(UUID player, double amount, String reason) {
        if (amount <= 0) {
            return Result.pass();
        }
        ensureLoaded();
        synchronized (owed) {
            owed.merge(player, amount, Double::sum);
        }
        save();
        deliver(player);
        return Result.pass();
    }

    @Override
    public void set(UUID player, double amount) {
        double target = Math.max(0, Math.floor(amount + EPSILON));
        double current = balance(player);
        if (target > current) {
            deposit(player, target - current, Messages.get(EconomyText.REASON_ADMIN_SET));
        } else if (target < current && Players.isOnline(player)) {
            withdraw(player, current - target, Messages.get(EconomyText.REASON_ADMIN_SET));
        }
    }

    @Override
    public void onReady(UUID player) {
        ensureLoaded();
        if (owed.getOrDefault(player, 0.0) >= 1 - EPSILON) {
            deliver(player);
        }
    }

    /** Hands out the whole items owed to an online player. Never blocks the caller. */
    private void deliver(UUID player) {
        PlayerRef ref = Players.online(player);
        World world = Players.world(ref);
        if (ref == null || world == null) {
            return;
        }
        String id = itemId();
        world.execute(() -> {
            // The whole items are reserved under the monitor first, so two deliveries can never hand out the same items.
            int whole;
            synchronized (owed) {
                double before = owed.getOrDefault(player, 0.0);
                whole = wholeOwed(before);
                if (whole <= 0) {
                    return;
                }
                settle(player, before - whole);
            }
            int given = 0;
            int left = whole;
            ItemContainer c = null;
            try {
                Ref<EntityStore> r = ref.getReference();
                if (r != null && r.isValid()) {
                    c = InventoryComponent.getCombined(r.getStore(), r, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
                }
                if (c != null) {
                    ItemStackTransaction tx = c.addItemStack(new ItemStack(id, whole), false, false, true);
                    ItemStack rest = tx == null ? null : tx.getRemainder();
                    left = ItemStack.isEmpty(rest) ? 0 : rest.getQuantity();
                    if (tx == null || (!tx.succeeded() && left == 0)) {
                        left = whole;
                    }
                    given = whole - left;
                }
            } finally {
                if (left > 0) {
                    synchronized (owed) {
                        settle(player, owed.getOrDefault(player, 0.0) + left);
                    }
                }
                if (given > 0) {
                    save();
                }
            }
            if (given > 0 && c != null) {
                lastKnown.put(player, count(c, id));
            }
            if (left > 0 && c != null) {
                Msg.send(ref, Msg.warn(Messages.get(EconomyText.INVENTORY_FULL, "amount", format.apply(left))));
            }
        });
    }

    /** Stores what a player is owed, dropping the entry once nothing is left. Caller holds the owed monitor. */
    private void settle(UUID player, double amount) {
        if (amount <= EPSILON) {
            owed.remove(player);
        } else {
            owed.put(player, amount);
        }
    }

    // ---- world thread calls -----------------------------------------------

    /**
     * Runs the action on the player's world thread with the combined hotbar, storage and backpack
     * container. Waits up to two seconds when called from another thread. A task that has not
     * started by then is abandoned so a late withdrawal can never happen after the caller gave up.
     */
    private <T> T call(PlayerRef player, Function<ItemContainer, T> action, T fallback) {
        World world = Players.world(player);
        if (world == null) {
            return fallback;
        }
        if (world.isInThread()) {
            return run(player, action, fallback);
        }
        AtomicInteger state = new AtomicInteger();
        CompletableFuture<T> future = new CompletableFuture<>();
        world.execute(() -> {
            if (!state.compareAndSet(0, 1)) {
                return;
            }
            try {
                future.complete(run(player, action, fallback));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (state.compareAndSet(0, 2)) {
                return fallback;
            }
            return await(future, fallback);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            ctx.logger().at(Level.WARNING).withCause(e.getCause()).log("Dominion item currency failed");
            return fallback;
        }
    }

    private <T> T await(CompletableFuture<T> future, T fallback) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            return fallback;
        }
    }

    private static <T> T run(PlayerRef player, Function<ItemContainer, T> action, T fallback) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return fallback;
        }
        ItemContainer c = InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        return c == null ? fallback : action.apply(c);
    }

    // ---- owed storage -----------------------------------------------------

    private Path file() {
        return ctx.repo().root().resolve("economy-items-owed.json");
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (owed) {
            if (loaded) {
                return;
            }
            Document d = ctx.files().read(file());
            if (d != null) {
                d.forEach((k, v) -> {
                    if (v instanceof Number n) {
                        try {
                            owed.merge(UUID.fromString(k), n.doubleValue(), Double::sum);
                        } catch (IllegalArgumentException ignored) {
                            // not a player id
                        }
                    }
                });
            }
            loaded = true;
        }
    }

    private void save() {
        Document d = new Document();
        synchronized (owed) {
            owed.forEach((k, v) -> d.append(k.toString(), v));
            ctx.files().write(file(), d);
        }
    }
}
