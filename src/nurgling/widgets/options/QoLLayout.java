package nurgling.widgets.options;

import haven.Coord;
import haven.UI;

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

    static QoLLayout forSettings(int width, int gap, int columns,
                                 int leftHeight, int rightHeight) {
        int minCardWidth = (columns > 1) ? UI.scale(245) : width + 1;
        return calculate(width, gap, minCardWidth, leftHeight, rightHeight);
    }

    static int optionWidth(int cardWidth, int optionX, int preferredWidth,
                           int rightPadding) {
        return Math.max(1, Math.min(preferredWidth,
                cardWidth - optionX - rightPadding));
    }

    static int optionX(int requestedX, int minimumInset) {
        return Math.max(requestedX, minimumInset);
    }
}
