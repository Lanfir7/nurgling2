package nurgling.feedback;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FeedbackSubmissionService {
    private static final ExecutorService DELIVERY_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "feedback-delivery");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final FeedbackSender sender;
    private final Executor executor;

    public FeedbackSubmissionService(FeedbackSender sender) {
        this(sender, DELIVERY_EXECUTOR);
    }

    FeedbackSubmissionService(FeedbackSender sender, Executor executor) {
        this.sender = sender;
        this.executor = executor;
    }

    public Attempt begin(FeedbackSubmission submission) {
        return new Attempt(submission);
    }

    public void submit(final Attempt attempt, final Listener listener) {
        if(!attempt.start())
            return;
        executor.execute(() -> {
            try {
                if(!attempt.messageSent()) {
                    sender.sendMessage(FeedbackFormatter.message(attempt.submission()));
                    attempt.markMessageSent();
                }
                while(attempt.photosSent() < attempt.submission().attachments().size()) {
                    int index = attempt.photosSent();
                    sender.sendPhoto(attempt.submission().attachments().get(index).png(),
                            attempt.submission().reportId() + "-" + (index + 1) + ".png",
                            FeedbackFormatter.photoCaption(attempt.submission(), index));
                    attempt.markPhotoSent();
                }
                listener.succeeded();
            } catch(IOException failure) {
                listener.failed(failure);
            } finally {
                attempt.finish();
            }
        });
    }

    public interface Listener {
        void succeeded();
        void failed(IOException failure);
    }

    public static final class Attempt {
        private final FeedbackSubmission submission;
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile boolean messageSent;
        private volatile int photosSent;

        private Attempt(FeedbackSubmission submission) {
            if(submission == null)
                throw new IllegalArgumentException("submission");
            this.submission = submission;
        }

        public FeedbackSubmission submission() { return submission; }
        public boolean messageSent() { return messageSent; }
        public int photosSent() { return photosSent; }
        public boolean running() { return running.get(); }

        private boolean start() { return running.compareAndSet(false, true); }
        private void finish() { running.set(false); }
        private void markMessageSent() { messageSent = true; }
        private void markPhotoSent() { photosSent++; }
    }
}
