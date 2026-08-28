package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenTargetContainerTest {
    @Test
    void stockpileWindowIsReusedOnlyForItsOwnGob() {
        assertTrue(OpenTargetContainer.sameGobId(42L, 42L));
        assertFalse(OpenTargetContainer.sameGobId(42L, 43L));
        assertFalse(OpenTargetContainer.sameGobId(-1L, 42L));
    }
}
