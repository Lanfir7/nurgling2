package haven;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilestonePlacementAdvisorTest {
    @Test
    void recognizesOnlyWoodAndStoneMilestonePlacementResources() {
        assertTrue(MilestonePlacementAdvisor.isMilestoneResource("gfx/terobjs/road/milestone-wood-m"));
        assertTrue(MilestonePlacementAdvisor.isMilestoneResource("gfx/terobjs/road/milestone-stone-e"));
        assertFalse(MilestonePlacementAdvisor.isMilestoneResource("gfx/terobjs/road/road"));
        assertFalse(MilestonePlacementAdvisor.isMilestoneResource("gfx/terobjs/road/milestone-bronze-m"));
    }

    @Test
    void choosesNearestSuitableTileInsideFifteenTileDiameterAndKeepsRotation() {
        Coord2d origin = Coord2d.of(100, 200);
        Optional<Coord2d> suggestion = MilestonePlacementAdvisor.findNearest(
                origin, 11.0, Math.PI / 2.0,
                (candidate, angle) -> candidate.equals(Coord2d.of(100, 211)) && angle == Math.PI / 2.0);

        assertEquals(Coord2d.of(100, 211), suggestion.orElseThrow());
    }

    @Test
    void searchesThroughSevenAndHalfTilesButNotBeyond() {
        Coord2d origin = Coord2d.of(100, 200);
        Optional<Coord2d> withinRange = MilestonePlacementAdvisor.findNearest(
                origin, 11.0, 0.0,
                (candidate, angle) -> candidate.equals(Coord2d.of(177, 200)));
        Optional<Coord2d> beyondRange = MilestonePlacementAdvisor.findNearest(
                origin, 11.0, 0.0,
                (candidate, angle) -> candidate.equals(Coord2d.of(188, 200)));

        assertEquals(Coord2d.of(177, 200), withinRange.orElseThrow());
        assertFalse(beyondRange.isPresent());
    }

    @Test
    void snapsPlacementOnlyWhenClickIsWithinOneTileOfSuggestion() {
        Coord2d suggestion = Coord2d.of(111, 200);

        assertEquals(suggestion, MilestonePlacementAdvisor.snapIfNear(
                Coord2d.of(100, 200), suggestion, 11.0));
        assertEquals(Coord2d.of(99.9, 200), MilestonePlacementAdvisor.snapIfNear(
                Coord2d.of(99.9, 200), suggestion, 11.0));
    }
}
