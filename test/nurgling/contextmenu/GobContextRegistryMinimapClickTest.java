package nurgling.contextmenu;

import haven.KeyMatch;
import haven.UI;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.Hotkeys;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GobContextRegistryMinimapClickTest {
    private HotkeyBinding binding;
    private Object checkpoint;

    @BeforeEach
    void bindContextMenuToCtrlRightClick() {
        binding = Hotkeys.action(Hotkeys.WORLD_CONTEXT_MENU).binding();
        checkpoint = binding.checkpoint();
        binding.set(InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C));
    }

    @AfterEach
    void restoreBinding() {
        binding.restore(checkpoint);
    }

    @Test
    void ctrlRightClickOpensGobMenuWithoutSendingServerClick() {
        AtomicInteger menus = new AtomicInteger();
        AtomicInteger serverClicks = new AtomicInteger();

        boolean consumed = GobContextRegistry.routeMinimapIconClick(null, 3, UI.MOD_CTRL, true,
                gob -> menus.incrementAndGet(), serverClicks::incrementAndGet);

        assertTrue(consumed);
        assertEquals(1, menus.get());
        assertEquals(0, serverClicks.get());
    }

    @Test
    void plainRightClickKeepsSendingTheServerClick() {
        AtomicInteger menus = new AtomicInteger();
        AtomicInteger serverClicks = new AtomicInteger();

        boolean consumed = GobContextRegistry.routeMinimapIconClick(null, 3, 0, true,
                gob -> menus.incrementAndGet(), serverClicks::incrementAndGet);

        assertTrue(consumed);
        assertEquals(0, menus.get());
        assertEquals(1, serverClicks.get());
    }

    @Test
    void mouseReleaseDoesNotRepeatEitherAction() {
        AtomicInteger actions = new AtomicInteger();

        boolean consumed = GobContextRegistry.routeMinimapIconClick(null, 3, UI.MOD_CTRL, false,
                gob -> actions.incrementAndGet(), actions::incrementAndGet);

        assertFalse(consumed);
        assertEquals(0, actions.get());
    }
}
