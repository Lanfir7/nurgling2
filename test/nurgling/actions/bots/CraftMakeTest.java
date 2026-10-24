package nurgling.actions.bots;

import haven.GameUI;
import haven.Widget;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.tasks.NTask;
import nurgling.widgets.NMakewindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftMakeTest {
    private NConfig prior;

    @BeforeEach void config() {
        prior = NConfig.current;
        if (prior == null) NConfig.current = new NConfig();
    }

    @AfterEach void restore() { NConfig.current = prior; }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    @Test
    void storageProgressThatNeverSettlesHasBoundedFailureWhileLegacyWaitRemainsOpen() throws Exception {
        NGameUI gui = (NGameUI) unsafe().allocateInstance(NGameUI.class);
        gui.prog = (GameUI.Progress) unsafe().allocateInstance(GameUI.Progress.class);
        gui.prog.prog = 0.5;
        gui.prog.visible = true;
        NMakewindow recipe = (NMakewindow) unsafe().allocateInstance(NMakewindow.class);
        recipe.parent = new Widget();
        boolean[] closed = {false}, failed = {false}, completed = {false};
        NTask bounded = CraftMake.completionTask(gui, recipe, closed, failed, completed, true);
        for (int tick = 0; tick < 300; tick++) assertFalse(bounded.baseCheck());
        assertTrue(bounded.baseCheck());
        assertFalse(completed[0]);
        assertFalse(closed[0]);
        assertFalse(failed[0]);

        NTask legacy = CraftMake.completionTask(gui, recipe,
                new boolean[1], new boolean[1], new boolean[1], false);
        for (int tick = 0; tick < 350; tick++) assertFalse(legacy.baseCheck());
    }

    @Test
    void missingWindowIsClosed() {
        assertFalse(CraftMake.windowOpen(null));
    }

    @Test
    void stopWhenWindowClosedOrMakeFailed() {
        assertTrue(CraftMake.shouldStop(true, false));
        assertTrue(CraftMake.shouldStop(false, true));
        assertFalse(CraftMake.shouldStop(false, false));
    }

    @Test
    void noProgWithoutErrorIsInstantSuccess() {
        assertTrue(CraftMake.instantSuccess(false, false, false));
        assertFalse(CraftMake.instantSuccess(false, false, true));
        assertFalse(CraftMake.instantSuccess(false, true, false));
        assertFalse(CraftMake.instantSuccess(true, false, false));
    }
}
