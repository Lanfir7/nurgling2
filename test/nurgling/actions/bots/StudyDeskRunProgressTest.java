package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyDeskRunProgressTest {
    @Test
    void foundDeskThatFailsToOpenDoesNotCountAsCompleted() {
        StudyDeskFiller.RunProgress progress = new StudyDeskFiller.RunProgress();

        progress.recordFound();
        progress.recordFailure();

        assertTrue(progress.hasFoundDesk());
        assertFalse(progress.hasCompletedDesk());
        assertEquals(1, progress.issueCount);
    }

    @Test
    void openedDeskCountsAsCompletedAndPerfectOnlyWithoutIssues() {
        StudyDeskFiller.RunProgress progress = new StudyDeskFiller.RunProgress();

        progress.recordFound();
        progress.recordCompleted(true);

        assertTrue(progress.hasCompletedDesk());
        assertEquals(1, progress.perfectCount);
        assertEquals(0, progress.issueCount);
    }
}
