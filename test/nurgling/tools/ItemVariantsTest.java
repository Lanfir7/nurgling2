package nurgling.tools;

import haven.Indir;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;
import haven.res.ui.tt.ingred.Ingredient;
import nurgling.NGItem;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemVariantsTest {
    private static final class Butter extends NGItem {
        private final List<ItemInfo> tooltip;
        private final boolean loading;

        Butter(String milk, boolean loaded, boolean loading) {
            super((Indir<Resource>) () -> null);
            this.tooltip = milk == null ? List.of() : List.of(new Ingredient(this, milk));
            this.infoseq = loaded ? 1 : 0;
            this.loading = loading;
        }

        @Override public String name() { return "Butter"; }
        @Override public List<ItemInfo> info() {
            if (loading) throw new Loading("tooltip pending");
            return tooltip;
        }
    }

    @Test
    void milkIngredientsPickExactButterKindAndUnreadNeverBecomesGeneric() {
        for (String milk : ItemVariants.MILKS) {
            Butter item = new Butter(milk, true, false);
            assertEquals(ItemVariants.key("Butter", milk), ItemVariants.keyOf(item));
            assertTrue(ItemVariants.matches(item, ItemVariants.key("Butter", milk)));
            assertFalse(ItemVariants.matches(item, ItemVariants.key("Butter", "Generic")));
        }
        assertEquals("Butter (Generic)", ItemVariants.keyOf(new Butter(null, true, false)));
        assertNull(ItemVariants.keyOf(new Butter(null, false, false)));
        assertNull(ItemVariants.keyOf(new Butter(null, true, true)));
        assertFalse(ItemVariants.matches(new Butter(null, true, true), "Butter (Generic)"));
        assertNull(ItemVariants.keyOf(new Butter("Unknownmilk", true, false)));
        assertFalse(ItemVariants.matches(new Butter("Unknownmilk", true, false), "Butter (Generic)"));
    }

    @Test
    void legacyPlainNamesAndMilkLiquidsStayDistinct() {
        Butter cow = new Butter("Cowsmilk", true, false);
        assertTrue(ItemVariants.matches(cow, "Butter"));
        assertEquals("Butter", ItemVariants.base("Butter (Cowsmilk)"));
        assertEquals("Cowsmilk", ItemVariants.liquidOf("Cowsmilk"));
        assertEquals("Milk", ItemVariants.liquidOf("Generic Milk"));
        assertNull(ItemVariants.liquidOf("Milk"));
        assertNotEquals(ItemVariants.liquidOf("Cowsmilk"), ItemVariants.liquidOf("Goatsmilk"));
    }

    @Test
    void pickerKeepsPlainInputAndSeparatelyNamedKindsWithSharedIcon() {
        ArrayList<JSONObject> butter = VariantCraftOptions.options("Butter");
        assertTrue(butter.stream().anyMatch(o -> "Butter".equals(o.getString("name"))));
        for (String key : ItemVariants.keys("Butter"))
            assertTrue(butter.stream().anyMatch(o -> key.equals(o.getString("name"))));
        ArrayList<JSONObject> milk = VariantCraftOptions.options("Milk");
        assertTrue(milk.stream().anyMatch(o -> "Milk".equals(o.getString("name"))));
        for (String name : ItemVariants.milkEntries())
            assertTrue(milk.stream().anyMatch(o -> name.equals(o.getString("name"))));
    }
}
