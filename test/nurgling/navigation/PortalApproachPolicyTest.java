package nurgling.navigation;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalApproachPolicyTest {
    @Test
    void interiorBuildingDoorUsesRecordedApproachTile() {
        assertTrue(PortalApproachPolicy.usesRecordedTile("gfx/terobjs/arch/stonestead-door"));
        assertTrue(PortalApproachPolicy.usesRecordedTile("gfx/terobjs/arch/STONEMANSION-DOOR"));
    }

    @Test
    void otherPortalsKeepTheirExistingApproachLogic() {
        assertFalse(PortalApproachPolicy.usesRecordedTile("gfx/terobjs/arch/stonestead"));
        assertFalse(PortalApproachPolicy.usesRecordedTile("gfx/terobjs/arch/stonestead-door-open"));
        assertFalse(PortalApproachPolicy.usesRecordedTile("gfx/terobjs/cellardoor"));
        assertFalse(PortalApproachPolicy.usesRecordedTile(null));
    }

    @Test
    void recordedTileMustBeNearTheLiveDoor() {
        String door = "gfx/terobjs/arch/stonestead-door";
        Coord2d liveDoor = Coord2d.of(100, 100);
        assertTrue(PortalApproachPolicy.usesRecordedTile(
                door, Coord2d.of(111, 100), liveDoor, 11.0));
        assertFalse(PortalApproachPolicy.usesRecordedTile(
                door, Coord2d.of(650, 650), liveDoor, 11.0));
        assertFalse(PortalApproachPolicy.usesRecordedTile(door, null, liveDoor, 11.0));
    }
}
