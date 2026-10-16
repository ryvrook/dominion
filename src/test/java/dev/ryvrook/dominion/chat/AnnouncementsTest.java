package dev.ryvrook.dominion.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementsTest {
    @Test
    void labelAddsTheNameOnlyWhenItDiffersFromTheTag() {
        assertEquals("ABC", Announcements.label("ABC", "ABC"));
        assertEquals("ABC", Announcements.label("ABC", " "));
        assertEquals("ABC", Announcements.label("ABC", null));
        assertEquals("[ABC] The Alphas", Announcements.label("ABC", " The Alphas "));
        assertEquals("Test", Announcements.label("test", "Test"), "a tag that repeats the name in another case shows once");
    }

    @Test
    void renameLine() {
        assertEquals("The faction Old is now New.", Announcements.line("Old", "New"));
    }

    @Test
    void nameplateText() {
        assertEquals("[ABC] Meal", Nameplates.text("ABC", "Meal"));
    }

    @Test
    void savedTagOfAnyFactionIsRecognised() {
        assertTrue(Nameplates.taggedText("[ABC] Meal", "Meal"));
        assertTrue(Nameplates.taggedText("[OLD9] Meal", "Meal"), "a tag from a faction the player left still comes off");
        assertFalse(Nameplates.taggedText("Meal", "Meal"));
        assertFalse(Nameplates.taggedText("[] Meal", "Meal"));
        assertFalse(Nameplates.taggedText("[Sir Knight] Meal", "Meal"), "another plugin's prefix with a space is left alone");
        assertFalse(Nameplates.taggedText("[ABC] Other", "Meal"));
        assertFalse(Nameplates.taggedText(null, "Meal"));
    }
}
