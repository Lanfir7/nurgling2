package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinMinerHelpersTest {

    @Test
    void seedWaitCompleteWhenSeedCaptured() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();
        cap.offer(new Coord(1, 2), null);

        assertTrue(VeinMiner.seedWaitComplete(cap, "mine"));
    }

    @Test
    void seedWaitCompleteWhenCursorLeavesMine() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();

        assertFalse(VeinMiner.seedWaitComplete(cap, null));
        assertFalse(VeinMiner.seedWaitComplete(cap, "mine"));
        assertTrue(VeinMiner.seedWaitComplete(cap, "arw"));
        assertTrue(VeinMiner.seedWaitComplete(cap, "hand"));
    }

    @Test
    void stopsMiningWhenWallHasChangedToFloor() {
        String ore = "gfx/tiles/rocks/cassiterite";

        assertTrue(VeinMiner.isTargetTile(ore, ore));
        assertFalse(VeinMiner.isTargetTile(ore, "gfx/tiles/cave"));
        assertFalse(VeinMiner.isTargetTile(ore, null));
    }

    @Test
    void veinRocksAreMineableWallTiles() {
        assertTrue(VeinMiner.isVeinRock("gfx/tiles/rocks/cassiterite"));
        assertTrue(VeinMiner.isVeinRock("gfx/tiles/rocks/granite"));
        assertFalse(VeinMiner.isVeinRock("gfx/tiles/cave"));
        assertFalse(VeinMiner.isVeinRock(null));
    }

    @Test
    void mineCursorIsThePickaxeCursor() {
        assertTrue(VeinMiner.isMineCursor("gfx/hud/curs/mine"));
        assertTrue(VeinMiner.isMineCursor("mine"));
        assertFalse(VeinMiner.isMineCursor("gfx/hud/curs/arw"));
        assertFalse(VeinMiner.isMineCursor(null));
    }
}
