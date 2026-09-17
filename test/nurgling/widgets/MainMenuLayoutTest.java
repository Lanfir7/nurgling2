package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainMenuLayoutTest {
    @Test
    void draggableFrameExpandsToThePackedMenuInsteadOfClippingItsLastButton() {
        assertEquals(Coord.of(340, 105),
                MainMenuLayout.frameSize(Coord.of(305, 85), Coord.of(35, 20)));
    }

    @Test
    void flushChromeLeavesTheMenuAtItsOwnSize() {
        assertEquals(Coord.of(305, 85),
                MainMenuLayout.frameSize(Coord.of(305, 85), NDraggableLayout.chrome()));
    }
}
