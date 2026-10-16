package dev.ryvrook.dominion.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudMarkupTest {

    @Test
    void anchorFollowsSideAndTop() {
        assertTrue(DominionHud.markup("right", 400).contains("Anchor: (Right: 20, Top: 400, Width: 250, Height: 250);"));
        assertTrue(DominionHud.markup("LEFT", 120).contains("Anchor: (Left: 20, Top: 120,"));
        assertTrue(DominionHud.markup("middle", -5).contains("Anchor: (Right: 20, Top: 0,"));
        assertTrue(DominionHud.markup(null, 300).contains("(Right: 20, Top: 300,"));
    }

    @Test
    void markupIsBalancedAndHasEveryRowTheHudUpdates() {
        String m = DominionHud.markup("right", 400);
        assertEquals(m.chars().filter(c -> c == '{').count(), m.chars().filter(c -> c == '}').count());
        assertEquals(m.chars().filter(c -> c == '(').count(), m.chars().filter(c -> c == ')').count());
        assertEquals(0, m.chars().filter(c -> c == '"').count() % 2);
        for (String id : new String[] {"#DomTag ", "#DomTitle ", "#DomRank ", "#DomHintRow ", "#DomHint ", "#DomPowerRow ", "#DomPower ",
                "#DomFactionPowerRow ", "#DomFactionPower ", "#DomLandRow ", "#DomLand ", "#DomOnlineRow ", "#DomOnline ",
                "#DomNationRow ", "#DomNation ", "#DomHereRow ", "#DomHereBar ", "#DomHere ", "#DomHereFlags ",
                "#DomStatusRow ", "#DomStatus ", "#DomSiegeRow ", "#DomSiege ", "#DomShieldRow ", "#DomShield ",
                "#DomGraceRow ", "#DomGrace "}) {
            assertEquals(1, count(m, id), id + " appears once");
        }
    }

    @Test
    void fitShortensWithAnEllipsis() {
        assertEquals("short", DominionHud.fit("short", 10));
        assertEquals("abcdefg...", DominionHud.fit("abcdefghijklmnop", 10));
        assertEquals("", DominionHud.fit(null, 10));
    }

    @Test
    void titleShowsTheBracketedTagAndTheNameInItsOwnCase() {
        assertArrayEquals(new String[] {"[TST]", "Testers"}, DominionHud.titleParts("TST", "Testers"));
        assertArrayEquals(new String[] {"", "Testers"}, DominionHud.titleParts(null, "Testers"));
        String[] longOne = DominionHud.titleParts("ABCD", "A very long faction name here");
        assertEquals("[ABCD]", longOne[0]);
        assertTrue(longOne[0].length() + 1 + longOne[1].length() <= 18, longOne[1]);
        String[] wide = DominionHud.titleParts("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "Name");
        assertTrue(wide[0].length() <= 12, wide[0]);
        assertEquals("Name", wide[1]);
    }

    @Test
    void titleKeepsItsCaseWhileTheRankStaysUpperCase() {
        String m = DominionHud.markup("right", 400);
        String title = m.substring(m.indexOf("Label #DomTitle"), m.indexOf("Label #DomRank"));
        String rank = m.substring(m.indexOf("Label #DomRank"), m.indexOf("Group #DomHintRow"));
        assertFalse(title.contains("RenderUppercase"), title);
        assertTrue(rank.contains("RenderUppercase: true"), rank);
    }

    @Test
    void gridShadeKeepsAValidColour() {
        assertEquals("#000000", DominionPanel.shade("#ffffff", 0));
        assertEquals("#808080", DominionPanel.shade("#ffffff", 0.5 + 1e-9));
        assertTrue(DominionPanel.shade("#5fd37a", 0.45).matches("#[0-9a-f]{6}"));
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void aRefreshSendsOnlyWhatChanged() {
        DominionHud.Values first = new DominionHud.Values();
        first.set("#A.Text", "one");
        first.set("#A.Visible", true);
        first.patch("#Bar.Background", "#112233");
        assertEquals(3, first.writeChanges(new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder(), null));
        DominionHud.Values same = new DominionHud.Values();
        same.set("#A.Text", "one");
        same.set("#A.Visible", true);
        same.patch("#Bar.Background", "#112233");
        assertEquals(0, same.writeChanges(new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder(), first.map));
        DominionHud.Values moved = new DominionHud.Values();
        moved.set("#A.Text", "two");
        moved.set("#A.Visible", true);
        moved.patch("#Bar.Background", "#445566");
        assertEquals(2, moved.writeChanges(new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder(), first.map));
    }
}
