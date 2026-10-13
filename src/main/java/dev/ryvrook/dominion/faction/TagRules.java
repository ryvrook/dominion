package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.config.Settings;

import java.util.Locale;

/** Validation for tags, names and short texts. Each check returns null when the value is fine. */
final class TagRules {
    private TagRules() {}

    static final int DESCRIPTION_MAX = 120;
    static final int MOTD_MAX = 160;
    static final int TITLE_MAX = 24;

    static String tag(Settings s, String tag) {
        if (tag == null || tag.isBlank()) {
            return "Please choose a tag";
        }
        int min = s.integer("faction.tagMin");
        int max = s.integer("faction.tagMax");
        if (tag.length() < min) {
            return "Tags must be at least " + min + " characters long";
        }
        if (tag.length() > max) {
            return "Tags can be at most " + max + " characters long";
        }
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (!(c < 128 && Character.isLetterOrDigit(c))) {
                return "Tags may only use letters and digits";
            }
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        for (String banned : s.text("faction.tagBlacklist").split(",")) {
            if (!banned.isBlank() && banned.trim().toLowerCase(Locale.ROOT).equals(lower)) {
                return "That tag is not allowed";
            }
        }
        return null;
    }

    static String name(Settings s, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        int max = s.integer("faction.nameMax");
        if (name.strip().length() > max) {
            return "Names can be at most " + max + " characters long";
        }
        return plain(name) ? null : "Names cannot contain special characters";
    }

    static String text(String value, int max, String what) {
        if (value == null) {
            return null;
        }
        if (value.length() > max) {
            return what + " can be at most " + max + " characters long";
        }
        return plain(value) ? null : what + " cannot contain special characters";
    }

    static String color(String color) {
        if (color == null || !color.matches("#?[0-9a-fA-F]{6}")) {
            return "Colours must be a hex value such as #7fd6ff";
        }
        return null;
    }

    static String normaliseColor(String color) {
        return (color.startsWith("#") ? color : "#" + color).toLowerCase(Locale.ROOT);
    }

    static String playerName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            return "That is not a valid player name";
        }
        return null;
    }

    private static boolean plain(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
