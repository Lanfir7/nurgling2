package nurgling.actions;

import haven.Indir;
import haven.ItemInfo;
import haven.Resource;
import haven.res.ui.tt.ingred.Ingredient;
import nurgling.NGItem;
import nurgling.areas.NContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TransferItems2VariantRoutingTest {
    private static final class Butter extends NGItem {
        private final String milk;
        Butter(String milk) {
            super((Indir<Resource>) () -> null);
            this.milk = milk;
            infoseq = 1;
            quality = 20f;
        }
        @Override public String name() { return "Butter"; }
        @Override public List<ItemInfo> info() {
            return milk == null ? List.of() : List.of(new Ingredient(this, milk));
        }
    }

    private static NContext contextWithCowArea(double threshold) throws Exception {
        NContext context = new NContext(null);
        Field field = NContext.class.getDeclaredField("outAreas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, TreeMap<Double, String>> routes =
                (HashMap<String, TreeMap<Double, String>>) field.get(context);
        TreeMap<Double, String> cow = new TreeMap<>();
        cow.put(threshold, "cow-area");
        routes.put("Butter (Cowsmilk)", cow);
        return context;
    }

    @Test
    void plainButterKeepsLegacyRouteWithoutCompetingKindArea() {
        NContext context = new NContext(null);
        assertNull(TransferItems2.kindFilter(context, "Butter", 0, null));
        assertFalse(TransferItems2.needsButterKinds(List.of("Butter"), context));
        assertFalse(TransferItems2.needsButterKinds(List.of("Straw", "Cheese"), context));
    }

    @Test
    void variantDestinationSplitsOnlyItsQualityBandAndLeavesOtherKindsOnPlainRoute() throws Exception {
        NContext context = contextWithCowArea(20);
        assertNull(TransferItems2.kindFilter(context, "Butter", 1, 20.0));
        Predicate<NGItem> plain = TransferItems2.kindFilter(context, "Butter", 1, 21.0);
        assertNotNull(plain);
        assertFalse(plain.test(new Butter("Cowsmilk")));
        assertTrue(plain.test(new Butter("Goatsmilk")));
        assertTrue(plain.test(new Butter(null)));
        Predicate<NGItem> cow = TransferItems2.kindFilter(context, "Butter (Cowsmilk)", 20, null);
        assertTrue(cow.test(new Butter("Cowsmilk")));
        assertFalse(cow.test(new Butter("Goatsmilk")));
        assertTrue(TransferItems2.needsButterKinds(List.of("Butter"), context));
        assertFalse(TransferItems2.needsButterKinds(List.of("Straw"), context));
    }
}
