package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableInventoryExtensionTest {

    @Test
    void knownTableResourcesMatch() {
        assertTrue(TableInventoryExtension.isTableRes("gfx/terobjs/furn/table-stone"));
        assertTrue(TableInventoryExtension.isTableRes("gfx/terobjs/furn/table-rustic"));
        assertTrue(TableInventoryExtension.isTableRes("gfx/terobjs/furn/table-elegant"));
        assertTrue(TableInventoryExtension.isTableRes("gfx/terobjs/furn/cottagetable"));
    }

    @Test
    void newFurnTableVariantsMatch() {
        assertTrue(TableInventoryExtension.isTableRes("gfx/terobjs/furn/table-oak"));
        assertTrue(TableInventoryExtension.isTableRes("gfx/terobjs/table-stone"));
    }

    @Test
    void herbalistAndStudyAreNotFeastTables() {
        assertFalse(TableInventoryExtension.isTableRes("gfx/terobjs/htable"));
        assertFalse(TableInventoryExtension.isTableRes("gfx/terobjs/studydesk"));
        assertFalse(TableInventoryExtension.isTableRes(null));
    }

    @Test
    void tableWindowCaptionsMatch() {
        assertTrue(TableInventoryExtension.isTableWindowCap("Table"));
        assertTrue(TableInventoryExtension.isTableWindowCap("Rustic Table"));
        assertTrue(TableInventoryExtension.isTableWindowCap("Stone Table"));
        assertFalse(TableInventoryExtension.isTableWindowCap("Herbalist Table"));
        assertFalse(TableInventoryExtension.isTableWindowCap("Alchemist's Table"));
        assertFalse(TableInventoryExtension.isTableWindowCap("Study Desk"));
        assertFalse(TableInventoryExtension.isTableWindowCap(null));
    }

    @Test
    void feastButtonTextIsLoose() {
        assertTrue(TableInventoryExtension.isFeastText("Feast!"));
        assertTrue(TableInventoryExtension.isFeastText("Feast"));
        assertTrue(TableInventoryExtension.isFeastText("feast!"));
        assertFalse(TableInventoryExtension.isFeastText("Craft"));
        assertFalse(TableInventoryExtension.isFeastText(null));
    }

    @Test
    void hungerAndFoodLabelsAreLoose() {
        assertTrue(TableInventoryExtension.isHungerLabel("Hunger modifier: 35%"));
        assertTrue(TableInventoryExtension.isHungerLabel("Hunger reduction: 35%"));
        assertTrue(TableInventoryExtension.isFoodEventLabel("Food event bonus: 12%"));
        assertTrue(TableInventoryExtension.isFoodEventLabel("Food Event Bonus: 12%"));
        assertFalse(TableInventoryExtension.isHungerLabel("Food event bonus: 12%"));
        assertFalse(TableInventoryExtension.isFoodEventLabel("Hunger modifier: 35%"));
    }
}
