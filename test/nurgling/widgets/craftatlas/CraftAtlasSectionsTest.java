package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasSectionsTest {
    @Test
    void equipmentOpensANestedCategoryMenu() {
        assertTrue(CraftAtlasSections.MAIN.contains("equipment"));
        assertTrue(CraftAtlasSections.isEquipment("equipment"));
        assertTrue(CraftAtlasSections.isEquipment("equipment-rings"));
        assertFalse(CraftAtlasSections.isEquipment("foods"));
        assertTrue(CraftAtlasSections.MAIN.contains("curiosities"));
        assertEquals(Arrays.asList("equipment", "equipment-shoes", "equipment-pants", "equipment-shirts",
                        "equipment-shoulders", "equipment-hats", "equipment-capes", "equipment-cloaks", "equipment-rings"),
                CraftAtlasSections.EQUIPMENT);
    }

    @Test
    void sectionMapsDirectlyToItsCatalogCategory() {
        assertEquals("foods", CraftAtlasSections.category("foods"));
        assertEquals("curiosities", CraftAtlasSections.category("curiosities"));
        assertEquals("equipment", CraftAtlasSections.category("equipment"));
        assertEquals("equipment-cloaks", CraftAtlasSections.category("equipment-cloaks"));
        assertNull(CraftAtlasSections.category("favorites"));
    }
}
