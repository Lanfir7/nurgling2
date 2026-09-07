package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class InventoryHotkeysTest {
    @Test void catalogRegistersInventoryAndHeldItemDefaults() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);

        assertGesture(registry, "item.take", InputGesture.mouse(1, KeyMatch.MODS, 0));
        assertGesture(registry, "item.interact", InputGesture.mouse(3, KeyMatch.MODS, 0));
        assertGesture(registry, "item.transfer.one", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S));
        assertGesture(registry, "item.transfer.all", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.S));
        assertGesture(registry, "item.drop.one", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C));
        assertGesture(registry, "item.drop.all", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));
        assertGesture(registry, "item.recipes", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M));
        assertGesture(registry, "item.transfer_same.desc", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.M | KeyMatch.S));
        assertGesture(registry, "item.transfer_same.asc", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M | KeyMatch.S));
        assertGesture(registry, "item.drop_same.desc", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));
        assertGesture(registry, "item.drop_same.asc", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));
        assertGesture(registry, "inventory.transfer_to_main", InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S));
        assertGesture(registry, "inventory.transfer_from_main", InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S));
        assertGesture(registry, "held.drop_on_target", InputGesture.mouse(1, KeyMatch.MODS, 0));
        assertGesture(registry, "held.interact_with_target", InputGesture.mouse(3, KeyMatch.MODS, 0));
        assertGesture(registry, "held.open_without_using", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M));
        assertGesture(registry, "held.light_from_fire", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));

        assertEquals(Integer.valueOf(UI.MOD_CTRL | UI.MOD_META), registry.find("held.light_from_fire").canonicalMods());
        assertEquals(EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC), registry.find("item.drop.all").contexts());
        assertEquals(EnumSet.of(HotkeyContext.INVENTORY_ITEM_NURGLING), registry.find("item.drop_same.desc").contexts());
    }

    @Test void resolverUsesReboundGestureAndPreservesActionOrder() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        HotkeyAction take = registry.find("item.take");
        take.binding().set(InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.C));

        HotkeyResolver resolver = new HotkeyResolver(registry);
        assertSame(take, resolver.firstMouse(HotkeyContext.INVENTORY_ITEM_GENERIC, 2, KeyMatch.C));
        assertNull(resolver.firstMouse(HotkeyContext.INVENTORY_ITEM_GENERIC, 1, 0));
    }

    private static void assertGesture(HotkeyRegistry registry, String id, InputGesture expected) {
        HotkeyAction action = registry.find(id);
        assertNotNull(action, id);
        assertEquals(expected, action.defaultGesture(), id);
    }
}
