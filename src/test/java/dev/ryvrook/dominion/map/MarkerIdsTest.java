package dev.ryvrook.dominion.map;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarkerIdsTest {
    @Test
    void plainIdsStayReadable() {
        assertEquals("dominion_claim_default_-12345", MarkerIds.of("claim", "default", -12345L));
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        assertEquals("dominion_home_12345678-1234-1234-1234-123456789abc", MarkerIds.of("home", id));
        assertTrue(MarkerIds.valid(MarkerIds.of("allyhome", id)));
        assertTrue(MarkerIds.valid(MarkerIds.of("siege", id)));
    }

    @Test
    void unsafeCharactersAreReplaced() {
        String id = MarkerIds.of("event", "hill:Spawn Hill/№1");
        assertEquals("dominion_event_hill:Spawn-Hill--1", id);
        assertTrue(MarkerIds.valid(id));
    }

    @Test
    void longIdsAreCutWithAHashAndStayDistinct() {
        String world = "a_world_with_a_really_long_name_for_testing_purposes";
        String a = MarkerIds.of("claim", world, Long.MIN_VALUE);
        String b = MarkerIds.of("claim", world, Long.MAX_VALUE);
        assertTrue(a.length() <= MarkerIds.MAX_LENGTH);
        assertTrue(MarkerIds.valid(a));
        assertNotEquals(a, b);
        assertEquals(a, MarkerIds.of("claim", world, Long.MIN_VALUE), "ids are stable between updates");
    }

    @Test
    void emptyPartsStillMakeAValidId() {
        assertTrue(MarkerIds.valid(MarkerIds.of("event", "")));
        assertFalse(MarkerIds.valid(""));
        assertFalse(MarkerIds.valid("has space"));
    }
}
