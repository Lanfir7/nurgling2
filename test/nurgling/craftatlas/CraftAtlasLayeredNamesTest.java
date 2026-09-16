package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftAtlasLayeredNamesTest {
    @Test
    void layeredMeatKeepsTheSpecificAnimalName() {
        CraftAtlasLayeredNames.Identity identity = CraftAtlasLayeredNames.resolve(
                "gfx/invobjs/meat-raw", "Meat",
                List.of("gfx/invobjs/meat-raw", "gfx/invobjs/meat-badger"));

        assertEquals("Raw Badger", identity.name);
        assertEquals("gfx/invobjs/meat-raw+gfx/invobjs/meat-badger", identity.resource);
    }

    @Test
    void ordinaryItemsKeepTheFallbackName() {
        CraftAtlasLayeredNames.Identity identity = CraftAtlasLayeredNames.resolve(
                "gfx/invobjs/intestines", "Intestines", List.of());

        assertEquals("Intestines", identity.name);
        assertEquals("gfx/invobjs/intestines", identity.resource);
    }
}
