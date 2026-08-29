package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NHitBoxCustomTest {
    @Test
    void hiddenHollowHasAHitboxForPathfinding() {
        NHitBox box = NHitBox.findCustom("gfx/terobjs/map/hiddenhollow");
        assertNotNull(box);
        assertEquals(33.0, box.end.x - box.begin.x, 0.01);
        assertEquals(33.0, box.end.y - box.begin.y, 0.01);
    }

    @Test
    void stonekistHasAHitboxForPathfinding() {
        assertNotNull(NHitBox.findCustom("gfx/terobjs/map/stonekist"));
    }

    @Test
    void unknownMapObjectStaysWithoutCustomHitbox() {
        assertNull(NHitBox.findCustom("gfx/terobjs/map/not-a-real-object"));
    }

    @Test
    void wildHorseUsesSameHitboxAsStallion() {
        NHitBox wild = NHitBox.findCustom("gfx/kritter/horse/horse");
        NHitBox stallion = NHitBox.findCustom("gfx/kritter/horse/stallion");
        assertNotNull(wild);
        assertEquals(stallion.end.x - stallion.begin.x, wild.end.x - wild.begin.x, 0.01);
        assertEquals(stallion.end.y - stallion.begin.y, wild.end.y - wild.begin.y, 0.01);
    }

    @Test
    void boughPyreHasAHitboxForPathfinding() {
        NHitBox box = NHitBox.findCustom("gfx/terobjs/bpyre");
        assertNotNull(box);
        assertEquals(10.0, box.end.x - box.begin.x, 0.01);
        assertEquals(10.0, box.end.y - box.begin.y, 0.01);
    }

    @Test
    void timberTunnelHasForceZeroHitbox() {
        assertForceZeroHitbox("gfx/terobjs/timbertunnel");
    }

    @Test
    void reinforcedTunnelHasForceZeroHitbox() {
        assertForceZeroHitbox("gfx/terobjs/reinforcedtunnel");
    }

    @Test
    void stoneArchTunnelHasForceZeroHitbox() {
        assertForceZeroHitbox("gfx/terobjs/stonearchtunnel");
    }

    private static void assertForceZeroHitbox(String name) {
        NHitBox box = NHitBox.findCustom(name);
        assertNotNull(box);
        assertEquals(0.0, box.begin.x, 0.01);
        assertEquals(0.0, box.begin.y, 0.01);
        assertEquals(0.0, box.end.x, 0.01);
        assertEquals(0.0, box.end.y, 0.01);
    }
}
