package dev.ryvrook.dominion.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command names from the commands settings section. The names are read when the commands are
 * registered, at start or /plugin reload, and the names actually registered are remembered here so
 * text that names a command, such as help lines and hints, can print the name players have to type.
 */
public final class CommandNames {
    private CommandNames() {}

    public static final String DEFAULT_ROOT = "f";
    public static final String DEFAULT_ADMIN = "fa";
    public static final String DEFAULT_EVENTS = "fe";
    public static final String DEFAULT_SHOP_EDIT = "fashop";

    /** What a command name or alias may contain. */
    public static final String NAME = "[A-Za-z0-9_-]{1,32}";
    /** A comma separated list of names, possibly empty. */
    public static final String NAME_LIST = "\\s*(" + NAME + "(\\s*,\\s*" + NAME + ")*)?\\s*";

    private static final Pattern VALID = Pattern.compile(NAME);

    private static volatile String root = DEFAULT_ROOT;
    private static volatile String admin = DEFAULT_ADMIN;
    private static volatile String events = DEFAULT_EVENTS;
    private static volatile String shopEdit = DEFAULT_SHOP_EDIT;
    private static volatile Set<String> disabled = Set.of();

    /** The registered player root, f unless commands.root changed it. */
    public static String root() { return root; }
    public static String admin() { return admin; }
    public static String events() { return events; }
    public static String shopEdit() { return shopEdit; }

    /** The root name to register from a setting, the default when the setting is blank or not a valid name. */
    public static String name(String configured, String fallback) {
        String v = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        return VALID.matcher(v).matches() ? v : fallback;
    }

    /**
     * The aliases of a comma separated setting, lower case, without blanks, duplicates, invalid names
     * or the names in exclude.
     */
    public static List<String> aliases(String csv, String... exclude) {
        Set<String> out = new LinkedHashSet<>();
        Set<String> skip = new LinkedHashSet<>();
        for (String e : exclude) {
            if (e != null) {
                skip.add(e.toLowerCase(Locale.ROOT));
            }
        }
        if (csv != null) {
            for (String part : csv.split(",")) {
                String a = part.trim().toLowerCase(Locale.ROOT);
                if (!a.isEmpty() && VALID.matcher(a).matches() && !skip.contains(a)) {
                    out.add(a);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** The sub command names in commands.disabled, lower case. */
    public static Set<String> disabledIn(String csv) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(aliases(csv)));
    }

    /** Records the player root and the disabled sub commands that were registered. */
    public static void registeredRoot(String name, Set<String> off) {
        root = name(name, DEFAULT_ROOT);
        disabled = off == null ? Set.of() : Set.copyOf(off);
    }

    public static void registeredAdmin(String name) {
        admin = name(name, DEFAULT_ADMIN);
    }

    public static void registeredEvents(String name) {
        events = name(name, DEFAULT_EVENTS);
    }

    public static void registeredShopEdit(String name) {
        shopEdit = name(name, DEFAULT_SHOP_EDIT);
    }

    /** True when the sub command was left out by commands.disabled at the last registration. */
    public static boolean disabled(String sub) {
        return sub != null && disabled.contains(sub.toLowerCase(Locale.ROOT));
    }

    /** The disabled sub commands of the last registration. */
    public static Set<String> disabled() {
        return disabled;
    }

    private static final Pattern SLASH = Pattern.compile("(?<![\\w/])/(f|fa|fe|fashop)(?![\\w-])");

    /**
     * Rewrites /f, /fa, /fe and /fashop in a sentence to the registered names. Returns the text
     * itself when every name is the default, which is the usual case.
     */
    public static String localize(String text) {
        if (text == null || text.indexOf('/') < 0) {
            return text;
        }
        String r = root;
        String a = admin;
        String e = events;
        String s = shopEdit;
        if (r.equals(DEFAULT_ROOT) && a.equals(DEFAULT_ADMIN) && e.equals(DEFAULT_EVENTS) && s.equals(DEFAULT_SHOP_EDIT)) {
            return text;
        }
        Matcher m = SLASH.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 8);
        while (m.find()) {
            String name = switch (m.group(1)) {
                case "f" -> r;
                case "fa" -> a;
                case "fe" -> e;
                default -> s;
            };
            m.appendReplacement(out, Matcher.quoteReplacement("/" + name));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Puts every name back to its default. For tests. */
    static void resetForTests() {
        root = DEFAULT_ROOT;
        admin = DEFAULT_ADMIN;
        events = DEFAULT_EVENTS;
        shopEdit = DEFAULT_SHOP_EDIT;
        disabled = Set.of();
    }
}
