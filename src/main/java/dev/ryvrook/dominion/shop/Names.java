package dev.ryvrook.dominion.shop;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import dev.ryvrook.dominion.market.Items;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Readable item names. The item asset translation is used when the server language files know it,
 * otherwise the id is turned into words.
 */
final class Names {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private Names() {}

    static String of(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "item";
        }
        return CACHE.computeIfAbsent(itemId, Names::lookup);
    }

    private static String lookup(String itemId) {
        try {
            Item item = Items.item(itemId);
            I18nModule i18n = I18nModule.get();
            if (item != null && i18n != null) {
                String key = item.getTranslationKey();
                if (key != null && !key.isBlank()) {
                    String[] tries = {key, key.startsWith("server.") ? key.substring(7) : "server." + key};
                    for (String k : tries) {
                        String v = i18n.getMessage(I18nModule.DEFAULT_LANGUAGE, k);
                        if (v != null && !v.isBlank() && !v.equals(k)) {
                            return v.trim();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Language data is optional. The readable id below is always available.
        }
        return Items.name(itemId);
    }

    /** Forgets cached names, for example after an asset reload. */
    static void clear() {
        CACHE.clear();
    }
}
