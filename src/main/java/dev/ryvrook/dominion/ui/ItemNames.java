package dev.ryvrook.dominion.ui;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Readable item names for market rows. Uses the public static market.Items.displayName(String)
 * when the market module provides it, looked up once by reflection so the ui module compiles
 * and runs whether or not that method or the market module is present. Falls back to the raw id.
 */
final class ItemNames {
    private static final String ITEMS_CLASS = "dev.ryvrook.dominion.market.Items";
    private static volatile Method method;
    private static volatile boolean resolved;

    private ItemNames() {}

    static String of(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        Method m = lookup();
        if (m != null) {
            try {
                Object out = m.invoke(null, itemId);
                if (out instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall through to the raw id.
            }
        }
        return itemId;
    }

    private static Method lookup() {
        if (resolved) {
            return method;
        }
        synchronized (ItemNames.class) {
            if (!resolved) {
                try {
                    Class<?> c = Class.forName(ITEMS_CLASS, true, ItemNames.class.getClassLoader());
                    Method m = c.getMethod("displayName", String.class);
                    if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == String.class) {
                        method = m;
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                    method = null;
                }
                resolved = true;
            }
        }
        return method;
    }
}
