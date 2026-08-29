package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CraftMakeTest {

    @Test
    void missingWindowIsClosed() {
        assertFalse(CraftMake.windowOpen(null));
    }
}
