package dev.ryvrook.dominion.importer;

import dev.ryvrook.dominion.config.Settings;

import java.util.Locale;
import java.util.function.Predicate;

/** Turns foreign faction names into Dominion tags that pass the same rules as /f create. */
final class Tags {
    private Tags() {}

    /** Letters and digits of the name, cut to the maximum and padded to the minimum length. */
    static String base(String raw, Settings s) {
        int min = Math.max(1, s.integer("faction.tagMin"));
        int max = Math.max(min, s.integer("faction.tagMax"));
        StringBuilder b = new StringBuilder();
        if (raw != null) {
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c < 128 && Character.isLetterOrDigit(c)) {
                    b.append(c);
                }
            }
        }
        if (b.isEmpty()) {
            b.append("Imported");
        }
        if (b.length() > max) {
            b.setLength(max);
        }
        while (b.length() < min) {
            b.append('0');
        }
        return b.toString();
    }

    /** A tag that is free and allowed, adding a number when the plain tag is taken. Null when none fits. */
    static String unique(String raw, Settings s, Predicate<String> free) {
        int min = Math.max(1, s.integer("faction.tagMin"));
        int max = Math.max(min, s.integer("faction.tagMax"));
        String base = base(raw, s);
        if (allowed(base, s) && free.test(base)) {
            return base;
        }
        for (int n = 2; n < 10000; n++) {
            String suffix = Integer.toString(n);
            if (suffix.length() >= max) {
                break;
            }
            String t = base.substring(0, Math.min(base.length(), max - suffix.length())) + suffix;
            if (t.length() >= min && allowed(t, s) && free.test(t)) {
                return t;
            }
        }
        return null;
    }

    private static boolean allowed(String tag, Settings s) {
        String lower = tag.toLowerCase(Locale.ROOT);
        String list = s.text("faction.tagBlacklist");
        if (list == null) {
            return true;
        }
        for (String banned : list.split(",")) {
            if (!banned.isBlank() && banned.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                return false;
            }
        }
        return true;
    }
}
