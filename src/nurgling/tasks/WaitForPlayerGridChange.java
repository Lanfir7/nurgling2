package nurgling.tasks;

import java.util.function.LongSupplier;

import static nurgling.navigation.ChunkNavConfig.PORTAL_LOAD_TIMEOUT_MS;

/**
 * Waits until a portal traversal places the player in a different, loaded grid.
 * The supplied grid lookup returns -1 while the player's current grid data is unavailable.
 */
public class WaitForPlayerGridChange extends NTask {
    private final long initialGridId;
    private final LongSupplier currentGridId;
    private final LongSupplier clock;
    private final long timeoutMs;
    private long startTime = -1;

    public WaitForPlayerGridChange(long initialGridId, LongSupplier currentGridId) {
        this(initialGridId, currentGridId, System::currentTimeMillis, PORTAL_LOAD_TIMEOUT_MS);
    }

    WaitForPlayerGridChange(long initialGridId, LongSupplier currentGridId,
                            LongSupplier clock, long timeoutMs) {
        this.initialGridId = initialGridId;
        this.currentGridId = currentGridId;
        this.clock = clock;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean check() {
        long now = clock.getAsLong();
        if (startTime == -1) {
            startTime = now;
        }

        long gridId = currentGridId.getAsLong();
        if (gridId != -1 && (initialGridId == -1 || gridId != initialGridId)) {
            return true;
        }

        return now - startTime >= timeoutMs;
    }
}
