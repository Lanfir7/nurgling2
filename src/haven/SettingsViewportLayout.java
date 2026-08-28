package haven;

final class SettingsViewportLayout {
    private SettingsViewportLayout() {
    }

    static Coord fit(Coord desired, Coord measuredWindow, Coord rootSize,
                     int margin, Coord minimum) {
        Coord maximumWindow = Coord.of(
                Math.max(minimum.x, rootSize.x - (margin * 2)),
                Math.max(minimum.y, rootSize.y - (margin * 2)));
        int overflowX = Math.max(0, measuredWindow.x - maximumWindow.x);
        int overflowY = Math.max(0, measuredWindow.y - maximumWindow.y);
        return Coord.of(
                Math.max(minimum.x, desired.x - overflowX),
                Math.max(minimum.y, desired.y - overflowY));
    }

    static Area visibleArea(Coord rootSize, Area parentArea) {
        Area root = Area.sized(Coord.z, rootSize);
        if(parentArea == null)
            return(root);
        Area visible = root.overlap(parentArea);
        return((visible == null) ? root : visible);
    }

    static boolean canFit(Widget current, Widget parent, Coord rootSize) {
        return((current != null) && (parent != null) && (rootSize != null));
    }
}
