package nurgling.widgets.craftatlas;

/** Tracks the last catalog input seen while the Atlas was visible. */
final class CraftAtlasRefreshGate {
    private static final long RETRY_INTERVAL_NS = 1_000_000_000L;

    private boolean initialized;
    private int menuRevision;
    private long storeRevision;
    private boolean incomplete;
    private long lastAttempt;

    boolean shouldRefresh(boolean visible, int menuRevision, long storeRevision, long now) {
        return visible && (!initialized || this.menuRevision != menuRevision || this.storeRevision != storeRevision ||
                (incomplete && now - lastAttempt >= RETRY_INTERVAL_NS));
    }

    void completed(int menuRevision, long storeRevision, boolean incomplete, long now) {
        initialized = true;
        this.menuRevision = menuRevision;
        this.storeRevision = storeRevision;
        this.incomplete = incomplete;
        lastAttempt = now;
    }

    void invalidate() {
        initialized = false;
    }
}
