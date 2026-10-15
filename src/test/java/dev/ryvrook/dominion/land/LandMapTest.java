package dev.ryvrook.dominion.land;

import dev.ryvrook.dominion.model.Relation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LandMapTest {

    private static String[] rowOf(int width) {
        String[] colors = new String[width];
        for (int i = 0; i < width; i++) {
            colors[i] = i % 7 == 0 ? Relation.ENEMY.color() : i % 3 == 0 ? Relation.OWN.color() : LandMap.WILD;
        }
        colors[width / 2] = Land.YOU;
        return colors;
    }

    @Test
    void everyRowIsExactlyAsWideAsTheMapAndUsesOneGlyph() {
        for (int size = 1; size <= 3; size++) {
            int width = 21 + (size - 1) * 10;
            StringBuilder text = new StringBuilder();
            for (String[] run : LandMap.runs(rowOf(width), width / 2)) {
                text.append(run[0]);
            }
            assertEquals(width, text.length(), "row width for size " + size);
            assertTrue(text.toString().chars().allMatch(ch -> ch == '#'), "only the cell glyph is used");
        }
    }

    @Test
    void runsFollowTheColoursAndOnlyTheCentreIsBold() {
        String w = LandMap.WILD;
        String own = Relation.OWN.color();
        List<String[]> runs = LandMap.runs(new String[] {w, w, own, Land.YOU, own, w}, 3);
        assertEquals(5, runs.size());
        assertArrayEquals(new String[] {"##", w, null}, runs.get(0));
        assertArrayEquals(new String[] {"#", own, null}, runs.get(1));
        assertArrayEquals(new String[] {"#", Land.YOU, "bold"}, runs.get(2));
        assertArrayEquals(new String[] {"#", own, null}, runs.get(3));
        assertArrayEquals(new String[] {"#", w, null}, runs.get(4));
        assertTrue(LandMap.runs(new String[0], -1).isEmpty());
    }

    @Test
    void legendIsTwoShortLinesWithDistinctColours() {
        List<List<String[]>> lines = LandMap.legendEntries();
        assertEquals(2, lines.size());
        Set<String> colours = new HashSet<>();
        int entries = 0;
        for (List<String[]> line : lines) {
            int chars = 0;
            for (String[] e : line) {
                chars += e[0].length() + 5;
                colours.add(e[1]);
                entries++;
            }
            assertTrue(chars <= 50, "legend line stays short");
        }
        assertEquals(entries, colours.size(), "each legend entry has its own colour");
    }

    @Test
    void zonesUseALighterShadeOfTheOwnerColour() {
        assertEquals("#ffffff", LandMap.lighter("#000000", 1));
        assertEquals("#000000", LandMap.lighter("#000000", 0));
        assertEquals("#808080", LandMap.lighter("#000000", 0.5 + 1e-9));
        String zone = LandMap.lighter(Relation.OWN.color(), LandMap.ZONE_LIGHTEN);
        assertNotEquals(Relation.OWN.color(), zone);
        assertEquals("oops", LandMap.lighter("oops", 0.5));
    }
}
