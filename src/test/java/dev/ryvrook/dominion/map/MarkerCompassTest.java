package dev.ryvrook.dominion.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarkerCompassTest {
    @Test
    void noneHidesEveryMarkerFromTheCompass() {
        assertEquals(ClaimMarkerProvider.BLANK_IMAGE, ClaimMarkerProvider.compassImage("none", false));
        assertEquals(ClaimMarkerProvider.BLANK_IMAGE, ClaimMarkerProvider.compassImage("none", true));
        assertEquals(ClaimMarkerProvider.BLANK_IMAGE, ClaimMarkerProvider.compassImage(null, true), "an unknown value hides");
    }

    @Test
    void eventsKeepsHomesAndSiegesButNotClaims() {
        assertNull(ClaimMarkerProvider.compassImage("events", true));
        assertEquals(ClaimMarkerProvider.BLANK_IMAGE, ClaimMarkerProvider.compassImage("Events ", false));
    }

    @Test
    void allLeavesEveryMarkerAlone() {
        assertNull(ClaimMarkerProvider.compassImage("all", false));
        assertNull(ClaimMarkerProvider.compassImage("ALL", true));
    }
}
