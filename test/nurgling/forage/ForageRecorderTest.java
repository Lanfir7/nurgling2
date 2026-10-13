package nurgling.forage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForageRecorderTest {
    @Test void unrelatedSoleInventoryArrivalCannotBeAttributedToThePickedGob() {
        assertNull(ForageRecorder.matchingRes("gfx/terobjs/herbs/blueberry",
            List.of("gfx/invobjs/ore/iron")));
        assertEquals("gfx/invobjs/herbs/blueberry",
            ForageRecorder.matchingRes("gfx/terobjs/herbs/blueberry",
                List.of("gfx/invobjs/ore/iron", "gfx/invobjs/herbs/blueberry")));
    }

    @Test void cataloguedDifferentResourceNamesAreAcceptedOnlyForThatGob() {
        assertTrue(ForageRecorder.matchesKnownProduct(
            "gfx/terobjs/bushes/blackberrybush", " blackberry "));
        assertFalse(ForageRecorder.matchesKnownProduct(
            "gfx/terobjs/bushes/blackcurrant", "Blackberry"));
    }
}
