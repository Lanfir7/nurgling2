package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void iceBearToothIsGroupedWithFineboneAndBoneMaterial() {
        assertTrue(VSpec.getCategory("Ice Bear Tooth").contains("Finebone"));
        assertTrue(VSpec.getCategory("Ice Bear Tooth").contains("Bone Material"));
        assertEquals(4, StackSupporter.getFullStackSize("Ice Bear Tooth"));
    }

    @Test
    void iceBearAndNarwhalMeatUseTheRawMeatStack() {
        assertTrue(VSpec.getCategory("Raw Ice Bear").contains("Raw Meat"));
        assertTrue(VSpec.getCategory("Raw Narwhal").contains("Raw Meat"));
        assertEquals(5, StackSupporter.getFullStackSize("Raw Ice Bear"));
        assertEquals(5, StackSupporter.getFullStackSize("Raw Narwhal"));
    }

    @Test
    void narwhalTuskIsBoneMaterial() {
        assertTrue(VSpec.getCategory("Narwhal Tusk").contains("Finebone"));
        assertTrue(VSpec.getCategory("Narwhal Tusk").contains("Bone Material"));
        assertEquals(4, StackSupporter.getFullStackSize("Narwhal Tusk"));
    }

    @Test
    void lynxClawsUseTheirServerStackSize() {
        assertTrue(VSpec.getCategory("Lynx Claws").contains("Stackable Curiosities"));
        assertEquals(4, StackSupporter.getFullStackSize("Lynx Claws"));
        assertFalse(StackSupporter.isKnownUnstackableName("Lynx Claws"));
    }

    @Test
    void jotunClamMeatUsesItsServerStackSize() {
        assertEquals(5, StackSupporter.getFullStackSize("Jotun Clam Meat"));
    }

    @Test
    void curiousNeedleIsAStackableCuriosityWithServerStackSize() {
        assertTrue(VSpec.getCategory("Curious Needle").contains("Stackable Curiosities"));
        assertFalse(VSpec.getCategory("Curious Needle").contains("NonStackable"));
        assertEquals(5, StackSupporter.getFullStackSize("Curious Needle"));
    }

    @Test
    void peculiarFlotsamIsAStackableCuriosity() {
        assertTrue(VSpec.getCategory("Peculiar Flotsam").contains("Stackable Curiosities"));
        assertEquals(4, StackSupporter.getFullStackSize("Peculiar Flotsam"));
    }

    @Test
    void existingLocalCustomStackRulesRemainUnchanged() {
        assertEquals(5, StackSupporter.getFullStackSize("Branch"));
        assertEquals(4, StackSupporter.getFullStackSize("Standing Grass"));
    }
}
