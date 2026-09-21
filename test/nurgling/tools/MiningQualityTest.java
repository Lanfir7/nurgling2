package nurgling.tools;

import nurgling.actions.bots.MasterMiner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningQualityTest {
    @Test void keepsMiningMasterCoefficientsAndBoundaryBehavior() {
        assertEquals(288.75, MiningQuality.fromWall(500, 100, 0.8), 1e-9);
        assertEquals(295, MiningQuality.fromWall(500, 100, 0.9), 1e-9);
        assertEquals(300, MiningQuality.fromWall(500, 100, 1), 1e-9);
        assertEquals(90, MiningQuality.fromWall(90, 100, 0.8), 1e-9);
        assertEquals(88.75, MiningQuality.fromWall(100, 100, 0.8), 1e-9);
        assertEquals(300, MiningQuality.fromWall(500, 100, 0), 1e-9);
        assertEquals(500, MiningQuality.wallFromDrop(288.75, 100, 0.8), 1e-9);
        assertEquals(90, MiningQuality.wallFromDrop(90, 100, 0.8), 1e-9);
        assertEquals(MiningQuality.fromWall(543, 37, 0.8), MasterMiner.invDropQ(543, 37, 0.8), 1e-9);
    }
}
