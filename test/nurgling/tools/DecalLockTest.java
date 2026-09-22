package nurgling.tools;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecalLockTest {
    private final NConfig previous = NConfig.current;

    @AfterEach
    void restoreCurrent() {
        NConfig.current = previous;
    }

    @Test
    void lockDecalsIsDisabledByDefaultAndCanBeEnabled() {
        NConfig.current = new NConfig();

        assertFalse(DecalLock.enabled());
        NConfig.set(NConfig.Key.lockDecals, true);
        assertTrue(DecalLock.enabled());
    }
}
