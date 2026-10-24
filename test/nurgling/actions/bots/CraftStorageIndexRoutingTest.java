package nurgling.actions.bots;

import nurgling.NConfig;
import nurgling.areas.NArea;
import nurgling.widgets.NMakewindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CraftStorageIndexRoutingTest {
    private NConfig prior;

    @BeforeEach void config() {
        prior = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach void restore() { NConfig.current = prior; }

    private static NMakewindow.Spec spec(String name) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        NMakewindow.Spec spec = (NMakewindow.Spec) ((Unsafe) field.get(null))
                .allocateInstance(NMakewindow.Spec.class);
        spec.name = name;
        spec.count = 1;
        return spec;
    }

    @Test void defaultOffAndExactAreaLessInputOnly() throws Exception {
        assertEquals(Boolean.FALSE, NConfig.get(NConfig.Key.craftUseStorageIndex));
        NMakewindow.Spec barley = spec("Barley Seed");
        assertTrue(Craft.storageIndexEligible(barley, "Barley Seed"));
        barley.selectedZone = new NArea("Chosen seeds");
        assertFalse(Craft.storageIndexEligible(barley, "Barley Seed"));
        barley.selectedZone = null;
        barley.isInventory = true;
        assertFalse(Craft.storageIndexEligible(barley, "Barley Seed"));
        barley.isInventory = false;
        barley.isSubCraft = true;
        assertFalse(Craft.storageIndexEligible(barley, "Barley Seed"));
        barley.isSubCraft = false;
        barley.useCategory = true;
        assertFalse(Craft.storageIndexEligible(barley, "Barley Seed"));
        assertFalse(Craft.storageIndexEligible(spec("Butter"), "Butter"));
    }
}
