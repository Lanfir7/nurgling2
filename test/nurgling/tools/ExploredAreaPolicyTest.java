package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredAreaPolicyTest {
    @Test
    void displayOffDoesNotDisableRecording() {
        assertTrue(ExploredAreaPolicy.shouldRecord(true));
        assertFalse(ExploredAreaPolicy.shouldDraw(false));
    }

    @Test
    void recordingOffDoesNotForceDrawing() {
        assertFalse(ExploredAreaPolicy.shouldRecord(false));
        assertTrue(ExploredAreaPolicy.shouldDraw(true));
    }

    @Test
    void nullAndNonBooleanFlagsAreOff() {
        assertFalse(ExploredAreaPolicy.shouldRecord(null));
        assertFalse(ExploredAreaPolicy.shouldDraw("yes"));
    }

    @Test
    void missingRecordKeyInheritsDisplayForExistingUsers() {
        assertTrue(ExploredAreaPolicy.migrateRecord(false, null, true));
        assertFalse(ExploredAreaPolicy.migrateRecord(false, null, false));
        assertFalse(ExploredAreaPolicy.migrateRecord(false, null, null));
    }

    @Test
    void presentRecordKeyWinsEvenIfDisplayDiffers() {
        assertFalse(ExploredAreaPolicy.migrateRecord(true, false, true));
        assertTrue(ExploredAreaPolicy.migrateRecord(true, true, false));
    }
}
