package nurgling.widgets;

import nurgling.areas.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NAreaDirectionMenuTest {
    @Test
    void applyChangesDirectionAndMarksRouting() {
        NArea area = new NArea("zone");

        assertTrue(NAreaDirectionMenu.apply(area, PileFillDirection.TOP_TO_BOTTOM));
        assertEquals(PileFillDirection.TOP_TO_BOTTOM, area.pileFillDirection);
        assertTrue(area.dirtyGroups.contains(AreaFieldGroup.ROUTING));
        assertFalse(NAreaDirectionMenu.apply(area, PileFillDirection.TOP_TO_BOTTOM));
    }
}
