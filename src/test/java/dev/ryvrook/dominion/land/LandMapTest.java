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
    void legendPacksSeveralFactionsPerLineAndKeepsTheFixedLine() {
        List<String[]> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            entries.add(new String[] {"Faction" + i, "#112233", Relation.NEUTRAL.color()});
        }
        List<List<String[]>> lines = LandMap.pack(entries, LandMap.LEGEND_CHARS);
        assertTrue(lines.size() >= 2 && lines.size() <= 3, "nine entries fit on a few lines");
        int total = 0;
        for (List<String[]> line : lines) {
            assertTrue(line.size() > 1, "several entries share a line");
            int chars = line.stream().mapToInt(e -> e[0].length() + 5).sum();
            assertTrue(chars <= LandMap.LEGEND_CHARS);
            total += line.size();
        }
        assertEquals(9, total);
        List<List<String[]>> one = LandMap.pack(List.<String[]>of(new String[] {"x".repeat(80), "#112233", "#ffffff"}), 10);
        assertEquals(1, one.size(), "a long name still gets its own line");
        assertTrue(LandMap.pack(List.of(), 10).isEmpty());

        Set<String> colours = new HashSet<>();
        for (String[] e : LandMap.fixedLegend()) {
            if (e[1] != null) {
                assertTrue(colours.add(e[1]), "fixed entries have distinct colours");
            }
        }
        assertEquals(4, colours.size());
    }

    @Test
    void claimedCellsUseTheFactionColourWithFallbacks() {
        java.util.UUID id = java.util.UUID.randomUUID();
        dev.ryvrook.dominion.model.Faction f = new dev.ryvrook.dominion.model.Faction(id, "abc", "Alpha", id, 0L);
        f.setColor("#123abc");
        assertEquals("#123abc", LandMap.cellColor(f, Relation.ENEMY.color()));
        f.setColor("purple");
        assertEquals(Relation.ENEMY.color(), LandMap.cellColor(f, Relation.ENEMY.color()));
        f.setType(dev.ryvrook.dominion.model.FactionType.SAFEZONE);
        assertEquals(Land.SAFEZONE, LandMap.cellColor(f, Relation.ENEMY.color()));
        f.setType(dev.ryvrook.dominion.model.FactionType.WARZONE);
        assertEquals(Land.WARZONE, LandMap.cellColor(f, Relation.ENEMY.color()));
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
