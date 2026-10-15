package dev.ryvrook.dominion.integration.atlas;

import dev.ryvrook.dominion.integration.IntegrationText;
import dev.ryvrook.dominion.text.Messages;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The label and popup of a live siege marker. Pure and free of Atlas types, so it is tested on its own. */
public final class SiegeText {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private SiegeText() {}

    public static String label(String attacker, String defender) {
        return Messages.get(IntegrationText.SIEGE_LABEL, "attacker", tag(attacker, IntegrationText.SIEGE_UNKNOWN_ATTACKER),
                "defender", tag(defender, IntegrationText.SIEGE_UNKNOWN_DEFENDER));
    }

    /**
     * Who besieges whom, where, the phase and when it ends, in the server's time zone. Atlas escapes the popup,
     * so faction tags are safe in it. The end time is absolute because the marker only changes on siege events.
     */
    public static String popup(String attacker, String defender, String phase, int chunkX, int chunkZ, long endsAt,
                               ZoneId zone) {
        String a = tag(attacker, IntegrationText.SIEGE_UNKNOWN_ATTACKER);
        String d = tag(defender, IntegrationText.SIEGE_UNKNOWN_DEFENDER);
        String when = endsAt > 0 ? Messages.get(IntegrationText.SIEGE_WHEN, "time",
                CLOCK.format(Instant.ofEpochMilli(endsAt).atZone(zone))) : null;
        String state = switch (phase == null ? "" : phase) {
            case "CAPTURING" -> when == null ? Messages.get(IntegrationText.SIEGE_CAPTURING)
                    : Messages.get(IntegrationText.SIEGE_CAPTURING_UNTIL, "when", when);
            case "BREACHED" -> when == null ? Messages.get(IntegrationText.SIEGE_BREACHED)
                    : Messages.get(IntegrationText.SIEGE_BREACHED_UNTIL, "when", when);
            default -> Messages.get(IntegrationText.SIEGE_PHASE, "phase", phase.toLowerCase(Locale.ROOT));
        };
        return Messages.get(IntegrationText.SIEGE_POPUP, "attacker", a, "defender", d, "x", chunkX, "z", chunkZ,
                "state", state);
    }

    private static String tag(String tag, String fallbackId) {
        return tag == null || tag.isBlank() ? Messages.get(fallbackId) : tag;
    }
}
