package dev.ryvrook.dominion.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LabelTest {

    private static Faction faction(String tag, String name) {
        return new Faction(UUID.randomUUID(), tag, name, UUID.randomUUID(), 0L);
    }

    private static Nation nation(String tag, String name) {
        return new Nation(UUID.randomUUID(), tag, name, UUID.randomUUID(), 0L);
    }

    @Test
    void factionLabelShowsTheNameOnceWhenTheTagRepeatsIt() {
        assertEquals("test", faction("test", "test").label());
        assertEquals("Test", faction("TEST", "Test").label());
        assertFalse(faction("test", "Test").tagDiffers());
    }

    @Test
    void factionLabelKeepsADifferentTag() {
        Faction f = faction("RED", "Red Legion");
        assertEquals("[RED] Red Legion", f.label());
        assertTrue(f.tagDiffers());
    }

    @Test
    void factionLabelFallsBackToTheTagWhenTheNameIsBlank() {
        Faction f = faction("RED", "Red");
        f.setName("  ");
        assertEquals("RED", f.label());
        assertFalse(f.tagDiffers());
        f.setName(null);
        assertEquals("RED", f.label());
    }

    @Test
    void factionLabelFollowsRenames() {
        Faction f = faction("abc", "abc");
        assertEquals("abc", f.label());
        f.setName("Alpha");
        assertEquals("[abc] Alpha", f.label());
    }

    @Test
    void nationLabelUsesTheSameRule() {
        assertEquals("north", nation("north", "north").label());
        assertEquals("[NO] Northern Pact", nation("NO", "Northern Pact").label());
        assertTrue(nation("NO", "Northern Pact").tagDiffers());
        Nation n = nation("NO", "No");
        assertEquals("No", n.label());
        n.setName("");
        assertEquals("NO", n.label());
    }

    @Test
    void bracketedWrapsTheTrimmedTag() {
        assertEquals("[TST]", Faction.bracketed(" TST "));
        assertEquals("[TST] Testers", Faction.label("TST", "Testers"));
        assertEquals("Testers", Faction.label("TESTERS", "Testers"));
    }
}
