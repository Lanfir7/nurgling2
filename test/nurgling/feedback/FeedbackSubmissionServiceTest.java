package nurgling.feedback;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackSubmissionServiceTest {
    @Test
    void retryContinuesAfterTheLastSuccessfulOperation() throws Exception {
        List<String> calls = new ArrayList<>();
        AtomicBoolean failSecondPhotoOnce = new AtomicBoolean(true);
        FeedbackSender sender = new FeedbackSender() {
            public void sendMessage(String text) {
                calls.add("message");
            }

            public void sendPhoto(byte[] png, String name, String caption) throws IOException {
                calls.add(name);
                if(name.endsWith("-2.png") && failSecondPhotoOnce.getAndSet(false))
                    throw new IOException("offline");
            }
        };
        FeedbackSubmissionService service = new FeedbackSubmissionService(sender, Runnable::run);
        FeedbackSubmissionService.Attempt attempt = service.begin(submissionWithThreeImages("R-7"));
        RecordingListener listener = new RecordingListener();

        service.submit(attempt, listener);
        assertEquals(Arrays.asList("message", "R-7-1.png", "R-7-2.png"), calls);
        assertTrue(attempt.messageSent());
        assertEquals(1, attempt.photosSent());
        assertEquals(1, listener.failures);

        service.submit(attempt, listener);
        assertEquals(Arrays.asList("message", "R-7-1.png", "R-7-2.png", "R-7-2.png", "R-7-3.png"), calls);
        assertTrue(listener.succeeded);
        assertEquals(3, attempt.photosSent());
        assertFalse(attempt.running());
    }

    @Test
    void formatterContainsOnlyExplicitReportData() {
        FeedbackSubmission report = new FeedbackSubmission("R-8", FeedbackType.SUGGESTION,
                "Craft window", "Please add favorites", Collections.emptyList());

        String text = FeedbackFormatter.message(report);

        assertTrue(text.contains("R-8"));
        assertTrue(text.contains("SUGGESTION"));
        assertTrue(text.contains("Craft window"));
        assertTrue(text.contains("Please add favorites"));
        assertFalse(text.toLowerCase().contains("character"));
        assertFalse(text.toLowerCase().contains("machine"));
    }

    @Test
    void maximumValidReportFitsTelegramMessageLimit() {
        String subject = repeat('s', FeedbackDraft.MAX_SUBJECT);
        String description = repeat('d', FeedbackDraft.MAX_DESCRIPTION);
        FeedbackSubmission report = new FeedbackSubmission("R-12345678", FeedbackType.SUGGESTION,
                subject, description, Collections.emptyList());

        assertTrue(FeedbackFormatter.message(report).length() <= 4096);
    }

    @Test
    void duplicateSubmitIsIgnoredWhileAttemptIsRunning() throws Exception {
        QueuedExecutor executor = new QueuedExecutor();
        List<String> calls = new ArrayList<>();
        FeedbackSender sender = new FeedbackSender() {
            public void sendMessage(String text) { calls.add("message"); }
            public void sendPhoto(byte[] png, String filename, String caption) { calls.add(filename); }
        };
        FeedbackSubmissionService service = new FeedbackSubmissionService(sender, executor);
        FeedbackSubmissionService.Attempt attempt = service.begin(submissionWithThreeImages("R-9"));
        RecordingListener listener = new RecordingListener();

        service.submit(attempt, listener);
        service.submit(attempt, listener);

        assertEquals(1, executor.tasks.size());
        executor.tasks.remove(0).run();
        assertEquals(4, calls.size());
        assertTrue(listener.succeeded);
    }

    @Test
    void failureListenerCanImmediatelyRetryTheSameAttempt() throws Exception {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        FeedbackSender sender = new FeedbackSender() {
            public void sendMessage(String text) throws IOException {
                if(failOnce.getAndSet(false))
                    throw new IOException("offline");
            }
            public void sendPhoto(byte[] png, String filename, String caption) { }
        };
        FeedbackSubmissionService service = new FeedbackSubmissionService(sender, Runnable::run);
        FeedbackSubmissionService.Attempt attempt = service.begin(new FeedbackSubmission(
                "R-10", FeedbackType.BUG, "Broken", "Steps", Collections.emptyList()));
        AtomicBoolean succeeded = new AtomicBoolean();
        FeedbackSubmissionService.Listener listener = new FeedbackSubmissionService.Listener() {
            public void succeeded() { succeeded.set(true); }
            public void failed(IOException failure) { service.submit(attempt, this); }
        };

        service.submit(attempt, listener);

        assertTrue(succeeded.get());
        assertFalse(attempt.running());
    }

    private static FeedbackSubmission submissionWithThreeImages(String id) throws IOException {
        List<FeedbackAttachment> images = new ArrayList<>();
        for(int i = 0; i < 3; i++)
            images.add(FeedbackAttachment.from(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)));
        return new FeedbackSubmission(id, FeedbackType.BUG, "Broken", "Steps", images);
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static final class RecordingListener implements FeedbackSubmissionService.Listener {
        boolean succeeded;
        int failures;

        public void succeeded() { succeeded = true; }
        public void failed(IOException failure) { failures++; }
    }

    private static final class QueuedExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();
        public void execute(Runnable command) { tasks.add(command); }
    }
}
