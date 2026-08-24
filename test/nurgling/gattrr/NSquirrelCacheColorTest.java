package nurgling.gattrr;

import nurgling.tools.MaterialFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NSquirrelCacheColorTest {
    @Test
    void emptyMarkerIsGreen() {
        assertEquals(MaterialFactory.Status.FREE, NSquirrelCacheColor.statusOf(2));
    }

    @Test
    void stateSixIsRed() {
        assertEquals(MaterialFactory.Status.FULL, NSquirrelCacheColor.statusOf(6));
    }

    @Test
    void otherMarkerIsYellow() {
        assertEquals(MaterialFactory.Status.NOTFREE, NSquirrelCacheColor.statusOf(0));
        assertEquals(MaterialFactory.Status.NOTFREE, NSquirrelCacheColor.statusOf(4));
    }
}
