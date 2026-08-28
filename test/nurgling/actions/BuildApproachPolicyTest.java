package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildApproachPolicyTest {
    @Test
    void failedApproachSkipsAnExplicitGhostPosition() {
        assertEquals(
                Build.ApproachAction.SKIP_GHOST,
                Build.decideApproachAction(false, true)
        );
    }

    @Test
    void failedApproachAbortsAutomaticPlacement() {
        assertEquals(
                Build.ApproachAction.ABORT,
                Build.decideApproachAction(false, false)
        );
    }

    @Test
    void successfulApproachContinuesPlacement() {
        assertEquals(
                Build.ApproachAction.PROCEED,
                Build.decideApproachAction(true, true)
        );
    }
}
