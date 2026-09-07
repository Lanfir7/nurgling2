package nurgling.hotkeys;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyRegistryTest {
    @Test void globalConflictsWithEveryContextButSeparateSurfacesCanReuseGesture() {
        InputGesture g = InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C);
        HotkeyRegistry registry = registryWithGlobalInventoryAndMap(g);
        assertEquals(2, registry.conflicts("global", g).size());

        HotkeyRegistry surfaces = new HotkeyRegistry();
        surfaces.register(action("inventory", HotkeyContext.INVENTORY_ITEM_GENERIC, g));
        surfaces.register(action("map", HotkeyContext.WORLD_SURFACE, g));
        assertTrue(surfaces.conflicts("inventory", g).isEmpty());
    }

    @Test void incompatibleDuplicateIdFailsLoudly() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("same", HotkeyContext.WORLD_SURFACE,
                InputGesture.mouse(1, KeyMatch.MODS, 0)));
        assertThrows(IllegalStateException.class, () -> registry.register(
                action("same", HotkeyContext.INVENTORY_ITEM_GENERIC,
                        InputGesture.mouse(1, KeyMatch.MODS, 0))));
    }

    @Test void registrationIsIdempotentAndSnapshotIsOrdered() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyAction action = action("same", HotkeyContext.WORLD_SURFACE,
                InputGesture.mouse(1, KeyMatch.MODS, 0));
        registry.register(action);
        registry.register(action);
        assertEquals(1, registry.snapshot().size());
        assertSame(action, registry.find("same"));
    }

    private static HotkeyRegistry registryWithGlobalInventoryAndMap(InputGesture g) {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("global", HotkeyContext.GLOBAL, g));
        registry.register(action("inventory", HotkeyContext.INVENTORY_ITEM_GENERIC, g));
        registry.register(action("map", HotkeyContext.WORLD_SURFACE, g));
        return registry;
    }

    private static HotkeyAction action(String id, HotkeyContext context, InputGesture gesture) {
        return new HotkeyAction(id, null, null, HotkeyCategory.WORLD,
                EnumSet.of(context), EnumSet.of(gesture.type()),
                new MemoryBinding(id, gesture), null, 0, false);
    }

    static final class MemoryBinding implements HotkeyBinding {
        private final String id;
        private final InputGesture defaultGesture;
        private InputGesture current;

        MemoryBinding(String id, InputGesture defaultGesture) {
            this.id = id;
            this.defaultGesture = defaultGesture;
            this.current = defaultGesture;
        }

        public String id() { return id; }
        public InputGesture defaultGesture() { return defaultGesture; }
        public InputGesture current() { return current; }
        public void set(InputGesture gesture) { current = gesture; }
        public void reset() { current = defaultGesture; }
    }
}
