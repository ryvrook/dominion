package dev.ryvrook.dominion.text;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The colours from the colors settings section. Settings calls {@link #refresh} after it loads,
 * reloads or changes a value, and the holder copies the colours into the {@link Msg} fields and tells
 * every listener, so classes that keep a colour in a field of their own stay current. A value that is
 * not a #rrggbb colour falls back to its default.
 */
public final class Palette {
    private Palette() {}

    /** Setting key and default of every colour, in the order the settings page lists them. */
    public static final Map<String, String> DEFAULTS = orderedDefaults();

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile String own = DEFAULTS.get("colors.own");
    private static volatile String ally = DEFAULTS.get("colors.ally");
    private static volatile String truce = DEFAULTS.get("colors.truce");
    private static volatile String neutral = DEFAULTS.get("colors.neutral");
    private static volatile String enemy = DEFAULTS.get("colors.enemy");
    private static volatile String safeZone = DEFAULTS.get("colors.safeZone");
    private static volatile String warZone = DEFAULTS.get("colors.warZone");
    private static volatile String wilderness = DEFAULTS.get("colors.wilderness");

    private static Map<String, String> orderedDefaults() {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        m.put("colors.own", "#5fd37a");
        m.put("colors.ally", "#d99cff");
        m.put("colors.truce", "#7fd6ff");
        m.put("colors.neutral", "#d3d6db");
        m.put("colors.enemy", "#ff6b6b");
        m.put("colors.safeZone", "#E8A93B");
        m.put("colors.warZone", "#e0413c");
        m.put("colors.wilderness", "#6b7280");
        m.put("colors.accent", "#E8A93B");
        m.put("colors.text", "#d3d6db");
        m.put("colors.muted", "#96a9be");
        m.put("colors.good", "#5fd37a");
        m.put("colors.bad", "#ff6b6b");
        m.put("colors.warn", "#ffb347");
        m.put("colors.info", "#7aa7e6");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** True for a #rrggbb colour. */
    public static boolean valid(String color) {
        return color != null && HEX.matcher(color.trim()).matches();
    }

    /**
     * Reads every colour through the lookup, which answers the setting's text for a key, then updates
     * Msg and the listeners. Safe to call from any thread.
     */
    public static void refresh(Function<String, String> lookup) {
        own = pick(lookup, "colors.own");
        ally = pick(lookup, "colors.ally");
        truce = pick(lookup, "colors.truce");
        neutral = pick(lookup, "colors.neutral");
        enemy = pick(lookup, "colors.enemy");
        safeZone = pick(lookup, "colors.safeZone");
        warZone = pick(lookup, "colors.warZone");
        wilderness = pick(lookup, "colors.wilderness");
        Msg.GOLD = pick(lookup, "colors.accent");
        Msg.TEXT = pick(lookup, "colors.text");
        Msg.MUTED = pick(lookup, "colors.muted");
        Msg.GOOD = pick(lookup, "colors.good");
        Msg.BAD = pick(lookup, "colors.bad");
        Msg.WARN = pick(lookup, "colors.warn");
        Msg.INFO = pick(lookup, "colors.info");
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (RuntimeException e) {
                // A listener that fails must not keep the others from updating.
            }
        }
    }

    private static String pick(Function<String, String> lookup, String key) {
        String v = lookup == null ? null : lookup.apply(key);
        return valid(v) ? v.trim() : DEFAULTS.get(key);
    }

    /** Runs the listener after every refresh. Classes that copy a colour into a field register here. */
    public static void onChange(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static String own() { return own; }
    public static String ally() { return ally; }
    public static String truce() { return truce; }
    public static String neutral() { return neutral; }
    public static String enemy() { return enemy; }
    public static String safeZone() { return safeZone; }
    public static String warZone() { return warZone; }
    public static String wilderness() { return wilderness; }
}
