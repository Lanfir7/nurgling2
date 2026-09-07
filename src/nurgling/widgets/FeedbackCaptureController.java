package nurgling.widgets;

import haven.Coord;
import haven.GameUI;
import haven.Widget;
import nurgling.feedback.FeedbackAttachment;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FeedbackCaptureController {
    private FeedbackCaptureController() {
    }

    public static void capture(GameUI game, Widget form, Widget options,
                               Consumer<FeedbackAttachment> selected, Runnable cancelled,
                               Consumer<Throwable> failed) {
        boolean formWasVisible = form.visible();
        boolean optionsWasVisible = options != null && options.visible();
        form.hide();
        if(options != null)
            options.hide();
        VisibilityState visibility = new VisibilityState(() -> {
            if(optionsWasVisible)
                options.show();
            if(formWasVisible) {
                form.show();
                form.raise();
            }
        });
        try {
            game.ui.drawafter(output -> output.getimage(Coord.z, output.sz(), frame -> {
                synchronized(game.ui) {
                    try {
                        FeedbackSnipOverlay overlay = new FeedbackSnipOverlay(frame, crop -> {
                            try {
                                selected.accept(FeedbackAttachment.from(crop));
                            } catch(IOException | RuntimeException error) {
                                failed.accept(error);
                            } finally {
                                visibility.restore();
                            }
                        }, () -> {
                            visibility.restore();
                            cancelled.run();
                        });
                        game.ui.root.add(overlay, Coord.z);
                        game.ui.root.setfocus(overlay);
                    } catch(RuntimeException error) {
                        visibility.restore();
                        failed.accept(error);
                    }
                }
            }));
        } catch(RuntimeException error) {
            visibility.restore();
            failed.accept(error);
        }
    }

    static final class VisibilityState {
        private final Runnable restoration;
        private final AtomicBoolean restored = new AtomicBoolean();

        VisibilityState(Runnable restoration) {
            this.restoration = restoration;
        }

        void restore() {
            if(restored.compareAndSet(false, true))
                restoration.run();
        }
    }
}
