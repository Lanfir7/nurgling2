package nurgling.widgets;

import haven.Coord;

public final class FeedbackEntryLayout {
    private FeedbackEntryLayout() {
    }

    public static Coord below(Coord position, Coord size, int gap) {
        return Coord.of(position.x, position.y + size.y + gap);
    }
}
