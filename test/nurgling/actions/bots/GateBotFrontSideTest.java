package nurgling.actions.bots;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GateBotFrontSideTest {
    @Test
    void closingStandOffStaysOnApproachingSideOfThinGate() {
        Coord2d front = GateBot.frontOf(new Coord2d(100, 100), 0, true, 3, new Coord2d(80, 100));

        assertEquals(91.5, front.x, 0.001);
        assertEquals(100, front.y, 0.001);
    }

    @Test
    void closingStandOffRotatesWithGate() {
        Coord2d front = GateBot.frontOf(new Coord2d(100, 100), Math.PI / 2, true, 3, new Coord2d(100, 80));

        assertEquals(100, front.x, 0.001);
        assertEquals(91.5, front.y, 0.001);
    }
}
