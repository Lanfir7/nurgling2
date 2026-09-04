package nurgling.areas;

import java.util.concurrent.atomic.AtomicBoolean;

/** Coalesces worker refresh requests for consumption by the UI thread. */
public final class AreaLabelRefreshSignal {
    private final AtomicBoolean requested = new AtomicBoolean();

    public void request() {
        requested.set(true);
    }

    public boolean consume() {
        return requested.getAndSet(false);
    }
}
