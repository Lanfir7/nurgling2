package haven;

import java.awt.Color;

final class DropboxVisualStyle {
    private static final Color FIELD_BACKGROUND = new Color(20, 32, 34, 245);
    private static final Color ARROW_BACKGROUND = new Color(45, 62, 65, 255);
    private static final Color POPUP_BACKGROUND = new Color(12, 22, 24, 250);
    private static final Color BORDER = new Color(89, 111, 115, 255);
    private static final Color OPEN_BORDER = new Color(233, 156, 84, 255);
    private static final Color HOVER_BACKGROUND = new Color(57, 82, 86, 245);
    private static final Color SELECTED_BACKGROUND = new Color(120, 84, 30, 230);

    private DropboxVisualStyle() {
    }

    static Color fieldBackground() {
        return FIELD_BACKGROUND;
    }

    static Color arrowBackground() {
        return ARROW_BACKGROUND;
    }

    static Color popupBackground() {
        return POPUP_BACKGROUND;
    }

    static Color border(boolean open) {
        return open ? OPEN_BORDER : BORDER;
    }

    static Color hoverBackground() {
        return HOVER_BACKGROUND;
    }

    static Color selectedBackground() {
        return SELECTED_BACKGROUND;
    }

    static BorderFrame[] borderFrames(Coord size, int thickness) {
        int count = Math.max(0, Math.min(thickness,
                Math.min(size.x, size.y) / 2));
        BorderFrame[] frames = new BorderFrame[count];
        for(int i = 0; i < count; i++)
            frames[i] = new BorderFrame(Coord.of(i, i),
                    size.sub(i * 2, i * 2));
        return frames;
    }

    static final class BorderFrame {
        final Coord position;
        final Coord size;

        BorderFrame(Coord position, Coord size) {
            this.position = position;
            this.size = size;
        }
    }
}
