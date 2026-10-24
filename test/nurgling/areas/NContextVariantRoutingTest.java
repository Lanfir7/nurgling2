package nurgling.areas;

import haven.Indir;
import haven.ItemInfo;
import haven.Resource;
import haven.res.ui.tt.ingred.Ingredient;
import nurgling.NGItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NContextVariantRoutingTest {
    private static final class Butter extends NGItem {
        private final String milk;
        Butter(String milk, int infoSeq) {
            super((Indir<Resource>) () -> null);
            this.milk = milk;
            this.infoseq = infoSeq;
        }
        @Override public String name() { return "Butter"; }
        @Override public List<ItemInfo> info() {
            return milk == null ? List.of() : List.of(new Ingredient(this, milk));
        }
    }

    @Test
    void kindSpecificPutWinsOnlyAtItsQualityThresholdAndUnknownIsNotPlain() throws Exception {
        NContext context = new NContext(null);
        Field field = NContext.class.getDeclaredField("outAreas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, TreeMap<Double, String>> routes =
                (HashMap<String, TreeMap<Double, String>>) field.get(context);
        TreeMap<Double, String> cow = new TreeMap<>();
        cow.put(20.0, "cow-area");
        routes.put("Butter (Cowsmilk)", cow);
        assertEquals("Butter", context.outKey(new Butter("Cowsmilk", 1), 19.99));
        assertEquals("Butter (Cowsmilk)", context.outKey(new Butter("Cowsmilk", 1), 20));
        assertEquals("Butter", context.outKey(new Butter("Goatsmilk", 1), 30));
        assertEquals("Butter", context.outKey(new Butter(null, 1), 30));
        assertNull(context.outKey(new Butter(null, 0), 30));
    }

    @Test
    void barrelWindowWaitStopsAtItsTickLimitAndCanAcceptLateContent() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        assertFalse(NContext.waitFor(() -> false, 3, task -> {
            for (int i = 0; i < 10 && !task.baseCheck(); i++)
                checks.incrementAndGet();
        }));
        assertEquals(3, checks.get());

        AtomicInteger ready = new AtomicInteger();
        assertTrue(NContext.waitFor(() -> ready.get() >= 2, 3, task -> {
            for (int i = 0; i < 10 && !task.baseCheck(); i++)
                ready.incrementAndGet();
        }));
        assertEquals(2, ready.get());
    }
}
