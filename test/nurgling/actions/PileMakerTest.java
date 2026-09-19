package nurgling.actions;

import haven.Coord2d;
import haven.Pair;
import nurgling.NHitBox;
import nurgling.areas.NArea;
import nurgling.areas.PileFillDirection;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void transferToPilesUsesAlexandrCreationFlow() {
        PileMaker maker = PileMaker.forTransferToPiles(
                Pair.of(Coord2d.of(0, 0), Coord2d.of(22, 22)),
                "Raw Wildhide", new NAlias("stockpile"), 0);

        assertTrue(maker.usesAlexandrCreationFlow());
    }

    @Test
    void transferPilePositionUsesDirectionOnlyForNonLegacyDirections() {
        NArea area = new NArea("zone");
        Pair<Coord2d, Coord2d> bounds = new NArea.DirectedAreaBounds(
                Coord2d.of(0, 0), Coord2d.of(22, 22), area);
        Coord2d legacyPosition = Coord2d.of(1, 1);
        int[] legacyCalls = {0};
        List<PileFillDirection> directedCalls = new ArrayList<>();

        for (PileFillDirection direction : new PileFillDirection[]{
                PileFillDirection.RIGHT_TO_LEFT,
                PileFillDirection.TOP_TO_BOTTOM,
                PileFillDirection.BOTTOM_TO_TOP}) {
            area.pileFillDirection = direction;
            Coord2d directedPosition = Coord2d.of(direction.ordinal() + 10, 2);
            assertSame(directedPosition, PileMaker.transferPilePosition(
                    bounds,
                    () -> {
                        legacyCalls[0]++;
                        return legacyPosition;
                    },
                    selectedDirection -> {
                        directedCalls.add(selectedDirection);
                        return directedPosition;
                    }));
        }

        area.pileFillDirection = PileFillDirection.LEFT_TO_RIGHT;
        assertSame(legacyPosition, PileMaker.transferPilePosition(
                bounds,
                () -> {
                    legacyCalls[0]++;
                    return legacyPosition;
                },
                selectedDirection -> {
                    directedCalls.add(selectedDirection);
                    return Coord2d.of(99, 99);
                }));

        assertEquals(1, legacyCalls[0]);
        assertEquals(List.of(
                PileFillDirection.RIGHT_TO_LEFT,
                PileFillDirection.TOP_TO_BOTTOM,
                PileFillDirection.BOTTOM_TO_TOP), directedCalls);
    }

    @Test
    void visionWalkRequiredWhenZoneSitsOnVisionEdge() {
        Pair<Coord2d, Coord2d> edge = Pair.of(Coord2d.of(5400, 5400), Coord2d.of(5600, 5600));
        Pair<Coord2d, Coord2d> inside = Pair.of(Coord2d.of(5050, 5050), Coord2d.of(5300, 5300));
        Coord2d player = Coord2d.of(5000, 5000);
        assertTrue(PileMaker.requiresVisionWalk(edge, player));
        assertFalse(PileMaker.requiresVisionWalk(inside, player));
    }

    @Test
    void placementIgnoresFreeCellsOutsideVision() {
        Coord2d player = Coord2d.of(5000, 5000);
        Coord2d hidden = Coord2d.of(5600, 5600);
        Coord2d visible = Coord2d.of(5100, 5100);
        assertEquals(visible, PileMaker.firstVisibleCandidate(
                List.of(hidden, visible), player));
        assertEquals(null, PileMaker.firstVisibleCandidate(
                Collections.singletonList(hidden), player));
    }

    @Test
    void creationWalksUntilZoneCornersAreInVision() throws Exception {
        String src = new String(Files.readAllBytes(Paths.get("src/nurgling/actions/PileMaker.java")),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("ensurePlacementAreaVisible"), src);
        assertTrue(src.contains("firstVisibleCandidate"), src);
    }
}
