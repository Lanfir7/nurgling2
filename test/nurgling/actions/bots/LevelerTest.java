package nurgling.actions.bots;

import haven.Coord;
import haven.GItem;
import haven.WItem;
import haven.Widget;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NGItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelerTest {
    @Test
    void leftoverWormsNeedTheirOwnPut() {
        assertFalse(Leveler.soilDisposalComplete(0, 3, 0));
        assertEquals("Leveler: no earthworm PUT area available",
                Leveler.disposalError(0, 3, 0));
    }

    @Test
    void leftoverSoilStillNeedsARoute() {
        assertFalse(Leveler.soilDisposalComplete(2, 0, 0));
        assertEquals("Leveler: no soil disposal route available",
                Leveler.disposalError(2, 0, 0));
    }

    @Test
    void leftoverTubersNeedTheirOwnPut() {
        assertFalse(Leveler.soilDisposalComplete(0, 0, 1));
        assertEquals("Leveler: no Odd Tuber PUT area available",
                Leveler.disposalError(0, 0, 1));
    }

    @Test
    void fillTripTakesOnlyWhatFitsInInventory() {
        assertEquals(120, Leveler.tripSize(26609, 0, 24, 5));
        assertEquals(10, Leveler.tripSize(10, 0, 24, 5));
        assertEquals(0, Leveler.tripSize(26609, 0, 0, 5));
    }

    @Test
    void fillPullsOnlyWhenInventoryHasNoSoil() {
        assertTrue(Leveler.shouldPullSoil(26609, 0));
        assertFalse(Leveler.shouldPullSoil(26609, 4));
        assertFalse(Leveler.shouldPullSoil(0, 0));
    }

    @Test
    void afterSoilTripReturnsToResumeSurveyNotNearest() {
        haven.Coord resume = new haven.Coord(10, 10);
        haven.Coord nearest = new haven.Coord(1, 1);
        assertEquals(resume, Leveler.chooseSurveyTile(resume, nearest, true));
        assertEquals(nearest, Leveler.chooseSurveyTile(resume, nearest, false));
        assertEquals(nearest, Leveler.chooseSurveyTile(null, nearest, true));
    }

    @Test
    void fullInventoryOnFillDoesNotDumpSoil() {
        assertFalse(Leveler.shouldDumpForFreeSpace(true, 0));
        assertTrue(Leveler.shouldDumpForFreeSpace(false, 0));
        assertFalse(Leveler.shouldDumpForFreeSpace(false, 5));
        assertFalse(Leveler.shouldDumpForFreeSpace(false, 10));
        assertFalse(Leveler.shouldDumpForFreeSpace(false, -1));
        assertTrue(Leveler.shouldDumpForFreeSpace(false, 2, 3));
        assertFalse(Leveler.shouldDumpForFreeSpace(false, 3, 3));
    }

    @Test
    void excavationDumpsWormsThenSoilThenTubers() {
        assertEquals("Earthworm", Leveler.excavationDumpOrder()[0]);
        assertEquals("Soil", Leveler.excavationDumpOrder()[1]);
        assertEquals("Odd Tuber", Leveler.excavationDumpOrder()[2]);
    }

    @Test
    void remoteAreaNeedsChunkNavAndLoadedCoords() {
        assertFalse(Leveler.readyToUseRemoteArea(false, true));
        assertFalse(Leveler.readyToUseRemoteArea(true, false));
        assertTrue(Leveler.readyToUseRemoteArea(true, true));
    }

    @Test
    void keepsResumeFlagWhenGobUnloadedIfBookmarkExists() {
        assertTrue(Leveler.shouldKeepResume(true, false, true));
        assertFalse(Leveler.shouldKeepResume(true, false, false));
        assertTrue(Leveler.shouldKeepResume(true, true, false));
        assertFalse(Leveler.shouldKeepResume(false, false, true));
    }

    @Test
    void fillStopsImmediatelyWhenSoilRunsOut() {
        assertTrue(Leveler.shouldFetchMoreSoil(true, true));
        assertFalse(Leveler.shouldFetchMoreSoil(true, false));
        assertFalse(Leveler.shouldFetchMoreSoil(false, true));
        assertTrue(Leveler.isNeedSoilError("You need soil to fill this up."));
        assertFalse(Leveler.isNeedSoilError("cannot be further leveled"));
    }

    @Test
    void surveyWaitEndsWhenWindowOpensOrEightSecondsPass() {
        long start = 17_000_000_000L;
        assertFalse(Leveler.surveyOpenOrTimedOut(false, start, start + 7_999_999_999L));
        assertTrue(Leveler.surveyOpenOrTimedOut(false, start, start + 8_000_000_000L));
        assertTrue(Leveler.surveyOpenOrTimedOut(true, start, start));
    }

    @Test
    void digIdleUsesContinuousElapsedTimeAndResetsOnActivity() {
        Leveler.IdleTimeout idle = new Leveler.IdleTimeout(6_000_000_000L);
        long start = 20_000_000_000L;
        assertFalse(idle.expired(true, start));
        assertFalse(idle.expired(true, start + 5_999_999_999L));
        assertFalse(idle.expired(false, start + 6_000_000_000L));
        assertFalse(idle.expired(true, start + 10_000_000_000L));
        assertFalse(idle.expired(true, start + 15_999_999_999L));
        assertTrue(idle.expired(true, start + 16_000_000_000L));
        Leveler.IdleTimeout fill = new Leveler.IdleTimeout(350_000_000L);
        assertFalse(fill.expired(true, start));
        assertTrue(fill.expired(true, start + 350_000_000L));
    }

    @Test
    void soilDumpSelectsOnlyVerifiedSoilIncludingPureStacks() {
        Widget inventory = new Widget();
        WItem soil = namedItem("Soil");
        WItem food = namedItem("Bread");
        WItem tool = namedItem("Shovel");
        WItem similar = namedItem("Rich Soil");
        WItem unresolved = namedItem(null);
        ItemStack pure = stack("Soil", "Soil");
        ItemStack mixed = stack("Soil", "Bread");
        ItemStack unknown = stack("Soil", null);
        ItemStack foodStack = stack("Bread", "Cheese");
        WItem wrappedPure = namedItem("Soil");
        wrappedPure.item.contents = stack("Soil", "Soil");
        WItem wrappedMixed = namedItem("Soil");
        wrappedMixed.item.contents = stack("Soil", "Bread");
        WItem beltSoil = namedItem("Soil");
        WItem beltFood = namedItem("Bread");
        WItem beltMixed = namedItem("Soil");
        beltMixed.item.contents = stack("Soil", "Bread");
        Widget belt = new Widget();
        belt.add(beltSoil, Coord.z);
        belt.add(beltFood, Coord.z);
        belt.add(beltMixed, Coord.z);
        for (Widget item : Arrays.asList(soil, food, tool, similar, unresolved,
                pure, mixed, unknown, foodStack, wrappedPure, wrappedMixed)) {
            inventory.add(item, Coord.z);
        }
        Set<Widget> targets = new HashSet<>(Leveler.soilDropTargets(inventory,
                Arrays.asList(beltSoil, beltFood, beltMixed)));
        Set<Widget> expected = new HashSet<>(Arrays.asList(soil, wrappedPure, beltSoil));
        expected.addAll(pure.wmap.values());
        assertEquals(expected, targets);
        assertTrue(Leveler.soilDisposalComplete(0, 0, 0));
    }

    private static WItem namedItem(String name) {
        GItem item = new NGItem(null) {
            @Override public String name() { return name; }
        };
        return new WItem(item);
    }

    private static ItemStack stack(String... names) {
        ItemStack stack = new ItemStack();
        for (String name : names) stack.addchild(namedItem(name).item);
        return stack;
    }
}
