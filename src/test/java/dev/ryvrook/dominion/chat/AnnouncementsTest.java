package dev.ryvrook.dominion.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnouncementsTest {
    @Test
    void labelAddsTheNameOnlyWhenItDiffersFromTheTag() {
        assertEquals("ABC", Announcements.label("ABC", "ABC"));
        assertEquals("ABC", Announcements.label("ABC", " "));
        assertEquals("ABC", Announcements.label("ABC", null));
        assertEquals("ABC The Alphas", Announcements.label("ABC", " The Alphas "));
    }

    @Test
    void renameLine() {
        assertEquals("The faction Old is now New.", Announcements.line("Old", "New"));
    }

    @Test
    void nameplateText() {
        assertEquals("[ABC] Meal", Nameplates.text("ABC", "Meal"));
    }
}
