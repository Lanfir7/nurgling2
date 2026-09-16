package nurgling.widgets.login;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NLoginPanelPersistenceTest {
    @Test
    void rememberChoiceControlsWhetherANewCredentialIsStored() {
        assertTrue(NLoginPanel.shouldPersistNewCredential(true));
        assertFalse(NLoginPanel.shouldPersistNewCredential(false));
    }

    @Test
    void rejectedDuplicateSubmitCannotReplacePendingCredential() {
        assertFalse(NLoginPanel.shouldStageNewCredential(false, true));
        assertTrue(NLoginPanel.shouldStageNewCredential(true, true));
        assertFalse(NLoginPanel.shouldStageNewCredential(true, false));
    }
}
