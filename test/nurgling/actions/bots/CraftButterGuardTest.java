package nurgling.actions.bots;

import haven.Indir;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;
import haven.WItem;
import haven.res.ui.tt.ingred.Ingredient;
import nurgling.NGItem;
import nurgling.tools.LiquidContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class CraftButterGuardTest {
    private static WItem butter(String milk, boolean loaded) {
        NGItem item = new NGItem((Indir<Resource>) () -> null) {
            @Override public String name() { return "Butter"; }
            @Override public List<ItemInfo> info() {
                if (!loaded) throw new Loading("tooltip pending");
                return milk == null ? List.of() : List.of(new Ingredient(this, milk));
            }
        };
        item.infoseq = loaded ? 1 : 0;
        return new WItem(item);
    }

    @Test
    void selectedKindMustBeTheOnlyButterBeforeCraftingEvenForPrefilledInventory() {
        String selected = "Butter (Cowsmilk)";
        assertNull(Craft.prefilledButterProblem(List.of(butter("Cowsmilk", true)), selected));
        assertNotNull(Craft.prefilledButterProblem(List.of(butter("Cowsmilk", true), butter("Goatsmilk", true)), selected));
        assertNotNull(Craft.prefilledButterProblem(List.of(butter(null, false)), selected));
        assertFalse(Craft.hasWrongButter(List.of(butter("Cowsmilk", true)), selected));
        assertTrue(Craft.hasWrongButter(List.of(butter("Cowsmilk", true), butter("Goatsmilk", true)), selected));
        assertTrue(Craft.hasWrongButter(List.of(butter(null, true)), selected));
        assertTrue(Craft.hasWrongButter(List.of(butter(null, false)), selected));
    }

    @Test
    void craftCountsOnlyTheSelectedOpenedMilkContent() throws Exception {
        Constructor<LiquidContent> constructor = LiquidContent.class.getDeclaredConstructor(
                String.class, double.class, String.class, Double.class);
        constructor.setAccessible(true);
        LiquidContent cow = constructor.newInstance("Cowsmilk", 12.5, "l", null);
        LiquidContent goat = constructor.newInstance("Goatsmilk", 17.0, "l", null);
        LiquidContent generic = constructor.newInstance("Milk", 9.0, "l", null);
        assertEquals(12.5, Craft.selectedLiquidAmount(cow, "Cowsmilk"));
        assertEquals(-1, Craft.selectedLiquidAmount(goat, "Cowsmilk"));
        assertEquals(-1, Craft.selectedLiquidAmount(generic, "Cowsmilk"));
        assertEquals(9.0, Craft.selectedLiquidAmount(generic, "Generic Milk"));
        assertEquals(-1, Craft.selectedLiquidAmount(cow, "Generic Milk"));
    }
}
