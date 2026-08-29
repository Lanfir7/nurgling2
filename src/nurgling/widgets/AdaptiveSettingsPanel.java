package nurgling.widgets;

import haven.Coord;

public interface AdaptiveSettingsPanel {
    void fitToWidth(int width, int columns);

    default void fitToViewport(Coord viewport, int columns) {
        fitToWidth(viewport.x, columns);
    }

    default boolean ownsVerticalScroll() {
        return false;
    }
}
