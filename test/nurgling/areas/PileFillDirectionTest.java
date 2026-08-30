package nurgling.areas;

import haven.Coord2d;
import haven.Pair;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PileFillDirectionTest {
    @Test void defaultsToLegacyOrder() {
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, new NArea("old").pileFillDirection);
    }

    @Test void jsonRoundTripPreservesDirection() {
        NArea area = new NArea("zone");
        area.id = 1;
        area.space = new NArea.Space();
        area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
        NArea restored = new NArea(new JSONObject(area.toJson().toString()));
        assertEquals(PileFillDirection.BOTTOM_TO_TOP, restored.pileFillDirection);
    }

    @Test void missingAndUnknownValuesUseLegacyOrder() {
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileFillDirection.fromStored(null));
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileFillDirection.fromStored(""));
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileFillDirection.fromStored("future-value"));
    }

    @Test void setterMarksOnlyRoutingDirty() {
        NArea area = new NArea("zone");
        assertTrue(area.setPileFillDirection(PileFillDirection.RIGHT_TO_LEFT));
        assertEquals(java.util.EnumSet.of(AreaFieldGroup.ROUTING), area.dirtyGroups);
        assertFalse(area.setPileFillDirection(PileFillDirection.RIGHT_TO_LEFT));
    }

    @Test void boundsFactoryKeepsLivePileFillDirection() {
        NArea area = new NArea("zone");
        area.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;

        Pair<Coord2d, Coord2d> bounds = area.directedBounds(
                Coord2d.of(0, 0), Coord2d.of(22, 22));

        assertTrue(bounds instanceof NArea.DirectedAreaBounds);
        assertEquals(PileFillDirection.RIGHT_TO_LEFT,
                ((NArea.DirectedAreaBounds) bounds).direction());
        area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
        assertEquals(PileFillDirection.BOTTOM_TO_TOP,
                ((NArea.DirectedAreaBounds) bounds).direction());
    }
}
