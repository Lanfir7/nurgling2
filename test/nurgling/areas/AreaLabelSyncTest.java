package nurgling.areas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AreaLabelSyncTest {
    @Test
    void labelsLiveIffToggleOrEditor() {
        assertFalse(AreaLabelSync.labelsShouldBeLive(false, false));
        assertTrue(AreaLabelSync.labelsShouldBeLive(true, false));
        assertTrue(AreaLabelSync.labelsShouldBeLive(false, true));
        assertTrue(AreaLabelSync.labelsShouldBeLive(true, true));
    }

    @Test
    void toggleOnOnlyTrue() {
        assertFalse(AreaLabelSync.toggleOn(null));
        assertFalse(AreaLabelSync.toggleOn(false));
        assertTrue(AreaLabelSync.toggleOn(true));
    }

    @Test
    void labelsClickableOnlyWhenEditorOpen() {
        assertFalse(AreaLabelSync.labelsClickable(false));
        assertTrue(AreaLabelSync.labelsClickable(true));
    }

    @Test
    void decideCreateSkipRemove() {
        assertEquals(AreaLabelSync.Action.CREATE, AreaLabelSync.decide(true, true, false));
        assertEquals(AreaLabelSync.Action.SKIP, AreaLabelSync.decide(true, true, true));
        assertEquals(AreaLabelSync.Action.SKIP, AreaLabelSync.decide(true, false, false));
        assertEquals(AreaLabelSync.Action.REMOVE, AreaLabelSync.decide(false, true, true));
        assertEquals(AreaLabelSync.Action.REMOVE, AreaLabelSync.decide(false, false, true));
    }
}
