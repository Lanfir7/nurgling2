package nurgling.db;

import nurgling.NGameUI;
import nurgling.tools.CurrentHomeTerritories;
import nurgling.tools.HomeLocationResolver;

/** Decides whether storage snapshots may be persisted at the player's location. */
public final class StorageTrackingPolicy {
    private StorageTrackingPolicy() {
    }

    public static boolean shouldTrack(NGameUI gui) {
        return shouldTrack(CurrentHomeTerritories.status(gui));
    }

    static boolean shouldTrack(HomeLocationResolver.Status status) {
        return status != null && status.home;
    }

    /** A delayed storage update is valid only if the whole tracked session stayed home-scoped. */
    public static boolean canPersistSession(boolean startedAtHome, boolean homeAtCommit) {
        return startedAtHome && homeAtCommit;
    }
}
