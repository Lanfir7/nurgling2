package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredCraftTest {

    @Test
    void flourAmountIsWeightNotItemCount() {
        assertEquals(1, MeasuredCraft.itemsToCover(35));
        assertEquals(1, MeasuredCraft.slotsFor(35, 3));
        assertEquals(4, MeasuredCraft.itemsToCover(350));
        assertEquals(2, MeasuredCraft.slotsFor(350, 3));
    }

    @Test
    void waterFromWaterskinsIsLitres() {
        assertEquals(25, MeasuredCraft.waterHundredths("0.25 l of Water"));
        assertEquals(150, MeasuredCraft.waterHundredths("1.50 l of Water"));
        assertEquals(300, MeasuredCraft.waterHundredths("3 l of Water"));
        assertEquals(0, MeasuredCraft.waterHundredths("1.50 l of Salt Water"));
        assertEquals(0, MeasuredCraft.waterHundredths("1.00 l of Milk"));
        assertEquals(0, MeasuredCraft.waterHundredths(null));
    }

    @Test
    void flourAndWaterAreMeasuredIngredients() {
        assertTrue(MeasuredCraft.isWater("Water"));
        assertTrue(MeasuredCraft.pricedByWeight("Flour"));
        assertTrue(MeasuredCraft.pricedByWeight("Wheat Flour"));
        assertFalse(MeasuredCraft.pricedByWeight("Branch"));
        assertFalse(MeasuredCraft.isWater("Branch"));
    }
}
