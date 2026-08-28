package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GobIconSavedIconLoadTest {
    @Test
    void loadingAlwaysRetriesSoCodeDepsCanFinish() {
        Loading wait = new Loading("Waiting for resource gfx/terobjs/mm/kritter...");
        assertTrue(GobIconSavedIconPolicy.rethrow(wait, true));
        assertTrue(GobIconSavedIconPolicy.rethrow(wait, false));
    }

    @Test
    void cachedIconsSkipPermanentFailures() {
        assertFalse(GobIconSavedIconPolicy.rethrow(new RuntimeException("broken factory"), true));
        assertFalse(GobIconSavedIconPolicy.rethrow(new LinkageError("missing class"), true));
    }

    @Test
    void uncachedBadVersionStillRethrows() {
        Resource.BadVersionException bad = new Resource.BadVersionException("gfx/kritter/lynx/icon", 4, 3, null);
        assertTrue(GobIconSavedIconPolicy.rethrow(bad, false));
        assertFalse(GobIconSavedIconPolicy.rethrow(bad, true));
    }
}
