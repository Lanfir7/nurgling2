package nurgling.widgets;

import haven.Coord;

final class AreasWindowCollapseState {
    enum Direction { LEFT, RIGHT }

    private final Coord expandedSize;
    private final Coord compactSize;
    private final int searchWidth;
    private boolean expanded;

    static int compactWidth(int listRight, int tabWidth) {
        return listRight + tabWidth;
    }

    static int alignedPanelWidth(int listWidth) {
        return listWidth;
    }

    AreasWindowCollapseState(Coord expandedSize, int compactWidth, int searchWidth) {
        this.expandedSize = new Coord(expandedSize);
        this.compactSize = Coord.of(compactWidth, expandedSize.y);
        this.searchWidth = searchWidth;
    }

    boolean expanded() {
        return expanded;
    }

    boolean detailsVisible() {
        return expanded;
    }

    int searchWidth() {
        return searchWidth;
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
