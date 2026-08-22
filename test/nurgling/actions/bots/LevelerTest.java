package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

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
}
