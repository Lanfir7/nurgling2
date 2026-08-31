package nurgling.actions;

import haven.Coord2d;
import haven.Pair;
import nurgling.NHitBox;
import nurgling.areas.NArea;
import nurgling.areas.PileFillDirection;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PileMakerTest {
    @Test
    void soilHitboxWorksWithoutLoadedPlobGhost() {
        NHitBox box = PileMaker.resolveHitbox(null, new NAlias("gfx/terobjs/stockpile-soil"));
        assertNotNull(box);
        assertEquals(NHitBox.findCustom("gfx/terobjs/stockpile-soil").begin, box.begin);
        assertEquals(NHitBox.findCustom("gfx/terobjs/stockpile-soil").end, box.end);
    }

    @Test
    void prefersReadyPlobHitbox() {
        NHitBox fromPlob = new NHitBox(new haven.Coord(-1, -1), new haven.Coord(1, 1), true);
        assertSame(fromPlob, PileMaker.resolveHitbox(fromPlob, new NAlias("gfx/terobjs/stockpile-soil")));
    }

    @Test
    void unknownPileFallsBackToGenericStockpile() {
        NHitBox box = PileMaker.resolveHitbox(null, new NAlias("stockpile"));
        assertNotNull(box);
        assertEquals(NHitBox.findCustom("stockpile").begin, box.begin);
    }

    @Test
    void closesStockpileWindowBeforeTakeToHand() {
        assertTrue(PileMaker.shouldCloseStockpileBeforeTakeToHand(true));
        assertFalse(PileMaker.shouldCloseStockpileBeforeTakeToHand(false));
    }

    @Test
    void neverSkipsTheFirstFreeSlotBecauseOfThePlayersPosition() throws InterruptedException {
        Coord2d first = Coord2d.of(10, 10);
        Coord2d later = Coord2d.of(20, 10);
        List<Coord2d> attempted = new java.util.ArrayList<>();

        Coord2d selected = PileMaker.firstSafeCandidate(
                Arrays.asList(first, later), candidate -> {
                    attempted.add(candidate);
                    return false;
                });

        assertNull(selected);
        assertEquals(Arrays.asList(first), attempted);
    }

    @Test
    void currentPlayerTileRemainsCandidateWhenMovingAwayKeepsAnEscape() throws InterruptedException {
        Coord2d playerTile = Coord2d.of(10, 10);

        Coord2d selected = PileMaker.firstSafeCandidate(
                Arrays.asList(playerTile), candidate -> true);

        assertEquals(playerTile, selected);
    }

    @Test
    void exitsPreviousPileThroughTheFreeStartChosenByPathfinder() throws InterruptedException {
        Coord2d freeStart = Coord2d.of(5.5, -2.75);
        List<Coord2d> directMoves = new java.util.ArrayList<>();

        boolean exited = PileMaker.exitStartObstacle(
                freeStart,
                target -> {
                    directMoves.add(target);
                    return true;
                });

        assertTrue(exited);
        assertEquals(Arrays.asList(freeStart), directMoves);
    }

    @Test
    void failedApproachExitsThenRetriesTheSameTargetOnce() throws InterruptedException {
        List<String> steps = new java.util.ArrayList<>();
        int[] approaches = {0};

        boolean reached = PileMaker.retryAfterExit(
                () -> {
                    steps.add("approach");
                    return approaches[0]++ == 1;
                },
                () -> {
                    steps.add("exit");
                    return true;
                });

        assertTrue(reached);
        assertEquals(Arrays.asList("approach", "exit", "approach"), steps);
    }

    @Test
    void failedRetryStopsInsteadOfLoopingAlongThePiles() throws InterruptedException {
        List<String> steps = new java.util.ArrayList<>();

        boolean reached = PileMaker.retryAfterExit(
                () -> {
                    steps.add("approach");
                    return false;
                },
                () -> {
                    steps.add("exit");
                    return true;
                });

        assertFalse(reached);
        assertEquals(Arrays.asList("approach", "exit", "approach"), steps);
    }

    @Test
    void exactPlacementBuildsANonDegenerateEscapeEnvelopeAroundPlayerAndPile() {
        Pair<Coord2d, Coord2d> envelope = PileMaker.escapeEnvelope(
                Coord2d.of(0, 0), Coord2d.of(20, 10), 50);

        assertEquals(Coord2d.of(-50, -50), envelope.a);
        assertEquals(Coord2d.of(70, 60), envelope.b);
    }

    @Test
    void escapeTargetsCoverEverySideOutsideThePlacementArea() {
        List<Coord2d> targets = PileMaker.escapeTargets(
                new Pair<>(Coord2d.of(0, 0), Coord2d.of(22, 22)), 11, 11);

        assertTrue(targets.contains(Coord2d.of(0, -11)));
        assertTrue(targets.contains(Coord2d.of(22, -11)));
        assertTrue(targets.contains(Coord2d.of(33, 0)));
        assertTrue(targets.contains(Coord2d.of(33, 22)));
        assertTrue(targets.contains(Coord2d.of(22, 33)));
        assertTrue(targets.contains(Coord2d.of(0, 33)));
        assertTrue(targets.contains(Coord2d.of(-11, 22)));
        assertTrue(targets.contains(Coord2d.of(-11, 0)));
    }

    @Test
    void zoneBoundsExposeLiveDirection() {
        NArea area = new NArea("zone");
        area.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;
        Pair<Coord2d, Coord2d> bounds = new NArea.DirectedAreaBounds(
                Coord2d.of(0, 0), Coord2d.of(22, 22), area);
        assertEquals(PileFillDirection.RIGHT_TO_LEFT, PileMaker.directionFor(bounds));
        area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
        assertEquals(PileFillDirection.BOTTOM_TO_TOP, PileMaker.directionFor(bounds));
    }

    @Test
    void plainBoundsUseLegacyDirection() {
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileMaker.directionFor(
                Pair.of(Coord2d.of(0, 0), Coord2d.of(22, 22))));
    }
}
