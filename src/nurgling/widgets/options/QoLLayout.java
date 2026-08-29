package nurgling.widgets.options;

import haven.Coord;

final class QoLLayout {
    final int columns;
    final int cardWidth;
    final Coord leftPosition;
    final Coord rightPosition;
    final int contentHeight;

    private QoLLayout(int columns, int cardWidth, Coord leftPosition,
                      Coord rightPosition, int contentHeight) {
        this.columns = columns;
        this.cardWidth = cardWidth;
        this.leftPosition = leftPosition;
        this.rightPosition = rightPosition;
        this.contentHeight = contentHeight;
    }

    static QoLLayout calculate(int width, int gap, int minCardWidth,
                               int leftHeight, int rightHeight) {
        boolean wide = width >= (minCardWidth * 2) + gap;
        if(wide) {
            int cardWidth = (width - gap) / 2;
            return new QoLLayout(2, cardWidth, Coord.z,
                    Coord.of(cardWidth + gap, 0), Math.max(leftHeight, rightHeight));
        }
        return new QoLLayout(1, width, Coord.z,
                Coord.of(0, leftHeight + gap), leftHeight + gap + rightHeight);
    }
}
