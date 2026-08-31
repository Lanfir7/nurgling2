package nurgling.widgets;

import java.util.Collection;

public final class AreaListSelection {
    private AreaListSelection() {}

    /** Id to pass into {@code showPath(path, id)} when the caller omitted one. */
    public static int implicitRebuildId(Integer selectedAreaId) {
        return selectedAreaId == null ? -1 : selectedAreaId;
    }

    /**
     * After a list rebuild: keep {@code requestedId} if that area is still present.
     * {@code null} means fall back to the last list row (deleted area or folder).
     */
    public static Integer keptAreaId(int requestedId, Collection<Integer> presentAreaIds) {
        if (requestedId >= 0 && presentAreaIds != null && presentAreaIds.contains(requestedId))
            return requestedId;
        return null;
    }
}
