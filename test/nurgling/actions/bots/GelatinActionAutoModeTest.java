package nurgling.actions.bots;

import haven.*;
import nurgling.NConfig;
import nurgling.widgets.NMakewindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GelatinActionAutoModeTest {
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static NMakewindow.Spec spec(NMakewindow window, String name, boolean ignored) {
        NMakewindow.Spec spec = window.new Spec(() -> null, MessageBuf.nil, -1, new Object[0]);
        spec.name = name;
        spec.categories = true;
        spec.ing = window.new Ingredient(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), name, ignored);
        return spec;
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void gelatinAutoModeUsesVisibleControlAndPreservesSelectionsAndNoTransferChoice() throws Exception {
        NConfig previous = NConfig.current;
        NConfig config = new NConfig();
        NConfig.current = config;
        try {
            Method configure = GelatinAction.class.getDeclaredMethod("configureCraftMode", NMakewindow.class);
            configure.setAccessible(true);
            GelatinAction action = new GelatinAction();
            for (boolean initiallyAuto : new boolean[]{false, true}) {
                for (boolean noTransfer : new boolean[]{false, true}) {
                    for (boolean storageEnabled : new boolean[]{false, true}) {
                        for (boolean storagePreference : new boolean[]{false, true}) {
                            NConfig.set(NConfig.Key.ndbenable, storageEnabled);
                            NConfig.set(NConfig.Key.craftUseStorageIndex, storagePreference);
                            NMakewindow window = new NMakewindow("Gelatin");
                            if (initiallyAuto)
                                window.setAutoMode(true);
                            NMakewindow.Spec input = spec(window, "Silk Purl", true);
                            NMakewindow.Spec output = spec(window, "Gelatin", false);
                            NMakewindow.Ingredient inputIngredient = input.ing;
                            NMakewindow.Ingredient outputIngredient = output.ing;
                            window.inputs = List.of(input);
                            window.outputs = List.of(output);
                            window.noTransfer.a = noTransfer;

                            configure.invoke(action, window);

                            assertTrue(window.autoMode);
                            assertTrue(((ACheckBox) field(window, "autoChk")).state());
                            assertTrue(window.noTransfer.visible);
                            assertEquals(noTransfer, window.noTransfer.a);
                            assertTrue(((Widget) field(window, "savePresetBtn")).visible);
                            CheckBox storageIndex = (CheckBox) field(window, "storageIndexBox");
                            assertEquals(storageEnabled, storageIndex.visible);
                            assertEquals(storagePreference, storageIndex.a);
                            assertSame(input, window.inputs.get(0));
                            assertSame(output, window.outputs.get(0));
                            assertSame(inputIngredient, input.ing);
                            assertSame(outputIngredient, output.ing);
                            assertEquals("Silk Purl", input.ing.name);
                            assertTrue(input.ing.isIgnored);
                            assertEquals("Gelatin", output.ing.name);
                            assertFalse(output.ing.isIgnored);
                            assertEquals(storageEnabled, NConfig.get(NConfig.Key.ndbenable));
                            assertEquals(storagePreference, NConfig.get(NConfig.Key.craftUseStorageIndex));
                        }
                    }
                }
            }
            assertSame(config, NConfig.current);
        } finally {
            NConfig.current = previous;
        }
    }
}
