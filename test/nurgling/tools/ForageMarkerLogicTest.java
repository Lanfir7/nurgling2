package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ForageMarkerLogicTest {
    @Test
    void pickAcceptedPickUpRejected() {
        assertTrue(ForageMarkerLogic.isPickAction("Pick"));
        assertFalse(ForageMarkerLogic.isPickAction("Pick up"));
        assertFalse(ForageMarkerLogic.isPickAction("Harvest"));
        assertFalse(ForageMarkerLogic.isPickAction(null));
    }

    @Test
    void gardenPotByName() {
        assertTrue(ForageMarkerLogic.isGardenPot("gfx/terobjs/gardenpot"));
        assertTrue(ForageMarkerLogic.isGardenPot("GardenPot"));
        assertFalse(ForageMarkerLogic.isGardenPot("gfx/terobjs/herbs/blueberry"));
        assertFalse(ForageMarkerLogic.isGardenPot(null));
    }

    @Test
    void qualityThreshold() {
        assertTrue(ForageMarkerLogic.shouldPlace(40f));
        assertTrue(ForageMarkerLogic.shouldPlace(40.1f));
        assertFalse(ForageMarkerLogic.shouldPlace(39.9f));
        assertFalse(ForageMarkerLogic.shouldPlace(null));
    }

    @Test
    void forageIdPrefix() {
        assertTrue(ForageMarkerLogic.isForageId("forage_1_2_3_Blueberries"));
        assertFalse(ForageMarkerLogic.isForageId("animal_1"));
        assertFalse(ForageMarkerLogic.isForageId("labeled_1_2_3_q20"));
        assertFalse(ForageMarkerLogic.isForageId(null));
    }

    @Test
    void labelFormatAndParse() {
        assertEquals("q45", ForageMarkerLogic.formatLabel(45.4));
        assertEquals(45.0, ForageMarkerLogic.parseQuality("q45"), 0.01);
        assertEquals(0.0, ForageMarkerLogic.parseQuality("nope"), 0.01);
        assertEquals(0.0, ForageMarkerLogic.parseQuality(null), 0.01);
    }

    @Test
    void forageLocationIdStartsWithPrefix() {
        String id = ForageMarkerLogic.forageLocationId(9, 1, 2, "Blueberries");
        assertTrue(id.startsWith("forage_"));
        assertTrue(id.contains("Blueberries") || id.contains("Blueberr"));
    }

    @Test
    void mapSearchNameOrQuality() {
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", ""));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "blue"));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "q45"));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "45"));
        assertFalse(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "chantrelle"));
    }

    @Test
    void windowSearchTypeAndMinQuality() {
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", null));
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Blueberries", 40.0));
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", 45.0));
        assertFalse(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Chantrelles", null));
        assertFalse(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", 46.0));
    }

    @Test
    void dedupKeepsHigherQualityInsideRadiusList() {
        ForageMarkerLogic.Neighbor weak = new ForageMarkerLogic.Neighbor("forage_old", 30);
        ForageMarkerLogic.Dedup better = ForageMarkerLogic.decideDedup(50, Collections.singletonList(weak));
        assertFalse(better.skip);
        assertEquals(Collections.singletonList("forage_old"), better.removeIds);

        ForageMarkerLogic.Neighbor strong = new ForageMarkerLogic.Neighbor("forage_hi", 60);
        ForageMarkerLogic.Dedup worse = ForageMarkerLogic.decideDedup(50, Collections.singletonList(strong));
        assertTrue(worse.skip);
        assertTrue(worse.removeIds.isEmpty());

        ForageMarkerLogic.Dedup many = ForageMarkerLogic.decideDedup(50, Arrays.asList(
                new ForageMarkerLogic.Neighbor("a", 20),
                new ForageMarkerLogic.Neighbor("b", 35)));
        assertFalse(many.skip);
        assertEquals(Arrays.asList("a", "b"), many.removeIds);

        ForageMarkerLogic.Dedup none = ForageMarkerLogic.decideDedup(50, Collections.emptyList());
        assertFalse(none.skip);
        assertTrue(none.removeIds.isEmpty());
    }

    @Test
    void playerInventoriesAcceptedContainersRejected() {
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(true, false));
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(true, true));
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(false, false));
        assertFalse(ForageMarkerLogic.acceptsPickedItemInventory(false, true));
    }
}
