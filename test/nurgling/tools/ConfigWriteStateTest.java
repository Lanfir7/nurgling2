package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWriteStateTest {
    @Test
    void changeDuringSaveKeepsConfigDirtyForTheNextSave() {
        ConfigWriteState state = new ConfigWriteState();
        state.initialize("{\"value\":1}");
        state.markDirty();
        ConfigWriteState.Save firstSave = state.begin();

        state.markDirty();
        state.complete(firstSave, "{\"value\":2}");

        assertTrue(state.isDirty());
        assertEquals("{\"value\":2}", state.baseline());
    }

    @Test
    void completedCurrentSaveClearsDirtyState() {
        ConfigWriteState state = new ConfigWriteState();
        state.initialize("{\"value\":1}");
        state.markDirty();
        ConfigWriteState.Save save = state.begin();

        state.complete(save, "{\"value\":2}");

        assertFalse(state.isDirty());
        assertEquals("{\"value\":2}", state.baseline());
    }
}
