package nurgling.tasks;

import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetNotStackExactNameTest {
    @Test
    void frogDoesNotMatchFrogsCrownAsStackTarget() {
        assertFalse(GetNotStack.matchesRequestedName(new NAlias("Frog"), "Frog's Crown"));
    }

    @Test
    void frogStillMatchesAnotherFrogAsStackTarget() {
        assertTrue(GetNotStack.matchesRequestedName(new NAlias("Frog"), "Frog"));
    }
}
