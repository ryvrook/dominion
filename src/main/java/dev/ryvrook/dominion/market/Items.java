package dev.ryvrook.dominion.market;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.model.Listing;
import dev.ryvrook.dominion.util.Players;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Item stack helpers shared by the market and the vault. Everything that touches a Store or Ref
 * must be called on the player's world thread, which {@link #onWorld} arranges.
 */
public final class Items {
    private Items() {}

    /** The stack in the player's active hand together with the container and slot it lives in. */
    public record Held(ItemContainer container, short slot, ItemStack stack) {}

    /**
     * Runs work on the player's world thread with the entity store and ref. Runs inline when the
     * caller is already on that thread. Completes with null when the player is offline or not in a world.
     */
    public static <T> CompletableFuture<T> onWorld(PlayerRef player, BiFunction<Store<EntityStore>, Ref<EntityStore>, T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        World world = Players.world(player);
        if (world == null) {
            future.complete(null);
            return future;
        }
        Runnable task = () -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    future.complete(null);
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (ref.getStore() != store) {
                    // The player changed worlds after the task was queued, so this thread does not own the entity.
                    future.complete(null);
                    return;
                }
                future.complete(work.apply(store, ref));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        if (world.isInThread()) {
            task.run();
        } else {
            world.execute(task);
        }
        return future;
    }

    /** The item in hand, or null when the hand is empty. World thread only. */
    public static Held held(Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Tool tool = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        if (tool != null && tool.isUsingToolsItem()) {
            Held h = active(tool.getInventory(), tool.getActiveSlot());
            if (h != null) {
                return h;
            }
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        return hotbar == null ? null : active(hotbar.getInventory(), hotbar.getActiveSlot());
    }

    private static Held active(ItemContainer container, byte slot) {
        if (container == null || slot < 0 || slot >= container.getCapacity()) {
            return null;
        }
        ItemStack stack = container.getItemStack(slot);
        return ItemStack.isEmpty(stack) ? null : new Held(container, slot, stack);
    }

    /** Removes up to amount from the held slot and returns what was taken, or null. World thread only. */
    public static ItemStack takeHeld(Held held, int amount) {
        int take = amount <= 0 ? held.stack().getQuantity() : Math.min(amount, held.stack().getQuantity());
        ItemStackSlotTransaction tx = held.container().removeItemStackFromSlot(held.slot(), take);
        if (tx == null || !tx.succeeded()) {
            return null;
        }
        return held.stack().withQuantity(take);
    }

    /** The player's main inventory with the hotbar first. World thread only. */
    public static ItemContainer inventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        return InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
    }

    /** How many items with this id the player carries. World thread only. */
    public static int count(ItemContainer container, String itemId) {
        int[] total = {0};
        container.forEach((slot, stack) -> {
            if (!ItemStack.isEmpty(stack) && itemId.equals(stack.getItemId()) && !stack.isBroken()) {
                total[0] += stack.getQuantity();
            }
        });
        return total[0];
    }

    /** Removes amount items with this id and returns the removed stacks with their metadata. World thread only. */
    public static List<ItemStack> take(ItemContainer container, String itemId, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int left = amount;
        for (short slot = 0; slot < container.getCapacity() && left > 0; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack) || !itemId.equals(stack.getItemId()) || stack.isBroken()) {
                continue;
            }
            int n = Math.min(left, stack.getQuantity());
            ItemStackSlotTransaction tx = container.removeItemStackFromSlot(slot, n);
            if (tx != null && tx.succeeded()) {
                out.add(stack.withQuantity(n));
                left -= n;
            }
        }
        return out;
    }

    /** Gives stacks to the player and returns whatever did not fit. World thread only. */
    public static List<ItemStack> give(Store<EntityStore> store, Ref<EntityStore> ref, List<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            ItemStackTransaction tx = Player.giveItem(stack, ref, store);
            ItemStack rest = tx == null ? stack : tx.getRemainder();
            if (!ItemStack.isEmpty(rest)) {
                leftovers.add(rest);
            }
        }
        return leftovers;
    }

    // ---- serialisation ------------------------------------------------------

    public static Document toDocument(ItemStack stack) {
        BsonValue v = ItemStack.CODEC.encode(stack, new ExtraInfo());
        return Document.parse(v.asDocument().toJson());
    }

    /** Decodes a stored stack. Returns null when the document is unreadable. */
    public static ItemStack fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        try {
            return ItemStack.CODEC.decode(BsonDocument.parse(doc.toJson()), new ExtraInfo());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A stack from a stored template with a new quantity, falling back to a plain stack of the id. */
    public static ItemStack withQuantity(Document template, String itemId, int quantity) {
        ItemStack base = fromDocument(template);
        if (base == null || ItemStack.isEmpty(base)) {
            try {
                return new ItemStack(itemId, quantity);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return base.withQuantity(quantity);
    }

    /** Splits a quantity into stacks no larger than the item's max stack. */
    public static List<ItemStack> split(Document template, String itemId, int quantity) {
        List<ItemStack> out = new ArrayList<>();
        int max = maxStack(itemId);
        int left = quantity;
        while (left > 0) {
            int n = Math.min(left, max);
            ItemStack s = withQuantity(template, itemId, n);
            if (s == null) {
                break;
            }
            out.add(s);
            left -= n;
        }
        return out;
    }

    public static int maxStack(String itemId) {
        Item item = item(itemId);
        int max = item == null ? 0 : item.getMaxStack();
        return max > 0 ? max : 100;
    }

    public static Item item(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            return Item.getAssetMap().getAsset(itemId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean exists(String itemId) {
        return item(itemId) != null;
    }

    /** Readable name built from the item id, such as Ingredient Bar Gold. */
    public static String name(String itemId) {
        if (itemId == null) {
            return "item";
        }
        String s = itemId.replace('_', ' ').trim();
        return s.isEmpty() ? "item" : s;
    }

    private static final Map<String, String> DISPLAY_NAMES = new ConcurrentHashMap<>();
    private static final int DISPLAY_CACHE_LIMIT = 4096;

    /**
     * The name players see for an item, read from the item's translation in the server default
     * language. Falls back to the readable id from {@link #name} when no translation is loaded.
     * Safe to call from any thread. Resolved names are cached.
     */
    public static String displayName(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return name(itemId);
        }
        String cached = DISPLAY_NAMES.get(itemId);
        if (cached != null) {
            return cached;
        }
        String resolved = translate(itemId);
        if (resolved == null) {
            return name(itemId);
        }
        if (DISPLAY_NAMES.size() < DISPLAY_CACHE_LIMIT) {
            DISPLAY_NAMES.put(itemId, resolved);
        }
        return resolved;
    }

    /** The display name of a listing's item, for rows in browse results. */
    public static String displayName(Listing listing) {
        return listing == null ? name(null) : displayName(listing.itemId());
    }

    private static String translate(String itemId) {
        Item item = item(itemId);
        if (item == null) {
            return null;
        }
        try {
            String key = item.getTranslationKey();
            I18nModule i18n = I18nModule.get();
            if (key == null || key.isBlank() || i18n == null) {
                return null;
            }
            String text = i18n.getMessage(I18nModule.DEFAULT_LANGUAGE, key);
            if ((text == null || text.isBlank()) && key.startsWith("server.")) {
                text = i18n.getMessage(I18nModule.DEFAULT_LANGUAGE, key.substring("server.".length()));
            }
            return text == null || text.isBlank() ? null : text.strip();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
