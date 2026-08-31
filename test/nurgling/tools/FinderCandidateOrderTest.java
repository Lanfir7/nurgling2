package nurgling.tools;

import haven.Coord2d;
import haven.Pair;
import nurgling.NHitBox;
import nurgling.areas.PileFillDirection;
import nurgling.pf.NHitBoxD;
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

    @Test
    void densePlacementRecoversImmediatelyAfterOffGridPileColumns() {
        NHitBox pile = new NHitBox(Coord2d.of(-2.5, -2.5),
                Coord2d.of(2.5, 2.5), true);
        List<NHitBoxD> offGridColumn = Arrays.asList(
                new NHitBoxD(pile.begin, pile.end, Coord2d.of(3.5, 2.5), 0),
                new NHitBoxD(pile.begin, pile.end, Coord2d.of(3.5, 7.5), 0));

        List<Coord2d> candidates = Finder.collectDenseFreePlaces(
                new Pair<>(Coord2d.of(0, 0), Coord2d.of(20, 10)),
                pile, 0, PileFillDirection.LEFT_TO_RIGHT, offGridColumn);

        assertEquals(Coord2d.of(8.5, 2.5), candidates.get(0));
    }

    @Test
    void fiveWidePileUsesActualNeighbourEdgeInsteadOfLeavingOneUnitGap() {
        NHitBox pile = new NHitBox(Coord2d.of(-2.5, -2.5),
                Coord2d.of(2.5, 2.5), true);
        List<NHitBoxD> serverShiftedColumn = Arrays.asList(
                new NHitBoxD(pile.begin, pile.end, Coord2d.of(3.75, 2.5), 0),
                new NHitBoxD(pile.begin, pile.end, Coord2d.of(3.75, 7.5), 0));

        List<Coord2d> candidates = Finder.collectDenseFreePlaces(
                new Pair<>(Coord2d.of(0, 0), Coord2d.of(20, 10)),
                pile, 0, PileFillDirection.LEFT_TO_RIGHT, serverShiftedColumn);

        assertEquals(Coord2d.of(8.75, 2.5), candidates.get(0));
    }

    @Test
    void verticalFillUsesActualNeighbourEdgeInsteadOfLeavingOneUnitGap() {
        NHitBox pile = new NHitBox(Coord2d.of(-2.5, -2.5),
                Coord2d.of(2.5, 2.5), true);
        List<NHitBoxD> serverShiftedRow = Arrays.asList(
                new NHitBoxD(pile.begin, pile.end, Coord2d.of(2.5, 3.75), 0),
                new NHitBoxD(pile.begin, pile.end, Coord2d.of(7.5, 3.75), 0));

        List<Coord2d> candidates = Finder.collectDenseFreePlaces(
                new Pair<>(Coord2d.of(0, 0), Coord2d.of(10, 20)),
                pile, 0, PileFillDirection.TOP_TO_BOTTOM, serverShiftedRow);

        assertEquals(Coord2d.of(2.5, 8.75), candidates.get(0));
    }

    @Test
    void oddSizedPilesStartFullyInsideFarZoneEdges() {
        NHitBox pile = new NHitBox(Coord2d.of(-2.5, -2.5),
                Coord2d.of(2.5, 2.5), true);
        Pair<Coord2d, Coord2d> zone = new Pair<>(
                Coord2d.of(0, 0), Coord2d.of(20, 10));

        List<Coord2d> fromRight = Finder.collectDenseFreePlaces(
                zone, pile, 0, PileFillDirection.RIGHT_TO_LEFT,
                java.util.Collections.emptyList());
        List<Coord2d> fromBottom = Finder.collectDenseFreePlaces(
                zone, pile, 0, PileFillDirection.BOTTOM_TO_TOP,
                java.util.Collections.emptyList());

        assertEquals(Coord2d.of(17.5, 2.5), fromRight.get(0));
        assertEquals(Coord2d.of(2.5, 7.5), fromBottom.get(0));
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
