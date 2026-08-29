package nurgling.actions.bots;

import nurgling.tools.CraftTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftCountCapTest {

    @Test
    void storedCountIsTheMaxIterations() {
        Craft craft = new Craft(null, 10);
        assertEquals(10, CraftTarget.capIterations(craft.count, 9999));
        assertEquals(10, CraftTarget.capIterations(craft.count, 10));
        assertEquals(3, CraftTarget.capIterations(craft.count, 3));
        assertTrue(CraftTarget.reachedCap(10, craft.count));
        assertFalse(CraftTarget.reachedCap(9, craft.count));
    }

    @Test
    void craftAllKeepsGoingUntilMaterialsEnd() {
        Craft craft = new Craft(null, CraftTarget.ALL);
        assertTrue(CraftTarget.isAll(craft.count));
        assertEquals(80, CraftTarget.capIterations(craft.count, 80));
        assertFalse(CraftTarget.reachedCap(80, craft.count));
    }
}
