package nurgling.hotkeys;

import haven.KeyBinding;
import haven.KeyMatch;
import org.junit.jupiter.api.Test;

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
}
