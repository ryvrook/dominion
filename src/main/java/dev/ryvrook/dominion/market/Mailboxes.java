package dev.ryvrook.dominion.market;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.ryvrook.dominion.storage.JsonFiles;
import org.bson.Document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per player mailboxes stored as market/mailbox/uuid.json. Items are kept as the documents
 * produced by ItemStack.CODEC so nothing is lost if an item asset is missing at load time.
 * Callers hold the market lock while mutating.
 */
final class Mailboxes {
    static final class Box {
        final List<Document> items = new ArrayList<>();
        /** Plain text notes shown once at the next login, such as a roster rotation notice. */
        final List<String> notes = new ArrayList<>();
        double coins;

        boolean empty() {
            return items.isEmpty() && coins <= 0 && notes.isEmpty();
        }
    }

    private final JsonFiles files;
    private final Path dir;
    private final Map<UUID, Box> boxes = new ConcurrentHashMap<>();

    Mailboxes(JsonFiles files, Path dir) {
        this.files = files;
        this.dir = dir;
    }

    Box box(UUID player) {
        return boxes.computeIfAbsent(player, this::load);
    }

    private Box load(UUID player) {
        Box b = new Box();
        Document d = files.read(file(player));
        if (d != null) {
            List<?> items = d.get("Items", List.class);
            if (items != null) {
                for (Object o : items) {
                    if (o instanceof Document doc) {
                        b.items.add(doc);
                    }
                }
            }
            List<?> notes = d.get("Notes", List.class);
            if (notes != null) {
                for (Object o : notes) {
                    if (o instanceof String text) {
                        b.notes.add(text);
                    }
                }
            }
            Object c = d.get("Coins");
            b.coins = c instanceof Number n ? n.doubleValue() : 0;
        }
        return b;
    }

    void addItems(UUID player, List<ItemStack> stacks) {
        Box b = box(player);
        for (ItemStack s : stacks) {
            if (!ItemStack.isEmpty(s)) {
                b.items.add(Items.toDocument(s));
            }
        }
        save(player, b);
    }

    void addCoins(UUID player, double coins) {
        Box b = box(player);
        b.coins = MarketServiceImpl.round(b.coins + coins);
        save(player, b);
    }

    /** Keeps at most this many notes per player, dropping the oldest. */
    static final int MAX_NOTES = 20;

    void addNote(UUID player, String text) {
        Box b = box(player);
        b.notes.add(text);
        while (b.notes.size() > MAX_NOTES) {
            b.notes.remove(0);
        }
        save(player, b);
    }

    void save(UUID player, Box b) {
        if (b.empty()) {
            files.delete(file(player));
            return;
        }
        files.write(file(player), new Document("Items", new ArrayList<>(b.items)).append("Coins", b.coins)
                .append("Notes", new ArrayList<>(b.notes)));
    }

    private Path file(UUID player) {
        return dir.resolve(player + ".json");
    }
}
