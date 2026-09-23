package nurgling.widgets;

/** Pure geometry shared by the barter window layout and its unit tests. */
public final class BarterWindowLayout {
    private BarterWindowLayout() {
    }

    public static int viewportHeight(int contentHeight, int availableHeight) {
        return Math.max(1, Math.min(contentHeight, availableHeight));
    }

    public static int scrollOffset(int requested, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(requested, Math.max(0, contentHeight - viewportHeight)));
    }

    public static int rowY(int viewportTop, int contentY, int scrollOffset) {
        return viewportTop + contentY - scrollOffset;
    }

    public static int compareOrigins(int ay, int ax, int by, int bx) {
        int byRow = Integer.compare(ay, by);
        return byRow != 0 ? byRow : Integer.compare(ax, bx);
    }
}
