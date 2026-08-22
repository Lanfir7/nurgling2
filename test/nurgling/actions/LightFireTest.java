package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightFireTest {
    @Test
    void twoBranchPiecesInInventorySkipFuelArea() {
        assertFalse(LightFire.needsFuelArea(2));
        assertFalse(LightFire.needsFuelArea(5));
    }

    @Test
    void fewerThanTwoBranchPiecesNeedFuelArea() {
        assertTrue(LightFire.needsFuelArea(0));
        assertTrue(LightFire.needsFuelArea(1));
    }
}
