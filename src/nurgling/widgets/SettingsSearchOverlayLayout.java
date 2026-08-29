package nurgling.widgets;

import haven.Coord;

final class SettingsSearchOverlayLayout {
    final Coord position;
    final Coord size;

    private SettingsSearchOverlayLayout(Coord position, Coord size) {
        this.position = position;
        this.size = size;
    }

    static SettingsSearchOverlayLayout calculate(Coord searchPosition, Coord searchSize,
                                                  Coord hostSize, Coord dropdownSize, int gap) {
        int width = Math.min(dropdownSize.x, Math.max(1, hostSize.x));
        int height = Math.min(dropdownSize.y, Math.max(1, hostSize.y));
        int x = Math.max(0, Math.min(searchPosition.x, hostSize.x - width));
        int desiredY = searchPosition.y + searchSize.y + gap;
        int y = Math.max(0, Math.min(desiredY, hostSize.y - height));
        return new SettingsSearchOverlayLayout(Coord.of(x, y), Coord.of(width, height));
    }
}
