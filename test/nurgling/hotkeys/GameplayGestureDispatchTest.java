package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameplayGestureDispatchTest {
    @Test void reboundWorldContextMenuUsesTheNewGestureOnly() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        HotkeyAction action = registry.find("world.context_menu");
        assertNotNull(action);
        action.binding().set(InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));
        try {
            HotkeyResolver resolver = new HotkeyResolver(registry);
            assertSame(action, resolver.firstMouse(HotkeyContext.WORLD_SURFACE, 3, KeyMatch.C | KeyMatch.M));
            assertNull(resolver.firstMouse(HotkeyContext.WORLD_SURFACE, 3, KeyMatch.C));
        } finally {
            action.binding().reset();
        }
    }

    @Test void reboundWorldPingRetainsCanonicalServerModifierFlags() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        HotkeyAction ping = registry.find("world.ping");
        assertNotNull(ping);
        assertEquals(Integer.valueOf(UI.MOD_META | UI.MOD_SHIFT), ping.canonicalMods());
    }
}
