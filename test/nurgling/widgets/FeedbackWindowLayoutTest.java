package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackWindowLayoutTest {
    @Test
    void threeThumbnailsRemainInsideTheAttachmentRow() {
        FeedbackWindowLayout layout = FeedbackWindowLayout.calculate(560, 3);

        assertEquals(3, layout.thumbnails().size());
        assertTrue(layout.thumbnails().get(2).x + layout.thumbnailSize() <= 560);
        assertTrue(layout.sendY() > layout.attachmentsY());
    }

    @Test
    void sendRowDoesNotMoveWhenThereAreNoAttachments() {
        assertEquals(FeedbackWindowLayout.calculate(560, 0).sendY(),
                FeedbackWindowLayout.calculate(560, 3).sendY());
    }

    @Test
    void discordFieldSitsBetweenSubjectAndDescription() {
        FeedbackWindowLayout layout = FeedbackWindowLayout.calculate(560, 0);

        assertTrue(layout.discordY() > layout.subjectY());
        assertTrue(layout.descriptionY() > layout.discordY());
    }
}
