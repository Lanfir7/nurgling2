package nurgling.navigation;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkNavGatesTest {
    @Test
    void gateOpeningStaysPassableWhenAWallBoxCoversIt() {
        assertEquals(ChunkNavGates.GATE, ChunkNavGates.classify(false, false, true));
        assertEquals(ChunkNavGates.GATE, ChunkNavGates.classify(false, true, true));
        assertEquals(ChunkNavGates.BLOCKED, ChunkNavGates.classify(true, false, true));
        assertEquals(ChunkNavGates.WALKABLE, ChunkNavGates.classify(false, false, false));
        assertTrue(ChunkNavGates.isTraversable(ChunkNavGates.GATE));
        assertTrue(!ChunkNavGates.isTraversable(ChunkNavGates.BLOCKED));
    }

    @Test
    void straightStepThroughTheOpeningCrossesAndAMissAlongsideDoesNot() {
        ChunkNavGates.Pose gate = ChunkNavGates.fromHitbox(new Coord2d(0, 0), 0, -1, -8, 1, 8);
        assertNotNull(ChunkNavGates.crossingPoint(new Coord2d(-20, 0), new Coord2d(20, 0), gate));
        assertNull(ChunkNavGates.crossingPoint(new Coord2d(-20, 30), new Coord2d(20, 30), gate));
        assertNull(ChunkNavGates.crossingPoint(new Coord2d(-20, 0), new Coord2d(-5, 0), gate));
    }

    @Test
    void exitStandsOnTheFarSideClearOfTheLeafAndStillInClickRange() {
        double tile = 11;
        ChunkNavGates.Pose gate = ChunkNavGates.fromHitbox(new Coord2d(100, 50), 0, -1, -8, 1, 8);
        Coord2d exit = ChunkNavGates.exitPoint(gate, new Coord2d(80, 50), tile);
        assertTrue(exit.x > gate.center.x + gate.halfThin);
        assertTrue(exit.dist(gate.center) <= tile * ChunkNavGates.MAX_CLICK_TILES + 0.01);
    }
}
