package nurgling;

import haven.Gob;

import java.util.function.Consumer;

/** Keeps programmatic gob clicks equivalent to clicks made directly on the world map. */
public final class ForageInteractionSource {
    private ForageInteractionSource() {
    }

    public static void remember(Gob gob, int button, Consumer<Gob> recorder) {
        if (button == 3 && gob != null && recorder != null) {
            recorder.accept(gob);
        }
    }
}
