package nurgling.widgets;

import haven.Coord;

final class EquipmentStatsPanelLayout {
    private EquipmentStatsPanelLayout() {
    }

    static Coord statsPosition(Coord[] slotCoords, Coord slotSize, int gap) {
        int rightEdge = 0;
        for (Coord slot : slotCoords) {
            rightEdge = Math.max(rightEdge, slot.x + slotSize.x);
        }
        return new Coord(rightEdge + gap, 0);
    }

    static Coord expandedSize(Coord equipmentSize, Coord statsPosition, int statsWidth) {
        return new Coord(Math.max(equipmentSize.x, statsPosition.x + statsWidth), equipmentSize.y);
    }

    static Coord indicatorOrigin(int equipmentWidth, int textWidth, int rightPadding, int y) {
        return new Coord(equipmentWidth - textWidth - rightPadding, y);
    }

    static boolean hitsIndicator(Coord click, Coord origin, int textWidth, int height) {
        return click.isect(origin, new Coord(height + textWidth, height));
    }
}
