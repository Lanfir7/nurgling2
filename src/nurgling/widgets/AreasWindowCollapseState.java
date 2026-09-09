package nurgling.widgets;

import haven.Coord;

final class AreasWindowCollapseState {
    enum Direction { LEFT, RIGHT }

    private final Coord expandedSize;
    private final Coord compactSize;
    private boolean expanded;

    AreasWindowCollapseState(Coord expandedSize, int compactWidth) {
        this.expandedSize = new Coord(expandedSize);
        this.compactSize = Coord.of(compactWidth, expandedSize.y);
    }

    boolean expanded() {
        return expanded;
    }

    boolean detailsVisible() {
        return expanded;
    }

    int searchWidth() {
        return compactSize.x;
    }

    Coord size() {
        return new Coord(expanded ? expandedSize : compactSize);
    }

    Direction direction() {
        return expanded ? Direction.LEFT : Direction.RIGHT;
    }

    void toggle() {
        expanded = !expanded;
    }

    void collapse() {
        expanded = false;
    }
}
