package nurgling.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rules for syncing a container window to storageitems on close.
 * Live widgets win over the event-sourced cache so a loot by another
 * client is persisted without needing to sort the inventory.
 */
public final class ContainerInventorySync {
    private ContainerInventorySync() {}

    public static <T> T resolveParentGob(T bound, T lastActionGob) {
        return bound != null ? bound : lastActionGob;
    }

    /**
     * Prefer a gob whose resource matches the window title. Last-action is often
     * a previously clicked chest while Hidden Hollow is the window actually open.
     */
    public static <T> T pickGob(T bound, boolean boundMatches,
                               T lastAction, boolean lastMatches,
                               T nearestByCap) {
        if (bound != null && boundMatches) {
            return bound;
        }
        if (lastAction != null && lastMatches) {
            return lastAction;
        }
        if (nearestByCap != null) {
            return nearestByCap;
        }
        return bound != null ? bound : lastAction;
    }

    public static List<String> resourceNamesForCap(String cap, Map<String, String> contcaps) {
        if (cap == null || contcaps == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String> e : contcaps.entrySet()) {
            if (cap.equals(e.getValue())) {
                names.add(e.getKey());
            }
        }
        return names;
    }

    public static boolean gobMatchesWindow(String gobRes, String windowCap, Map<String, String> contcaps) {
        if (gobRes == null || windowCap == null || contcaps == null) {
            return false;
        }
        return windowCap.equals(contcaps.get(gobRes));
    }

    public static boolean shouldWrite(boolean gobBound) {
        return gobBound;
    }

    /** Empty snapshot may delete DB rows; only do that if this session actually saw items. */
    public static boolean shouldWrite(int snapshotSize, int peakSize) {
        return snapshotSize > 0 || peakSize > 0;
    }

    /**
     * On close, children are often already destroyed so {@code live} is empty.
     * Replacing the session cache with that empty list wipes storageitems.
     * Trust live widgets only while slots are still in the window.
     */
    public static <T> List<T> itemsToPersist(List<T> eventCache, List<T> live, int liveSlotCount) {
        if (liveSlotCount > 0 && live != null && !live.isEmpty()) {
            return live;
        }
        return eventCache;
    }

    public static boolean keepUnexpandedStackEntries(int cachedCountAtSlot, int liveAmount) {
        return liveAmount > 0 && cachedCountAtSlot == liveAmount;
    }
}
