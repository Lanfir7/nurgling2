package haven;

import java.awt.Color;

final class TextEntryVisualStyle {
    private static final Color BACKGROUND = new Color(12, 20, 22, 235);
    private static final Color BORDER = new Color(82, 101, 104);
    private static final Color FOCUS_BORDER = new Color(233, 156, 84);

    private TextEntryVisualStyle() {
    }

    static Color background() {
        return BACKGROUND;
    }

    static Color border(boolean focused) {
        return focused ? FOCUS_BORDER : BORDER;
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
