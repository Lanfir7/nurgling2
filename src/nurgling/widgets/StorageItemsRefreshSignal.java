package nurgling.widgets;

import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps database-change notifications until the storage window can reload them. */
public final class StorageItemsRefreshSignal {
    private final AtomicBoolean requested = new AtomicBoolean();

    public void request() {
        requested.set(true);
    }

    public boolean take(boolean visible, boolean loading) {
        return visible && !loading && requested.compareAndSet(true, false);
    }
}
