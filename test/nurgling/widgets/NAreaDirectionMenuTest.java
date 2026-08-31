package nurgling.widgets;

import nurgling.areas.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NAreaDirectionMenuTest {
    @Test
    void verticalGlyphsMatchFillDirection() {
        assertEquals(PileFillDirection.TOP_TO_BOTTOM, NAreaDirectionMenu.directionForGlyph("↑"));
        assertEquals(PileFillDirection.BOTTOM_TO_TOP, NAreaDirectionMenu.directionForGlyph("↓"));
        assertEquals(PileFillDirection.RIGHT_TO_LEFT, NAreaDirectionMenu.directionForGlyph("←"));
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, NAreaDirectionMenu.directionForGlyph("→"));
    }

    @Test
    void applyChangesDirectionAndMarksRouting() {
        NArea area = new NArea("zone");

        assertTrue(NAreaDirectionMenu.apply(area, PileFillDirection.TOP_TO_BOTTOM));
        assertEquals(PileFillDirection.TOP_TO_BOTTOM, area.pileFillDirection);
        assertTrue(area.dirtyGroups.contains(AreaFieldGroup.ROUTING));
        assertFalse(NAreaDirectionMenu.apply(area, PileFillDirection.TOP_TO_BOTTOM));
    }
}
