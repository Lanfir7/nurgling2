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

    public static Result dragFrame(Edge edge, int frameLeft, int contentOffset,
                                   int contentWidth, int cursorX, int minContentWidth,
                                   int maxContentWidth, int frameExtraWidth) {
        int contentLeft = frameLeft + contentOffset;
        int contentRight = contentLeft + contentWidth;
        Result content = drag(edge, contentLeft, contentRight, cursorX,
                minContentWidth, maxContentWidth);
        return new Result(content.left - contentOffset, content.width + frameExtraWidth);
    }
}
