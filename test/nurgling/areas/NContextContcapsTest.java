package nurgling.areas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NContextContcapsTest {
    @Test
    void hiddenHollowIsAKnownStorageContainer() {
        assertEquals("Hidden Hollow", NContext.contcaps.get("gfx/terobjs/map/hiddenhollow"));
    }
}
