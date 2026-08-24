package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestRewardFilterTest {
    @Test
    void turnInWhenOnlyTellLeft() {
        assertTrue(QuestRewardFilter.isTurnIn(true, false));
    }

    @Test
    void skipWhenOtherStepsOpen() {
        assertFalse(QuestRewardFilter.isTurnIn(true, true));
    }

    @Test
    void skipWhenTellAlreadyDone() {
        assertFalse(QuestRewardFilter.isTurnIn(false, false));
        assertFalse(QuestRewardFilter.isTurnIn(false, true));
    }
}
