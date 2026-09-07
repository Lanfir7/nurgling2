package nurgling.hotkeys;

import haven.KeyBinding;
import haven.KeyMatch;
import haven.Utils;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyCatalogTest {
    @Test void coreCatalogContainsPreviouslyListedAndPreviouslyHiddenBindings() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        String[] ids = {"inv", "equ", "areas", "cookbook", "craft-atlas", "storage",
                "cam-left", "mapwnd/prov", "make/one", "fgt/0", "quickaction",
                "mwnd_fog", "session-next", "belt00"};
        for(String id : ids)
            assertNotNull(registry.find(id), id);
    }

    @Test void dynamicRegistrationIsVisibleAndIdempotent() {
        HotkeyRegistry registry = new HotkeyRegistry();
        KeyBinding binding = KeyBinding.get("scm/test/action", KeyMatch.nil);
        HotkeyCatalog.registerMenuAction(registry, binding, "Test action");
        HotkeyCatalog.registerMenuAction(registry, binding, "Test action");
        assertEquals(1, registry.snapshot().size());
    }

    @Test void sameIdWithDifferentBindingInstanceIsRejected() {
        KeyBinding original = KeyBinding.get("scm/test/distinct", KeyMatch.nil);
        KeyBinding distinct = new KeyBinding(original);
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerMenuAction(registry, original, "Test action");
        assertThrows(IllegalStateException.class,
                () -> HotkeyCatalog.registerMenuAction(registry, distinct, "Test action"));
    }

    @Test void beltRegistrationKeepsBeltMetadata() {
        HotkeyRegistry registry = new HotkeyRegistry();
        KeyBinding binding = KeyBinding.get("belt-test-slot", KeyMatch.nil);
        HotkeyCatalog.registerBelt(registry, binding, "belt-test", 0);
        HotkeyAction action = registry.find(binding.id);
        assertEquals(HotkeyCategory.BELTS, action.category());
        assertEquals(EnumSet.of(HotkeyContext.BELT), action.contexts());
        assertEquals("belt-test slot 1", action.label());
    }

    @Test void catalogPreservesSpecialBindingInitializationSemantics() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        assertEquals(0, KeyBinding.get("fgt-cycle").modign);
    }

    @Test void catalogRunsNaturePreferenceMigrationBeforeResolvingBinding() {
        String legacy = KeyMatch.forcode(java.awt.event.KeyEvent.VK_J, 0).reduce();
        Utils.setpref("keybind/togglenature", "");
        Utils.setpref("keybind/mwnd_nature", legacy);
        HotkeyCatalog.registerCore(new HotkeyRegistry());
        assertEquals(legacy, Utils.getpref("keybind/togglenature", ""));
        assertEquals("", Utils.getpref("keybind/mwnd_nature", ""));
        Utils.setpref("keybind/togglenature", "");
    }
}
