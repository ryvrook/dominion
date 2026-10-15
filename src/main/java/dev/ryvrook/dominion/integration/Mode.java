package dev.ryvrook.dominion.integration;

import java.util.Locale;

/** The integrations.tracker, integrations.feats and integrations.atlas settings. */
public enum Mode {
    /** Binds when the sibling is installed and new enough, else runs without it quietly. The default. */
    AUTO,
    /** Binds to any installed version and logs a warning when the sibling is missing. */
    ALWAYS,
    /** Never binds, even when the sibling is installed. */
    NEVER;

    /** Reads the setting. Anything unknown, blank or null is {@link #AUTO}. */
    public static Mode parse(String value) {
        if (value == null) {
            return AUTO;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "always" -> ALWAYS;
            case "never", "off" -> NEVER;
            default -> AUTO;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
