package nurgling.actions.bots;

import java.util.Arrays;
import java.util.Collections;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriedFishMaterialsTest {
    @Test
    void emptyInputUsesInventoryFish() {
        assertTrue(FriedFishMaterials.fromInventory(false));
        assertFalse(FriedFishMaterials.fromInventory(true));
    }

    @Test
    void cookedFishStaysInInventoryWhenOutputIsEmpty() {
        assertFalse(FriedFishMaterials.toContainers(false));
        assertTrue(FriedFishMaterials.toContainers(true));
    }

    @Test
    void closestFireplaceIsTheOneNearestThePlayer() {
        Coord2d player = new Coord2d(0, 0);
        Coord2d far = new Coord2d(100, 0);
        Coord2d near = new Coord2d(11, 0);
        Coord2d picked = FriedFishMaterials.closestSpot(player, Arrays.asList(far, near));
        assertEquals(11.0, picked.x, 0.01);
        assertEquals(0.0, picked.y, 0.01);
        assertNull(FriedFishMaterials.closestSpot(player, Collections.emptyList()));
        assertNull(FriedFishMaterials.closestSpot(null, Arrays.asList(near)));
    }

    @Test
    void roastspitFireplaceMustHaveSpitAndNotBeBlocked() {
        assertTrue(FriedFishMaterials.isUsableRoastspitPow(0, true));
        assertTrue(FriedFishMaterials.isUsableRoastspitPow(5, true));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(0, false));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(16, true));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(32, true));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(48, true));
    }

    @Test
    void spitIsBusyOnlyWhileRawFishIsCookingOnALitFire() {
        assertTrue(FriedFishMaterials.isSpitReadyToWork(null, 5));
        assertTrue(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/meat-herring", 5));
        assertFalse(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/meat-herring-raw", 5));
        assertTrue(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/meat-herring-raw", 0));
    }

    @Test
    void keepsWorkingUntilFishAndSpitAreEmpty() {
        assertTrue(FriedFishMaterials.shouldKeepWorking(true, false, true, false));
        assertTrue(FriedFishMaterials.shouldKeepWorking(false, true, false, false));
        assertTrue(FriedFishMaterials.shouldKeepWorking(true, false, false, true));
        assertFalse(FriedFishMaterials.shouldKeepWorking(true, false, false, false));
        assertFalse(FriedFishMaterials.shouldKeepWorking(false, false, true, false));
    }

    @Test
    void cookedSpitroastIsNotRawInventoryFish() {
        assertTrue(FriedFishMaterials.isCookedSpitroast("Spitroast Herring"));
        assertFalse(FriedFishMaterials.isCookedSpitroast("Herring"));
        assertFalse(FriedFishMaterials.isCookedSpitroast(null));
    }
}
