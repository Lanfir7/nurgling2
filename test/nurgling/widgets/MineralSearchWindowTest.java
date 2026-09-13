package nurgling.widgets;

import haven.Coord;
import nurgling.conf.ProspectKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralSearchWindowTest {

    @Test
    void kindsAreOnlyOreGemStone() {
        assertArrayEquals(
                new ProspectKind[] {ProspectKind.ORE, ProspectKind.GEM, ProspectKind.STONE},
                MineralSearch.KINDS);
    }

    @Test
    void oreOnlyKeepsOres() {
        assertTypes(MineralSearch.filter(sampleMarks(), ProspectKind.ORE, "Any", null),
                "Cassiterite", "Iron Ochre");
    }

    @Test
    void gemOnlyKeepsGems() {
        assertTypes(MineralSearch.filter(sampleMarks(), ProspectKind.GEM, "Any", null),
                "Onyx");
    }

    @Test
    void stoneOnlyKeepsStones() {
        assertTypes(MineralSearch.filter(sampleMarks(), ProspectKind.STONE, "Any", null),
                "Granite");
    }

    @Test
    void anyKeepsOreGemAndStoneRichestFirst() {
        List<LabeledMinimapMark> found = MineralSearch.filter(sampleMarks(), null, "Any", null);
        assertTypes(found, "Onyx", "Cassiterite", "Granite", "Iron Ochre");
        assertEquals(90.0, found.get(0).quality);
        assertEquals(80.0, found.get(1).quality);
        assertEquals(60.0, found.get(2).quality);
        assertEquals(40.0, found.get(3).quality);
    }

    @Test
    void typeMatchKeepsExactResourceType() {
        assertTypes(MineralSearch.filter(sampleMarks(), ProspectKind.ORE, "Cassiterite", null),
                "Cassiterite");
    }

    @Test
    void qualityThresholdDropsLowerMarks() {
        assertTypes(MineralSearch.filter(sampleMarks(), null, "Any", 50.0),
                "Onyx", "Cassiterite", "Granite");
        assertTypes(MineralSearch.filter(sampleMarks(), null, "Any", 60.0),
                "Onyx", "Cassiterite", "Granite");
        assertTypes(MineralSearch.filter(sampleMarks(), null, "Any", 61.0),
                "Onyx", "Cassiterite");
    }

    @Test
    void quarryartzClayAnimalAndForageAreExcluded() {
        List<String> types = typesOf(MineralSearch.filter(sampleMarks(), null, "Any", null));
        assertTrue(types.stream().noneMatch(t ->
                t.equals("Quarryartz") || t.equals("Clay") || t.equals("Water")
                        || t.equals("Fox") || t.equals("Morels")));
    }

    private static List<LabeledMinimapMark> sampleMarks() {
        return Arrays.asList(
                mark("q80", "Cassiterite", 80.0, 1, 1),
                mark("q40", "Iron Ochre", 40.0, 2, 2),
                mark("q90", "Onyx", 90.0, 3, 3),
                mark("q60", "Granite", 60.0, 4, 4),
                mark("q101", "Quarryartz", 101.0, 5, 5),
                mark("q30", "Clay", 30.0, 6, 6),
                mark("q20", "Water", 20.0, 7, 7),
                new LabeledMinimapMark("animal_99", "q12", "Fox", 1L, new Coord(8, 8), null, null),
                new LabeledMinimapMark("forage_1_9_9_Morels_1", "q55", "Morels", 1L, new Coord(9, 9), null, null)
        );
    }

    private static LabeledMinimapMark mark(String label, String resourceType, double quality, int x, int y) {
        return new LabeledMinimapMark(label, resourceType, quality, 1L, new Coord(x, y), null);
    }

    private static void assertTypes(List<LabeledMinimapMark> found, String... expected) {
        assertEquals(Arrays.asList(expected), typesOf(found));
    }

    private static List<String> typesOf(List<LabeledMinimapMark> found) {
        return found.stream().map(mark -> mark.resourceType).collect(Collectors.toList());
    }
}
