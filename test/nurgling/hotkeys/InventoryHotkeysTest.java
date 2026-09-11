package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class InventoryHotkeysTest {
    @org.junit.jupiter.api.BeforeEach
    void isolateGesturePreferences() {
        HotkeyCatalog.useGesturePreferences(new PreferenceStore() {
            private final java.util.Map<String, String> values = new java.util.HashMap<>();
            public String get(String key, String fallback) {
                return values.containsKey(key) ? values.get(key) : fallback;
            }
            public void set(String key, String value) {
                if(value == null) values.remove(key);
                else values.put(key, value);
            }
        });
    }

    @org.junit.jupiter.api.AfterEach
    void restoreGesturePreferences() {
        HotkeyCatalog.useGesturePreferences(null);
    }

    @Test void catalogRegistersInventoryAndHeldItemDefaults() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);

        assertGesture(registry, "item.take", InputGesture.mouse(1, KeyMatch.MODS, 0));
        assertGesture(registry, "item.interact", InputGesture.mouse(3, KeyMatch.MODS, 0));
        assertGesture(registry, "item.interact.all", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C));
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
        assertGesture(registry, Hotkeys.STOCKPILE_TRANSFER_OUT, InputGesture.wheel(-1, KeyMatch.MODS, 0));
        assertGesture(registry, Hotkeys.STOCKPILE_TRANSFER_IN, InputGesture.wheel(1, KeyMatch.MODS, 0));
        assertGesture(registry, "stockpile.transfer_out_all", InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S));
        assertGesture(registry, "stockpile.transfer_in_all", InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S));
        assertEquals(EnumSet.of(HotkeyContext.STOCKPILE), registry.find(Hotkeys.STOCKPILE_TRANSFER_OUT).contexts());
        assertEquals(EnumSet.of(HotkeyContext.STOCKPILE), registry.find(Hotkeys.STOCKPILE_TRANSFER_IN).contexts());
        assertEquals(Integer.valueOf(UI.MOD_CTRL), registry.find("item.interact.all").canonicalMods());
    }

    @Test void resolverUsesReboundGestureAndPreservesActionOrder() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        HotkeyAction take = registry.find("item.take");
        take.binding().set(InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.C));
        try {
            HotkeyResolver resolver = new HotkeyResolver(registry);
            assertSame(take, resolver.firstMouse(HotkeyContext.INVENTORY_ITEM_GENERIC, 2, KeyMatch.C));
            assertNull(resolver.firstMouse(HotkeyContext.INVENTORY_ITEM_GENERIC, 1, 0));
        } finally {
            take.binding().reset();
        }
    }

    @Test void specializedActionsKeepDirectionAndCountWhenRebound() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        HotkeyAction dropDesc = registry.find("item.drop_same.desc");
        HotkeyAction dropAsc = registry.find("item.drop_same.asc");
        try {
            dropDesc.binding().set(InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));
            dropAsc.binding().set(InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M));

            HotkeyResolver resolver = new HotkeyResolver(registry);
            assertSame(dropDesc, resolver.firstMouse(HotkeyContext.INVENTORY_ITEM_NURGLING,
                    2, KeyMatch.C | KeyMatch.M));
            assertSame(dropAsc, resolver.firstMouse(HotkeyContext.INVENTORY_ITEM_NURGLING,
                    1, KeyMatch.C | KeyMatch.M));
            assertEquals("item.drop_same.desc", dropDesc.id());
            assertEquals("item.drop_same.asc", dropAsc.id());
        } finally {
            dropDesc.binding().set(dropDesc.defaultGesture());
            dropAsc.binding().set(dropAsc.defaultGesture());
        }
    }

    @Test void handlersResolveMiddleButtonBeforePhysicalButtonBranches() throws Exception {
        assertFalse(source("src/haven/WItem.java").contains("if(ev.b == 1 || ev.b == 3)"));
        assertFalse(source("src/nurgling/NWItem.java").contains("if(ev.b == 1 || ev.b == 3)"));
        assertFalse(source("src/nurgling/NInventory.java").contains("if (ev.b == 1 || ev.b == 3)"));
        assertFalse(source("src/haven/ItemDrag.java").contains("if(ev.b == 1)"));
    }

    @Test void specializedInventorySemanticsDoNotDeriveDirectionFromPhysicalButton() throws Exception {
        String source = source("src/nurgling/NInventory.java");
        assertFalse(source.contains("processGroupItems(group, ev.b == 3"));
        assertFalse(source.contains("wdgmsg(\"drop-same\", item, ev.b == 3"));
        assertTrue(source.contains("groupedAscending(action.id())"));
        assertTrue(source.contains("groupedAll(action.id())"));
    }

    @Test void heldCtrlRmbIsAnEditableActionWithUnmodifiedServerMeaning() {
        HotkeyAction action = Hotkeys.action(Hotkeys.HELD_OPEN_WITH_CONTROL);
        assertTrue(action.defaultGesture().matchesMouse(3, KeyMatch.C));
        assertEquals(Integer.valueOf(0), action.canonicalMods());
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void assertGesture(HotkeyRegistry registry, String id, InputGesture expected) {
        HotkeyAction action = registry.find(id);
        assertNotNull(action, id);
        assertEquals(expected, action.defaultGesture(), id);
    }
}
