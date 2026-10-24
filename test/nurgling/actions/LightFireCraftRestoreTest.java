package nurgling.actions;

import haven.*;
import nurgling.NConfig;
import nurgling.NCore;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.sessions.SessionManager;
import nurgling.sessions.ThreadLocalUI;
import nurgling.tasks.NTask;
import nurgling.widgets.NCraftWindow;
import nurgling.widgets.NMakewindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import sun.misc.Unsafe;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LightFireCraftRestoreTest {
    private static class NoWaitCore extends NCore {
        @Override public void addTask(NTask task) { }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T unconstructed(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static NMakewindow.Spec spec(NMakewindow window, String name) {
        NMakewindow.Spec spec = window.new Spec(() -> null, MessageBuf.nil, -1, new Object[0]);
        spec.name = name;
        spec.categories = true;
        return spec;
    }

    private static boolean checkboxState(NMakewindow window) throws Exception {
        return ((ACheckBox) field(NMakewindow.class, "autoChk").get(window)).state();
    }

    private static boolean savePresetVisible(NMakewindow window) throws Exception {
        return ((Widget) field(NMakewindow.class, "savePresetBtn").get(window)).visible;
    }

    private static void roundTrip(boolean savedAuto, boolean reopenedInitiallyAuto,
                                  boolean savedNoTransfer, boolean reopenedInitiallyNoTransfer) throws Exception {
        NConfig previous = NConfig.current;
        NUI previousThreadUi = ThreadLocalUI.get();
        NUI previousUiInstance = UI.getInstance();
        SessionManager sessions = SessionManager.getInstance();
        NUI previousActiveUi = sessions.getActiveUI();
        NConfig.current = new NConfig();
        NUI taskUi = unconstructed(NUI.class);
        taskUi.core = unconstructed(NoWaitCore.class);
        ThreadLocalUI.set(taskUi);
        try {
            NMakewindow original = new NMakewindow("Milk and Butter");
            NMakewindow.Spec cowMilk = spec(original, "Cow's Milk");
            NMakewindow.Spec goatMilk = spec(original, "Goat's Milk");
            NMakewindow.Spec sheepButter = spec(original, "Sheep's Butter");
            NMakewindow.Spec butterProduct = spec(original, "Butter");
            original.inputs = List.of(cowMilk, goatMilk, sheepButter);
            original.outputs = List.of(butterProduct);
            BufferedImage cowMilkIcon = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
            BufferedImage goatMilkIcon = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            BufferedImage butterIcon = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);
            BufferedImage outputIcon = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
            cowMilk.ing = original.new Ingredient(cowMilkIcon, "Cow's Milk", false);
            goatMilk.ing = original.new Ingredient(goatMilkIcon, "Goat's Milk", true);
            sheepButter.ing = original.new Ingredient(butterIcon, "Sheep's Butter", false);
            butterProduct.ing = original.new Ingredient(outputIcon, "Butter", true);
            original.setAutoMode(savedAuto);
            original.noTransfer.a = savedNoTransfer;

            MenuGrid menu = new MenuGrid();
            NGameUI gui = unconstructed(NGameUI.class);
            NCraftWindow craftWindow = unconstructed(NCraftWindow.class);
            gui.menu = menu;
            gui.craftwnd = craftWindow;
            craftWindow.makeWidget = original;

            NMakewindow reopened = new NMakewindow("Milk and Butter");
            NMakewindow.Spec reopenedCowMilk = spec(reopened, "Cow's Milk");
            NMakewindow.Spec reopenedGoatMilk = spec(reopened, "Goat's Milk");
            NMakewindow.Spec reopenedSheepButter = spec(reopened, "Sheep's Butter");
            NMakewindow.Spec reopenedButter = spec(reopened, "Butter");
            reopened.inputs = List.of(reopenedCowMilk, reopenedGoatMilk, reopenedSheepButter);
            reopened.outputs = List.of(reopenedButter);
            if(reopenedInitiallyAuto)
                reopened.setAutoMode(true);
            reopened.noTransfer.a = reopenedInitiallyNoTransfer;

            MenuGrid.Pagina recipe = new MenuGrid.Pagina(menu, "milk-butter", () -> null) {
                private final MenuGrid.PagButton button = new MenuGrid.PagButton(this) {
                    @Override public KeyBinding binding() { return null; }
                    @Override public String name() { return "Milk and Butter"; }
                    @Override public void use(MenuGrid.Interaction interaction) {
                        craftWindow.makeWidget = reopened;
                    }
                };

                @Override public MenuGrid.PagButton button() { return button; }
            };
            menu.paginae.add(recipe);

            LightFire action = new LightFire(null);
            Method save = LightFire.class.getDeclaredMethod("saveCraftState", NGameUI.class);
            save.setAccessible(true);
            save.invoke(action, gui);

            craftWindow.makeWidget = new NMakewindow("Light fire");
            Method restore = LightFire.class.getDeclaredMethod("restoreCraftState", NGameUI.class);
            restore.setAccessible(true);
            restore.invoke(action, gui);

            assertSame(reopened, craftWindow.makeWidget);
            assertEquals("Milk and Butter", reopened.rcpnm);
            assertEquals(savedAuto, reopened.autoMode);
            assertEquals(savedAuto, checkboxState(reopened));
            assertEquals(savedAuto, reopened.noTransfer.visible);
            assertEquals(savedNoTransfer, reopened.noTransfer.a);
            assertEquals(savedAuto, savePresetVisible(reopened));
            assertIngredient(reopenedCowMilk, "Cow's Milk", false, cowMilkIcon);
            assertIngredient(reopenedGoatMilk, "Goat's Milk", true, goatMilkIcon);
            assertIngredient(reopenedSheepButter, "Sheep's Butter", false, butterIcon);
            assertIngredient(reopenedButter, "Butter", true, outputIcon);
        } finally {
            NConfig.current = previous;
            if(previousThreadUi == null)
                ThreadLocalUI.clear();
            else
                ThreadLocalUI.set(previousThreadUi);
            assertSame(previousActiveUi, sessions.getActiveUI());
            assertSame(previousUiInstance, UI.getInstance());
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void restoresAutoModeAndExactMilkButterSelectionsThroughSaveAndRecipeReopen() throws Exception {
        for (boolean savedNoTransfer : new boolean[]{false, true}) {
            for (boolean reopenedInitiallyNoTransfer : new boolean[]{false, true}) {
                roundTrip(true, false, savedNoTransfer, reopenedInitiallyNoTransfer);
            }
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void restoresManualModeAndUnchecksPreviouslyAutoRecipeWidget() throws Exception {
        for (boolean savedNoTransfer : new boolean[]{false, true}) {
            for (boolean reopenedInitiallyNoTransfer : new boolean[]{false, true}) {
                roundTrip(false, true, savedNoTransfer, reopenedInitiallyNoTransfer);
            }
        }
    }

    @Test
    void missingSavedStateIsANoop() throws Exception {
        NConfig previous = NConfig.current;
        NConfig.current = new NConfig();
        try {
            NMakewindow target = new NMakewindow("Unchanged");
            LightFire action = new LightFire(null);

            action.applySavedCraftState(target);

            assertEquals("Unchanged", target.rcpnm);
            assertFalse(target.autoMode);
            assertFalse(checkboxState(target));
        } finally {
            NConfig.current = previous;
        }
    }

    private static void assertIngredient(NMakewindow.Spec spec, String name, boolean ignored,
                                         BufferedImage image) {
        assertNotNull(spec.ing);
        assertEquals(name, spec.ing.name);
        assertEquals(ignored, spec.ing.isIgnored);
        assertSame(image, spec.ing.img);
    }
}
