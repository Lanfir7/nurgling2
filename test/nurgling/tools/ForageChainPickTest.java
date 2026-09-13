package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ForageChainPickTest {
    private static final String CATTAIL = "gfx/terobjs/herbs/cattail";
    private static final String MUSSELS = "gfx/terobjs/herbs/mussels";
    private static final String TREE = "gfx/terobjs/trees/oak";

    @Test
    void onlyHerbGobsAreChainable() {
        assertTrue(ForageChainPick.isHerbGob(CATTAIL));
        assertTrue(ForageChainPick.isHerbGob(MUSSELS));
        assertFalse(ForageChainPick.isHerbGob(TREE));
        assertFalse(ForageChainPick.isHerbGob("gfx/terobjs/bushes/arrowwood"));
        assertFalse(ForageChainPick.isHerbGob(null));
    }

    @Test
    void chainableActionsArePickNotPickup() {
        assertTrue(ForageChainPick.isChainableAction("Pick"));
        assertTrue(ForageChainPick.isChainableAction("Pick cattail head"));
        assertFalse(ForageChainPick.isChainableAction("Pick up"));
        assertFalse(ForageChainPick.isChainableAction("Harvest"));
        assertFalse(ForageChainPick.isChainableAction("Chop"));
        assertFalse(ForageChainPick.isChainableAction(null));
    }

    @Test
    void startsOnlyOnShiftPickOfHerbWhenIdle() {
        assertTrue(ForageChainPick.shouldStart(true, false, "Pick", CATTAIL));
        assertFalse(ForageChainPick.shouldStart(false, false, "Pick", CATTAIL));
        assertFalse(ForageChainPick.shouldStart(true, true, "Pick", CATTAIL));
        assertFalse(ForageChainPick.shouldStart(true, false, "Pick", TREE));
        assertFalse(ForageChainPick.shouldStart(true, false, "Chop", CATTAIL));
    }

    @Test
    void selectNextTakesNearestSameNameInsideRadius() {
        ForageChainPick.Candidate origin = new ForageChainPick.Candidate(1, CATTAIL, 0, 0);
        ForageChainPick.Candidate near = new ForageChainPick.Candidate(2, CATTAIL, 20, 0);
        ForageChainPick.Candidate far = new ForageChainPick.Candidate(3, CATTAIL, 40, 0);
        ForageChainPick.Candidate other = new ForageChainPick.Candidate(4, MUSSELS, 10, 0);

        ForageChainPick.Candidate next = ForageChainPick.selectNext(
                Arrays.asList(origin, near, far, other), CATTAIL, 1, 0, 0, 33);

        assertNotNull(next);
        assertEquals(2, next.id);
    }

    @Test
    void selectNextSkipsOutsideRadiusAndExcludedId() {
        ForageChainPick.Candidate far = new ForageChainPick.Candidate(3, CATTAIL, 40, 0);

        assertNull(ForageChainPick.selectNext(
                Collections.singletonList(far), CATTAIL, 1, 0, 0, 33));
        assertNull(ForageChainPick.selectNext(
                Collections.singletonList(new ForageChainPick.Candidate(1, CATTAIL, 5, 0)),
                CATTAIL, 1, 0, 0, 33));
    }

    @Test
    void stopWhenFullOrNothingLeft() {
        ForageChainPick.Candidate next = new ForageChainPick.Candidate(2, CATTAIL, 10, 0);
        assertTrue(ForageChainPick.shouldStop(true, next));
        assertTrue(ForageChainPick.shouldStop(false, null));
        assertFalse(ForageChainPick.shouldStop(false, next));
    }

    @Test
    void startsOnQualifiedPickActionNames() {
        assertTrue(ForageChainPick.shouldStart(true, false, "Pick cattail head", CATTAIL));
        assertFalse(ForageChainPick.shouldStart(true, false, "Pick up", CATTAIL));
    }
}
