package nurgling.widgets;

import haven.Coord;
import haven.DTarget;
import haven.DropTarget;
import haven.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NDraggableWidgetScaleHitTest {
    @Test
    void heldItemDropOnAScaledSlotMapsOntoThatSlot() {
        Coord slot = Coord.of(33, 33);
        double scale = 1.5;
        Coord visual = Coord.of((int)Math.round((slot.x + slot.x / 2.0) * scale),
                                (int)Math.round((slot.y / 2.0) * scale));
        Coord content = NDraggableLayout.toContent(visual, scale, Coord.z);
        assertEquals(1, content.x / slot.x);
        assertEquals(0, content.y / slot.y);
    }

    @Test
    void heldItemEventsMustBeRewrittenIntoContentSpace() {
        assertTrue(NDraggableLayout.remapPointerToContent(new DTarget.Drop(Coord.of(90, 10), null)));
        assertTrue(NDraggableLayout.remapPointerToContent(new DTarget.Interact(Coord.of(90, 10), null)));
        assertTrue(NDraggableLayout.remapPointerToContent(new DropTarget.Drop(Coord.of(90, 10), null)));
        assertFalse(NDraggableLayout.remapPointerToContent(new Widget.MouseDownEvent(Coord.of(90, 10), 1)));
    }
}
