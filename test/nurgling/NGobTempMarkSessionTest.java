package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NGobTempMarkSessionTest {
    @Test
    void tempMarksStayOnOwnerWhenAnotherWindowIsFocused() {
        assertEquals("surface", NGob.tempMarkSession("surface", "mine"));
        assertEquals("mine", NGob.tempMarkSession("mine", "surface"));
    }

    @Test
    void missingOwnerDoesNotPlaceTempMarksOnFocusedWindow() {
        assertNull(NGob.tempMarkSession(null, "mine"));
    }
}
