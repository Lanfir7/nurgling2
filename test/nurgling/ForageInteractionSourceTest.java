package nurgling;

import haven.Coord2d;
import haven.Gob;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ForageInteractionSourceTest {
    @Test
    void programmaticRightClickBecomesFlowerMenuSource() {
        Gob gob = new Gob(null, Coord2d.of(10, 20), 42);
        AtomicReference<Gob> source = new AtomicReference<>();

        ForageInteractionSource.remember(gob, 3, source::set);

        assertSame(gob, source.get());
    }

    @Test
    void programmaticLeftClickDoesNotReplaceFlowerMenuSource() {
        Gob original = new Gob(null, Coord2d.of(10, 20), 42);
        Gob leftClicked = new Gob(null, Coord2d.of(30, 40), 43);
        AtomicReference<Gob> source = new AtomicReference<>(original);

        ForageInteractionSource.remember(leftClicked, 1, source::set);

        assertSame(original, source.get());
    }
}
