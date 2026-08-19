package nurgling.map;

/**
 * Permanent SMarker icons such as vanilla Cave Passage use mm/up and mm/down.
 * Probing missing gfx/terobjs/mm/cave-passage paths with loadwait freezes the UI.
 */
public final class PermanentMarkerPath {
    private PermanentMarkerPath() {}

    public static boolean isCavePassage(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("cave") && lower.contains("passage");
    }

    public static boolean isReadyMinimapResource(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return path.equals("mm/up")
                || path.equals("mm/down")
                || path.startsWith("mm/")
                || path.startsWith("gfx/terobjs/mm/");
    }

    public static boolean shouldProbeRemotePaths(String tooltipName, String currentPath) {
        if (isReadyMinimapResource(currentPath) || isCavePassage(tooltipName)) {
            return false;
        }
        return true;
    }
}
