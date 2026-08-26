package nurgling.areas;

public final class AreaLabelSync {
    private AreaLabelSync() {}

    public enum Action { CREATE, SKIP, REMOVE }

    public static boolean labelsShouldBeLive(boolean toggleOn, boolean editorOpen) {
        return toggleOn || editorOpen;
    }

    public static boolean toggleOn(Object cfg) {
        return Boolean.TRUE.equals(cfg);
    }

    public static boolean labelsClickable(boolean editorOpen) {
        return editorOpen;
    }

    public static Action decide(boolean areaExists, boolean locatable, boolean dummyAlive) {
        if (!areaExists) {
            return dummyAlive ? Action.REMOVE : Action.SKIP;
        }
        if (dummyAlive) {
            return locatable ? Action.SKIP : Action.REMOVE;
        }
        return locatable ? Action.CREATE : Action.SKIP;
    }
}
