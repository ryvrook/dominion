package dev.ryvrook.dominion.integration.atlas;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The label and popup of a live siege marker. Pure and free of Atlas types, so it is tested on its own. */
public final class SiegeText {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private SiegeText() {}

    public static String label(String attacker, String defender) {
        return tag(attacker, "Unknown") + " besieging " + tag(defender, "unknown");
    }

    /**
     * Who besieges whom, where, the phase and when it ends, in the server's time zone. Atlas escapes the popup,
     * so faction tags are safe in it. The end time is absolute because the marker only changes on siege events.
     */
    public static String popup(String attacker, String defender, String phase, int chunkX, int chunkZ, long endsAt,
                               ZoneId zone) {
        String a = tag(attacker, "Unknown");
        String d = tag(defender, "unknown");
        String when = endsAt > 0 ? CLOCK.format(Instant.ofEpochMilli(endsAt).atZone(zone)) + " server time" : null;
        String state = switch (phase == null ? "" : phase) {
            case "CAPTURING" -> when == null ? "The attackers are capturing the chunk."
                    : "The attackers are capturing the chunk until " + when + ".";
            case "BREACHED" -> when == null ? "The land is breached and open to raids."
                    : "The land is breached and open to raids until " + when + ".";
            default -> "The siege is " + phase.toLowerCase(Locale.ROOT) + ".";
        };
        return a + " is besieging " + d + " at chunk " + chunkX + ", " + chunkZ + ". " + state;
    }

    private static String tag(String tag, String fallback) {
        return tag == null || tag.isBlank() ? fallback : tag;
    }
}
