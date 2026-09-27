package nurgling.widgets;

import java.util.concurrent.TimeUnit;

/** Tracks one server-confirmed swimming session; the caller sends the toggle command. */
final class SwimmingAutoOffTimer {
    static final long TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(5);

    private long enabledAt;
    private boolean active;
    private boolean requestSent;

    void onState(boolean swimming, long now) {
        if (!swimming) {
            active = false;
            requestSent = false;
        } else if (!active) {
            active = true;
            enabledAt = now;
            requestSent = false;
        }
    }

    boolean isDue(boolean settingEnabled, long now) {
        return settingEnabled && active && !requestSent
                && now - enabledAt >= TIMEOUT_NANOS;
    }

    void requested() {
        requestSent = true;
    }
}
