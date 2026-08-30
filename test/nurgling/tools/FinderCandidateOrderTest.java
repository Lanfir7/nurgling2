package nurgling.tools;

import haven.Coord2d;
import nurgling.areas.PileFillDirection;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinderCandidateOrderTest {
    @Test
    void preventsWaitPileNoSuchMethodErrorForFinderCoord2dLookup() throws Throwable {
        MethodHandles.publicLookup().findStatic(Finder.class, "findGobs",
                MethodType.methodType(ArrayList.class, Coord2d.class));
    }

    @Test
    void leftToRightUsesLegacyColumnOrder() {
        assertEquals(Arrays.asList(c(1,10), c(1,20), c(2,10), c(2,20)),
                Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.LEFT_TO_RIGHT));
    }

    @Test
    void rightToLeftReversesColumnsOnly() {
        assertEquals(Arrays.asList(c(2,10), c(2,20), c(1,10), c(1,20)),
                Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.RIGHT_TO_LEFT));
    }

    @Test
    void topToBottomUsesRows() {
        assertEquals(Arrays.asList(c(1,10), c(2,10), c(1,20), c(2,20)),
                Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.TOP_TO_BOTTOM));
    }

    @Test
    void bottomToTopReversesRowsOnly() {
        assertEquals(Arrays.asList(c(1,20), c(2,20), c(1,10), c(2,10)),
                Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.BOTTOM_TO_TOP));
    }

    @Test
    void placementCandidateOffsetsUseTheStockpileFootprintStride() {
        assertEquals(Arrays.asList(5.0, 15.0, 25.0, 29.0),
                Finder.candidateOffsets(5, 29, 10));
    }

    private static List<Double> xs() {
        return Arrays.asList(1.0, 2.0);
    }

    private static List<Double> ys() {
        return Arrays.asList(10.0, 20.0);
    }

    private static Coord2d c(double x, double y) {
        return Coord2d.of(x, y);
    }
}
