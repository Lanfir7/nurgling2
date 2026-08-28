package nurgling.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class ForageMarkerLogic {
    public static final double DEFAULT_MIN_QUALITY = 40;
    public static final int MIN_QUALITY_SLIDER = 10;
    public static final int MAX_QUALITY_SLIDER = 100;
    public static final int DEDUP_RADIUS = 11;
    /* Item name and quality arrive asynchronously after the server creates the item.
     * Five seconds is not enough during map/resource loading, while 15 seconds still
     * bounds a missed pick before an unrelated incoming item can be associated. */
    public static final long QUALITY_WAIT_MS = 15000L;
    public static final String ID_PREFIX = "forage_";

    private ForageMarkerLogic() {}

    public static boolean isPickAction(String name) {
        return "Pick".equals(name);
    }

    public static boolean isGardenPot(String gobName) {
        return gobName != null && gobName.toLowerCase(Locale.ROOT).contains("gardenpot");
    }

    public static boolean shouldPlace(Float quality) {
        return shouldPlace(quality, DEFAULT_MIN_QUALITY);
    }

    public static boolean shouldPlace(Float quality, double minQuality) {
        return quality != null && quality >= minQuality;
    }

    public static int clampMinQuality(int value) {
        if (value < MIN_QUALITY_SLIDER) return MIN_QUALITY_SLIDER;
        if (value > MAX_QUALITY_SLIDER) return MAX_QUALITY_SLIDER;
        return value;
    }

    public static int minQualityFromConfig(Object confVal) {
        int v = (int) DEFAULT_MIN_QUALITY;
        if (confVal instanceof Number) {
            v = ((Number) confVal).intValue();
        }
        return clampMinQuality(v);
    }

    public static String resolveItemName(String displayName, String tooltip, String resName) {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        if (tooltip != null && !tooltip.isEmpty()) return tooltip;
        if (resName != null && !resName.isEmpty()) {
            int slash = resName.lastIndexOf('/');
            return slash >= 0 ? resName.substring(slash + 1) : resName;
        }
        return null;
    }

    public static String resourceKey(String resName) {
        if (resName == null || resName.isEmpty()) return null;
        int slash = resName.lastIndexOf('/');
        String base = slash >= 0 ? resName.substring(slash + 1) : resName;
        String key = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return key.isEmpty() ? null : key;
    }

    public static boolean acceptsPickedItemInventory(boolean isMainInv, boolean hasParentGob) {
        return true;
    }

    public static boolean isLikelyStackContainer(boolean parentIsItemStack, boolean contentsIsItemStack,
                                                 boolean hasAmount, Float quality) {
        if (parentIsItemStack) return false;
        return contentsIsItemStack;
    }

    public static boolean shouldWatchIncoming(boolean incomingIsStackContainer, boolean incomingIsStackMember,
                                             Float incomingQuality, boolean hasCurrent, Float currentQuality,
                                             double minQuality) {
        if (incomingIsStackContainer) return false;
        if (incomingQuality != null && !shouldPlace(incomingQuality, minQuality)) return false;
        if (incomingIsStackMember) return true;
        if (shouldPlace(currentQuality, minQuality)) return false;
        return true;
    }

    public static boolean isForageId(String locationId) {
        return locationId != null && locationId.startsWith(ID_PREFIX);
    }

    public static String formatLabel(double quality) {
        return String.format(Locale.US, "q%.0f", quality);
    }

    public static double parseQuality(String label) {
        if (label == null || !label.startsWith("q")) return 0;
        try {
            return Double.parseDouble(label.substring(1).trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String forageLocationId(long segmentId, int tileX, int tileY, String resourceType) {
        String type = resourceType != null ? resourceType.replaceAll("[^a-zA-Z0-9]", "_") : "item";
        return ID_PREFIX + segmentId + "_" + tileX + "_" + tileY + "_" + type + "_" + System.currentTimeMillis();
    }

    /** Background writer must stop once dispose owns the last save. */
    public static boolean allowQueuedMarkSave(boolean shutdown) {
        return !shutdown;
    }

    public static boolean commitMarkSnapshot(long snapVersion, long liveVersion, boolean shutdown) {
        return !shutdown && snapVersion == liveVersion;
    }

    public static boolean rescheduleMarkSave(long writtenVersion, long liveVersion, boolean shutdown) {
        return !shutdown && writtenVersion != liveVersion;
    }

    public static final class Place {
        public final long segmentId;
        public final int tileX;
        public final int tileY;
        public Place(long segmentId, int tileX, int tileY) {
            this.segmentId = segmentId;
            this.tileX = tileX;
            this.tileY = tileY;
        }
    }

    /** Correlates world picks with newly-created inventory items by resource type. */
    public static final class PickupSession {
        private final List<PendingPick> pending = new ArrayList<PendingPick>();
        private final List<Candidate> candidates = new ArrayList<Candidate>();
        private final Map<Object, Boolean> seen = new WeakHashMap<Object, Boolean>();

        public void notePick(long segmentId, int tileX, int tileY, long now) {
            notePick(segmentId, tileX, tileY, null, now);
        }

        public void notePick(long segmentId, int tileX, int tileY, String resourceName, long now) {
            prune(now);
            String key = resourceKey(resourceName);
            if (key == null) {
                for (int i = pending.size() - 1; i >= 0; i--) {
                    if (pending.get(i).resourceKey == null) pending.remove(i);
                }
            }
            pending.add(new PendingPick(segmentId, tileX, tileY, key, now));
        }

        public boolean offerItem(Object itemKey, boolean stackContainer, boolean stackMember,
                                 Float quality, double minQuality, long now) {
            return offerItem(itemKey, stackContainer, stackMember, quality, null, minQuality, now);
        }

        public boolean offerItem(Object itemKey, boolean stackContainer, boolean stackMember,
                                 Float quality, String resourceName, double minQuality, long now) {
            prune(now);
            if (itemKey == null || seen.containsKey(itemKey)) return false;
            seen.put(itemKey, Boolean.TRUE);
            if (pending.isEmpty()) return false;
            if (stackContainer) return false;
            String key = resourceKey(resourceName);
            PendingPick boundPick = findPending(key);
            if (key != null && boundPick == null) return false;
            if (boundPick != null) pending.remove(boundPick);
            candidates.add(new Candidate(itemKey, key, boundPick, now));
            return true;
        }

        public Place placeTick(Object itemKey, boolean stackContainer, Float quality, String name,
                               double minQuality, long now) {
            return placeTick(itemKey, stackContainer, quality, name, null, minQuality, now);
        }

        public Place placeTick(Object itemKey, boolean stackContainer, Float quality, String name,
                               String resourceName, double minQuality, long now) {
            prune(now);
            Candidate candidate = findCandidate(itemKey);
            if (candidate == null) return null;
            if (stackContainer) {
                candidates.remove(candidate);
                if (candidate.boundPick != null) {
                    returnPending(candidate.boundPick);
                    candidate.boundPick = null;
                }
                return null;
            }
            String itemResourceKey = resourceKey(resourceName);
            if (itemResourceKey != null) candidate.resourceKey = itemResourceKey;
            if (quality == null || name == null) return null;
            if (candidate.resourceKey == null && candidate.boundPick == null) return null;

            PendingPick pick = candidate.boundPick;
            if (pick == null) {
                pick = findPending(candidate.resourceKey);
                if (pick != null) pending.remove(pick);
                candidate.boundPick = pick;
            }
            if (pick == null) {
                candidates.remove(candidate);
                return null;
            }
            candidates.remove(candidate);
            if (!shouldPlace(quality, minQuality)) return null;
            return new Place(pick.segmentId, pick.tileX, pick.tileY);
        }

        public boolean isWatching(Object itemKey) {
            return findCandidate(itemKey) != null;
        }

        public void clear() {
            pending.clear();
            candidates.clear();
            seen.clear();
        }

        private Candidate findCandidate(Object itemKey) {
            if (itemKey == null) return null;
            for (Candidate candidate : candidates) {
                if (itemKey == candidate.itemKey) return candidate;
            }
            return null;
        }

        private PendingPick findPending(String itemResourceKey) {
            if (itemResourceKey != null) {
                for (PendingPick pick : pending) {
                    if (itemResourceKey.equals(pick.resourceKey)) return pick;
                }
            }
            /* Compatibility for callers without resource identity. The latest pick
             * is the only defensible association in that legacy mode. */
            for (int i = pending.size() - 1; i >= 0; i--) {
                if (pending.get(i).resourceKey == null) return pending.get(i);
            }
            return null;
        }

        private void returnPending(PendingPick pick) {
            int index = 0;
            while (index < pending.size() && pending.get(index).createdMs <= pick.createdMs) {
                index++;
            }
            pending.add(index, pick);
        }

        private void prune(long now) {
            for (int i = pending.size() - 1; i >= 0; i--) {
                if (now - pending.get(i).createdMs > QUALITY_WAIT_MS) pending.remove(i);
            }
            for (int i = candidates.size() - 1; i >= 0; i--) {
                if (now - candidates.get(i).watchStartMs > QUALITY_WAIT_MS) candidates.remove(i);
            }
        }

        private static final class PendingPick {
            final long segmentId;
            final int tileX;
            final int tileY;
            final String resourceKey;
            final long createdMs;
            PendingPick(long segmentId, int tileX, int tileY, String resourceKey, long createdMs) {
                this.segmentId = segmentId;
                this.tileX = tileX;
                this.tileY = tileY;
                this.resourceKey = resourceKey;
                this.createdMs = createdMs;
            }
        }

        private static final class Candidate {
            final Object itemKey;
            String resourceKey;
            PendingPick boundPick;
            final long watchStartMs;
            Candidate(Object itemKey, String resourceKey, PendingPick boundPick, long watchStartMs) {
                this.itemKey = itemKey;
                this.resourceKey = resourceKey;
                this.boundPick = boundPick;
                this.watchStartMs = watchStartMs;
            }
        }
    }

    public static String persistForageId(String locationId, boolean forageFlag) {
        if (locationId == null) return null;
        if (isForageId(locationId)) return locationId;
        if (forageFlag) return ID_PREFIX + locationId;
        return locationId;
    }

    public static String relocatedForageId(String locationId, long newSegmentId, int tileX, int tileY) {
        if (!isForageId(locationId)) return locationId;
        String rest = locationId.substring(ID_PREFIX.length());
        String[] parts = rest.split("_", 4);
        String suffix = parts.length >= 4 ? parts[3] : "item";
        return ID_PREFIX + newSegmentId + "_" + tileX + "_" + tileY + "_" + suffix;
    }

    public static boolean matchesMapSearch(String resourceType, String label, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return true;
        String p = pattern.toLowerCase(Locale.ROOT);
        String type = resourceType != null ? resourceType.toLowerCase(Locale.ROOT) : "";
        String lab = label != null ? label.toLowerCase(Locale.ROOT) : "";
        return type.contains(p) || lab.contains(p);
    }

    public static boolean matchesWindowSearch(String resourceType, String label, String selectedType, Double minQuality) {
        if (resourceType == null) return false;
        if (selectedType != null && !"Any".equals(selectedType) && !selectedType.equals(resourceType)) {
            return false;
        }
        if (minQuality != null) {
            return parseQuality(label) >= minQuality;
        }
        return true;
    }

    public static final class Neighbor {
        public final String locationId;
        public final double quality;
        public Neighbor(String locationId, double quality) {
            this.locationId = locationId;
            this.quality = quality;
        }
    }

    public static final class Dedup {
        public final boolean skip;
        public final List<String> removeIds;
        public Dedup(boolean skip, List<String> removeIds) {
            this.skip = skip;
            this.removeIds = removeIds;
        }
    }

    public static Dedup decideDedup(double newQuality, List<Neighbor> nearbySameType) {
        if (nearbySameType == null || nearbySameType.isEmpty()) {
            return new Dedup(false, Collections.emptyList());
        }
        List<String> remove = new ArrayList<String>();
        for (Neighbor n : nearbySameType) {
            if (n.quality >= newQuality) {
                return new Dedup(true, Collections.emptyList());
            }
            remove.add(n.locationId);
        }
        return new Dedup(false, remove);
    }
}
