package nurgling.widgets;

import haven.Coord;

/** Pure sizing for the draggable main-menu frame. */
public final class MainMenuLayout {
    private MainMenuLayout() { }

    public static Coord frameSize(Coord content, Coord chrome) {
        Coord safeContent = content == null ? Coord.z : content;
        Coord safeChrome = chrome == null ? Coord.z : chrome;
        return Coord.of(Math.max(0, safeContent.x) + Math.max(0, safeChrome.x),
                Math.max(0, safeContent.y) + Math.max(0, safeChrome.y));
    }
}
