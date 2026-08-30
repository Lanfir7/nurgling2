package nurgling.overlays.map;

import haven.Coord;

import java.util.Objects;

final class NOverlayRefreshPolicy {
    private final long intervalNanos;
    private boolean initialized;
    private long lastRefreshNanos;
    private Coord lastCenter;
    private long lastRevision;

    NOverlayRefreshPolicy(long intervalNanos) {
        this.intervalNanos = intervalNanos;
    }

    boolean shouldRefresh(long nowNanos, Coord center, long revision) {
        boolean changed = !initialized ||
                !Objects.equals(lastCenter, center) ||
                lastRevision != revision;
        boolean intervalElapsed = initialized &&
                nowNanos - lastRefreshNanos >= intervalNanos;
        if (!changed && !intervalElapsed) {
            return false;
        }
        initialized = true;
        lastRefreshNanos = nowNanos;
        lastCenter = center;
        lastRevision = revision;
        return true;
    }
}
