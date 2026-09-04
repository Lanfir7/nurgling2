package nurgling.areas;

import haven.Coord2d;
import haven.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NContextRoutingTest {

    @Test
    void geometricFallbackMeasuresDistanceToNearestAreaEdge() {
        Pair<Coord2d, Coord2d> area = new Pair<>(
                new Coord2d(5, 0), new Coord2d(10, 10));

        assertEquals(5.0, NContext.distanceToAreaGeometry(new Coord2d(0, 5), area), 0.001);
        assertEquals(0.0, NContext.distanceToAreaGeometry(new Coord2d(7, 7), area), 0.001);
    }

    @Test
    void geometricFallbackIsNormalizedToTileCostUnits() {
        Pair<Coord2d, Coord2d> area = new Pair<>(
                new Coord2d(22, 0), new Coord2d(33, 11));

        assertEquals(2.0, NContext.distanceToAreaGeometryInTiles(
                new Coord2d(0, 5), area, 11.0), 0.001);
    }

    @Test
    void cheapRouteScoreUsesGeometryOnlyAsChunkHopTieBreaker() {
        assertTrue(NContext.routingScore(1, 9999.0)
                < NContext.routingScore(2, 0.0));
        assertTrue(NContext.routingScore(1, 20.0)
                < NContext.routingScore(1, 30.0));
    }

    @Test
    void visibleFallbackAppliesOnlyToUnrecordedTargetGrid() {
        assertEquals(Integer.MAX_VALUE,
                NContext.visibleFallbackHops(true, false));
        assertEquals(0, NContext.visibleFallbackHops(true, true));
        assertEquals(Integer.MAX_VALUE,
                NContext.visibleFallbackHops(false, true));
    }

    @Test
    void missingPlayerPositionCannotRankAreasByDistance() {
        Pair<Coord2d, Coord2d> area = new Pair<>(
                new Coord2d(5, 0), new Coord2d(10, 10));

        assertEquals(Double.MAX_VALUE,
                NContext.distanceToAreaCorners(null, area));
    }

    @Test
    void loadedPlayerPositionKeepsExistingCornerSumRanking() {
        Pair<Coord2d, Coord2d> area = new Pair<>(
                new Coord2d(3, 4), new Coord2d(6, 8));

        assertEquals(15.0,
                NContext.distanceToAreaCorners(new Coord2d(0, 0), area), 0.001);
    }
}
