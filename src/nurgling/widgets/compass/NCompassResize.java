package nurgling.widgets.compass;

public final class NCompassResize {
    private NCompassResize() {
    }

    public enum Edge {
        LEFT,
        RIGHT
    }

    public static final class Result {
        public final int left;
        public final int width;

        private Result(int left, int width) {
            this.left = left;
            this.width = width;
        }
    }

    public static Result drag(Edge edge, int startLeft, int startRight, int cursorX,
                              int minWidth, int maxWidth) {
        int requested = edge == Edge.LEFT ? startRight - cursorX : cursorX - startLeft;
        int width = Math.max(minWidth, Math.min(maxWidth, requested));
        int left = edge == Edge.LEFT ? startRight - width : startLeft;
        return new Result(left, width);
    }
}
