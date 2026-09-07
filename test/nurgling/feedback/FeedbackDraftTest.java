package nurgling.feedback;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackDraftTest {
    @Test
    void requiresTrimmedSubjectAndDescription() {
        FeedbackDraft draft = new FeedbackDraft();

        assertEquals(FeedbackDraft.Validation.SUBJECT_REQUIRED, draft.validate());
        draft.setSubject("  crash  ");
        assertEquals(FeedbackDraft.Validation.DESCRIPTION_REQUIRED, draft.validate());
        draft.setDescription("  opening inventory crashes  ");
        assertEquals(FeedbackDraft.Validation.OK, draft.validate());

        FeedbackSubmission submission = draft.submission("R-1234");
        assertEquals("crash", submission.subject());
        assertEquals("opening inventory crashes", submission.description());
    }

    @Test
    void enforcesTextAndAttachmentLimits() throws Exception {
        FeedbackDraft draft = new FeedbackDraft();
        draft.setSubject(repeat('s', 121));
        draft.setDescription("body");
        assertEquals(FeedbackDraft.Validation.SUBJECT_TOO_LONG, draft.validate());

        draft.setSubject("subject");
        draft.setDescription(repeat('d', 4001));
        assertEquals(FeedbackDraft.Validation.DESCRIPTION_TOO_LONG, draft.validate());

        FeedbackAttachment image = FeedbackAttachment.from(
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB));
        assertTrue(draft.addAttachment(image));
        assertTrue(draft.addAttachment(image));
        assertTrue(draft.addAttachment(image));
        assertFalse(draft.addAttachment(image));
        assertEquals(3, draft.attachments().size());
    }

    @Test
    void clearingRestoresPristineBugDraft() {
        FeedbackDraft draft = new FeedbackDraft();
        draft.setType(FeedbackType.SUGGESTION);
        draft.setSubject("idea");
        assertTrue(draft.dirty());

        draft.clear();

        assertEquals(FeedbackType.BUG, draft.type());
        assertFalse(draft.dirty());
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
