package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackSupporterNewItemsTest {
    @Test
    void duckEggUsesEggStackRule() {
        assertTrue(VSpec.getCategory("Duck Egg").contains("Egg"));
        assertEquals(3, StackSupporter.getFullStackSize("Duck Egg"));
    }

    @Test
    void craneMeatUsesPoultryStackRule() {
        assertTrue(VSpec.getCategory("Crane Meat").contains("Poultry"));
        assertEquals(5, StackSupporter.getFullStackSize("Crane Meat"));
    }

    @Test
    void mouflonHornIsGroupedWithBoneMaterial() {
        assertTrue(VSpec.getCategory("Mouflon Horn").contains("Finebone"));
        assertTrue(VSpec.getCategory("Mouflon Horn").contains("Bone Material"));
        assertEquals(1, StackSupporter.getFullStackSize("Mouflon Horn"));
    }
}
