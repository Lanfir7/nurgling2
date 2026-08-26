package nurgling;

import haven.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NToolBeltTipTest {
    @Test
    void emptySlotHasNoTip() {
        assertNull(NToolBeltTip.from(null, false));
    }

    @Test
    void unknownItemHasNoTip() {
        assertNull(NToolBeltTip.from("not-a-slot", false));
    }

    @Test
    void widgetTipIsForwarded() {
        Widget w = new Widget();
        w.tooltip = "Chop";
        assertEquals("Chop", NToolBeltTip.from(w, false));
    }
}
