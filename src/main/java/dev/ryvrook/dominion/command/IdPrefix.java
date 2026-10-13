package dev.ryvrook.dominion.command;

import com.hypixel.hytale.server.core.Message;
import dev.ryvrook.dominion.text.Msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves a typed id against a set of candidates. Ids are UUIDs, so players may type the full
 * id or any prefix of at least six characters, with or without the dashes.
 */
public final class IdPrefix {
    public static final int MIN_LENGTH = 6;
    private static final int SHOW_AMBIGUOUS = 5;

    private IdPrefix() {}

    /** The short form shown next to listings and sieges. */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** The single match, or null after telling the caller why nothing was picked. */
    public static <T> T resolve(Call c, String typed, Iterable<T> candidates, Function<T, UUID> idOf,
                                Function<T, String> labelOf, String noun) {
        String text = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        String compact = text.replace("-", "");
        if (compact.length() < MIN_LENGTH) {
            c.bad("Type at least " + MIN_LENGTH + " characters of the " + noun + " id.");
            return null;
        }
        Map<UUID, T> matches = new LinkedHashMap<>();
        for (T candidate : candidates) {
            UUID id = idOf.apply(candidate);
            if (id == null || matches.containsKey(id)) {
                continue;
            }
            String full = id.toString();
            if (full.startsWith(text) || full.replace("-", "").startsWith(compact)) {
                matches.put(id, candidate);
            }
        }
        if (matches.isEmpty()) {
            c.bad("No " + noun + " has an id starting with " + typed.trim() + ".");
            return null;
        }
        if (matches.size() == 1) {
            return matches.values().iterator().next();
        }
        c.bad(matches.size() + " " + noun + "s start with " + typed.trim() + ". Type more of the id.");
        List<T> shown = new ArrayList<>(matches.values());
        for (int i = 0; i < Math.min(SHOW_AMBIGUOUS, shown.size()); i++) {
            T t = shown.get(i);
            c.send(Message.join(Msg.colored(" " + idOf.apply(t), Msg.GOLD), Msg.muted("  " + labelOf.apply(t))));
        }
        return null;
    }
}
