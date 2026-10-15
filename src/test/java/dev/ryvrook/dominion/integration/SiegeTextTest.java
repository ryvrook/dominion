package dev.ryvrook.dominion.integration;

import dev.ryvrook.dominion.integration.atlas.SiegeText;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SiegeTextTest {
    static final ZoneId UTC = ZoneOffset.UTC;
    static final long ENDS = LocalDateTime.of(2026, 10, 15, 14, 5).toInstant(ZoneOffset.UTC).toEpochMilli();

    @Test
    void popupNamesThePhaseAndTheEnd() {
        assertEquals("WOLF besieging BEAR", SiegeText.label("WOLF", "BEAR"));
        assertEquals("Unknown besieging unknown", SiegeText.label(null, " "));
        assertEquals("WOLF is besieging BEAR at chunk 3, -4. The attackers are capturing the chunk until 14:05 server time.",
                SiegeText.popup("WOLF", "BEAR", "CAPTURING", 3, -4, ENDS, UTC));
        assertEquals("WOLF is besieging BEAR at chunk 3, -4. The land is breached and open to raids until 14:05 server time.",
                SiegeText.popup("WOLF", "BEAR", "BREACHED", 3, -4, ENDS, UTC));
        assertTrue(SiegeText.popup("WOLF", "BEAR", "BREACHED", 0, 0, 0L, UTC).endsWith("open to raids."));
    }

    @Test
    void noSemicolonsOrDashesInTheText() {
        String p = SiegeText.popup("A", "B", "CAPTURING", 1, 2, ENDS, UTC);
        assertFalse(p.contains(";") || p.contains("\u2014") || p.contains("\u2013"));
    }
}
