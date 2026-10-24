package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FindQualityThresholdTest {
    @Test
    void laterCupboardCanDisplaceLowerQualityCopiesFromFirstCupboard() {
        // First cupboard contains three q10 eggs; the second holds q90 and q80.
        Float threshold = FindQualityThreshold.selectThreshold(
                Arrays.asList(10f, 10f, 10f, 90f, 80f), 2);

        assertEquals(80f, threshold);
        assertFalse(FindQualityThreshold.eligible(10f, threshold));
        assertTrue(FindQualityThreshold.eligible(90f, threshold));
        assertTrue(FindQualityThreshold.eligible(80f, threshold));
    }

    @Test
    void stackedUnitsCountSeparatelyAndBoundaryTiesAreEligible() {
        Float threshold = FindQualityThreshold.selectThreshold(
                Arrays.asList(55f, 55f, 55f, 70f, 55f), 3);

        assertEquals(55f, threshold);
        assertTrue(FindQualityThreshold.eligible(55f, threshold));
        assertFalse(FindQualityThreshold.eligible(54f, threshold));
    }

    @Test
    void missingOrNonFiniteQualityCannotBeRankedOrTransferred() {
        assertNull(FindQualityThreshold.selectThreshold(Arrays.asList(90f, null), 1));
        assertNull(FindQualityThreshold.selectThreshold(Arrays.asList(90f, Float.NaN), 1));
        assertFalse(FindQualityThreshold.eligible(null, 10f));
        assertFalse(FindQualityThreshold.eligible(Float.POSITIVE_INFINITY, 10f));
    }

    @Test
    void availableCopiesAllRemainEligibleWhenDemandExceedsStock() {
        Float threshold = FindQualityThreshold.selectThreshold(Arrays.asList(20f, 40f), 16);
        assertEquals(20f, threshold);
        assertTrue(FindQualityThreshold.eligible(20f, threshold));
    }

    @Test
    void productionPlanTakesBestUnitsAcrossCabinetsOverMultipleLoads() {
        FindQualityThreshold.QualityPlan plan = new FindQualityThreshold.QualityPlan(
                Arrays.asList(
                        new FindQualityThreshold.RankedCopy(1, 10f),
                        new FindQualityThreshold.RankedCopy(1, 10f),
                        new FindQualityThreshold.RankedCopy(2, 90f),
                        new FindQualityThreshold.RankedCopy(2, 80f),
                        new FindQualityThreshold.RankedCopy(1, 70f)), 70f);

        List<FindQualityThreshold.RankedCopy> firstLoad = plan.next(2);
        assertEquals(90f, firstLoad.get(0).quality);
        assertEquals(80f, firstLoad.get(1).quality);
        plan.confirmed(firstLoad.get(0));
        plan.confirmed(firstLoad.get(1));

        List<FindQualityThreshold.RankedCopy> nextLoad = plan.next(2);
        assertEquals(1, nextLoad.size());
        assertEquals(70f, nextLoad.get(0).quality);
        plan.confirmed(nextLoad.get(0));
        assertTrue(plan.next(2).isEmpty());
    }

    @Test
    void unconfirmedCopyIsStillScheduledAfterPartialTransfer() {
        FindQualityThreshold.QualityPlan plan = new FindQualityThreshold.QualityPlan(
                Arrays.asList(new FindQualityThreshold.RankedCopy(1, 90f),
                        new FindQualityThreshold.RankedCopy(2, 80f)), 80f);
        List<FindQualityThreshold.RankedCopy> firstLoad = plan.next(2);
        plan.confirmed(firstLoad.get(0));

        assertSame(firstLoad.get(1), plan.next(2).get(0));
    }
}
