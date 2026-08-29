package nurgling;

import haven.Coord;

final class NWindowTitleLayout {
    final Coord position;
    final int width;
    final boolean visible;

    private NWindowTitleLayout(Coord position, int width, boolean visible) {
        this.position = position;
        this.width = width;
        this.visible = visible;
    }

    static NWindowTitleLayout calculate(int windowWidth, int titleWidth, int closeX,
                                        int preferredWidth, int minWidth, int gap,
                                        int titleHeight, int widgetHeight) {
        int left = titleWidth + (gap * 2);
        int right = Math.min(windowWidth, closeX) - gap;
        int width = Math.min(preferredWidth, Math.max(0, right - left));
        boolean visible = width >= minWidth;
        Coord position = Coord.of(right - width,
                Math.max(0, (titleHeight - widgetHeight) / 2));
        return new NWindowTitleLayout(position, width, visible);
    }
}
