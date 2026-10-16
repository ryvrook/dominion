package dev.ryvrook.dominion.faction;

import dev.ryvrook.dominion.config.Settings;
import dev.ryvrook.dominion.text.Messages;

import java.util.Locale;

/** Validation for tags, names and short texts. Each check returns null when the value is fine. */
final class TagRules {
    private TagRules() {}

    static final int DESCRIPTION_MAX = 120;
    static final int MOTD_MAX = 160;
    static final int TITLE_MAX = 32;

    static String tag(Settings s, String tag) {
        if (tag == null || tag.isBlank()) {
            return Messages.get(FactionText.TAG_EMPTY);
        }
        int min = s.integer("faction.tagMin");
        int max = s.integer("faction.tagMax");
        if (tag.length() < min) {
            return Messages.get(FactionText.TAG_TOO_SHORT, "min", min);
        }
        if (tag.length() > max) {
            return Messages.get(FactionText.TAG_TOO_LONG, "max", max);
        }
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (!(c < 128 && Character.isLetterOrDigit(c))) {
                return Messages.get(FactionText.TAG_CHARACTERS);
            }
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        for (String banned : s.text("faction.tagBlacklist").split(",")) {
            if (!banned.isBlank() && banned.trim().toLowerCase(Locale.ROOT).equals(lower)) {
                return Messages.get(FactionText.TAG_BLOCKED);
            }
        }
        return null;
    }

    /**
     * The tag as it is stored. Trimmed, and upper case while faction.tagUppercase is on, so the sidebar,
     * chat and map never show the same tag in two cases. Existing tags are only changed by a rename.
     */
    static String normaliseTag(Settings s, String tag) {
        if (tag == null) {
            return null;
        }
        String t = tag.trim();
        return s.bool("faction.tagUppercase") ? t.toUpperCase(Locale.ROOT) : t;
    }

    static String name(Settings s, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        int max = s.integer("faction.nameMax");
        if (name.strip().length() > max) {
            return Messages.get(FactionText.NAME_TOO_LONG, "max", max);
        }
        return plain(name) ? null : Messages.get(FactionText.NAME_CHARACTERS);
    }

    static String text(String value, int max, String what) {
        if (value == null) {
            return null;
        }
        if (value.length() > max) {
            return Messages.get(FactionText.TEXT_TOO_LONG, "what", what, "max", max);
        }
        return plain(value) ? null : Messages.get(FactionText.TEXT_CHARACTERS, "what", what);
    }

    static String color(String color) {
        if (color == null || !color.matches("#?[0-9a-fA-F]{6}")) {
            return Messages.get(FactionText.COLOUR_INVALID);
        }
        return null;
    }

    static String normaliseColor(String color) {
        return (color.startsWith("#") ? color : "#" + color).toLowerCase(Locale.ROOT);
    }

    static String playerName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            return Messages.get(FactionText.PLAYER_NAME_INVALID);
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
