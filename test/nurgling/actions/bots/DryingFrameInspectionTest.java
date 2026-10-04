package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DryingFrameInspectionTest {
    @Test
    void greenEmptyMaskIsSkipped() {
        assertTrue(DryingFrameInspection.greenMask(0));
    }

    @Test
    void occupiedMasksAreStillChecked() {
        assertFalse(DryingFrameInspection.greenMask(1));
        assertFalse(DryingFrameInspection.greenMask(2));
        assertFalse(DryingFrameInspection.greenMask(-1));
    }
}
