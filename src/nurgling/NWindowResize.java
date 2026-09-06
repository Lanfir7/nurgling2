package nurgling;

import haven.Coord;

/** Pure geometry for resizing a window from any edge or corner. */
public final class NWindowResize {
    public enum Edge {
        NONE, LEFT, RIGHT, TOP, BOTTOM,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public static final class Result {
        public final Coord position;
        public final Coord size;

        private Result(Coord position, Coord size) {
            this.position = position;
            this.size = size;
        }
    }

    private NWindowResize() { }

    public static Edge hit(Coord point, Coord size, int border) {
        if(point == null || size == null || point.x < 0 || point.y < 0 ||
                point.x >= size.x || point.y >= size.y)
            return Edge.NONE;
        int margin = Math.max(1, border);
        boolean left = point.x < margin;
        boolean right = point.x >= size.x - margin;
        boolean top = point.y < margin;
        boolean bottom = point.y >= size.y - margin;
        if(top && left) return Edge.TOP_LEFT;
        if(top && right) return Edge.TOP_RIGHT;
        if(bottom && left) return Edge.BOTTOM_LEFT;
        if(bottom && right) return Edge.BOTTOM_RIGHT;
        if(left) return Edge.LEFT;
        if(right) return Edge.RIGHT;
        if(top) return Edge.TOP;
        if(bottom) return Edge.BOTTOM;
        return Edge.NONE;
    }

    public static Result drag(Edge edge, Coord startPosition, Coord startSize,
                              Coord delta, Coord minimumSize) {
        if(edge == null || edge == Edge.NONE)
            return new Result(startPosition, startSize);
        int minWidth = Math.max(1, minimumSize.x);
        int minHeight = Math.max(1, minimumSize.y);
        boolean left = edge == Edge.LEFT || edge == Edge.TOP_LEFT || edge == Edge.BOTTOM_LEFT;
        boolean right = edge == Edge.RIGHT || edge == Edge.TOP_RIGHT || edge == Edge.BOTTOM_RIGHT;
        boolean top = edge == Edge.TOP || edge == Edge.TOP_LEFT || edge == Edge.TOP_RIGHT;
        boolean bottom = edge == Edge.BOTTOM || edge == Edge.BOTTOM_LEFT || edge == Edge.BOTTOM_RIGHT;

        int width = Math.max(minWidth, startSize.x + (right ? delta.x : left ? -delta.x : 0));
        int height = Math.max(minHeight, startSize.y + (bottom ? delta.y : top ? -delta.y : 0));
        int x = left ? startPosition.x + startSize.x - width : startPosition.x;
        int y = top ? startPosition.y + startSize.y - height : startPosition.y;
        return new Result(Coord.of(x, y), Coord.of(width, height));
    }
}
