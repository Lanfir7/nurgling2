package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class GameplayGestureCatalogTest {
    @Test void ctrlShiftRightClickOnHeldItemLoadsAllMatchingFuel() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);

        HotkeyAction action = registry.find(Hotkeys.HELD_INTERACT_ALL_WITH_TARGET);

        assertNotNull(action);
        assertTrue(action.defaultGesture().matchesMouse(3, UI.MOD_CTRL | UI.MOD_SHIFT));
        assertFalse(action.defaultGesture().matchesMouse(3, UI.MOD_SHIFT));
        assertEquals(Integer.valueOf(UI.MOD_CTRL | UI.MOD_SHIFT), action.canonicalMods());
    }

    @Test void gameplayGesturesAreRegisteredWithDefaultsAndContexts() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);

        assertGesture(registry, "world.planner.remove_ghost",
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S), HotkeyContext.WORLD_SURFACE, null);
        assertGesture(registry, "world.planner.clone_ghost",
                InputGesture.mouse(2, KeyMatch.MODS, 0), HotkeyContext.WORLD_SURFACE, null);
        assertGesture(registry, "world.share_chat_area",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M), HotkeyContext.WORLD_SURFACE,
                Integer.valueOf(UI.MOD_CTRL | UI.MOD_META));
        assertGesture(registry, "map.quick_marker",
                InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.M), HotkeyContext.WORLD_SURFACE, null);
        assertGesture(registry, "world.toggle_object_ring",
                InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.C), HotkeyContext.WORLD_SURFACE, null);
        assertGesture(registry, "world.context_menu",
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C), HotkeyContext.WORLD_SURFACE, Integer.valueOf(UI.MOD_CTRL));
        assertGesture(registry, "world.queue_waypoint",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.M), HotkeyContext.WORLD_SURFACE, Integer.valueOf(UI.MOD_META));
        assertGesture(registry, "world.ping",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S | KeyMatch.M), HotkeyContext.WORLD_SURFACE,
                Integer.valueOf(UI.MOD_META | UI.MOD_SHIFT));
        assertGesture(registry, "world.placement.rotate_left",
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_LEFT, 0)), HotkeyContext.WORLD_PLACEMENT, Integer.valueOf(UI.MOD_CTRL));
        assertGesture(registry, "world.placement.rotate_right",
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_RIGHT, 0)), HotkeyContext.WORLD_PLACEMENT, Integer.valueOf(UI.MOD_CTRL));
        assertGesture(registry, "world.selection.rotate",
                InputGesture.key(KeyMatch.forchar('R', 0)), HotkeyContext.WORLD_SURFACE, null);
        assertGesture(registry, "world.selection.toggle_grid",
                InputGesture.key(KeyMatch.forchar('C', 0)), HotkeyContext.WORLD_SURFACE, null);

        assertGesture(registry, "map.marker.delete",
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S), HotkeyContext.MAP_SURFACE, null);
        assertGesture(registry, "map.marker.edit",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.M), HotkeyContext.MAP_SURFACE, null);
        assertGesture(registry, "map.marker.waypoint",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyContext.MAP_SURFACE, Integer.valueOf(UI.MOD_SHIFT));
        assertGesture(registry, "map.marker.navigate",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C), HotkeyContext.MAP_SURFACE, null);
        assertGesture(registry, "map.ping",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S | KeyMatch.M), HotkeyContext.MAP_SURFACE,
                Integer.valueOf(UI.MOD_META | UI.MOD_SHIFT));

        assertGesture(registry, "flower.force_manual", InputGesture.modifier(KeyMatch.S),
                HotkeyContext.FLOWER_MENU_MODE, null);
        assertGesture(registry, "flower.control_mode", InputGesture.modifier(KeyMatch.C),
                HotkeyContext.FLOWER_MENU_MODE, null);
        assertGesture(registry, "action_menu.keep_search_open", InputGesture.modifier(KeyMatch.C),
                HotkeyContext.MENU_SEARCH_MODE, null);
        assertGesture(registry, "action_menu.open_all_rosters", InputGesture.modifier(KeyMatch.S),
                HotkeyContext.ROSTER_BUTTON_MODE, null);
        assertGesture(registry, "craft.show_recipes",
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M), HotkeyContext.CRAFT_WINDOW, null);
        assertGesture(registry, "combat.action_points.increase",
                InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S), HotkeyContext.COMBAT_UI, null);
        assertGesture(registry, "combat.action_points.decrease",
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S), HotkeyContext.COMBAT_UI, null);
        assertGesture(registry, "fgt-cycle",
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C)), HotkeyContext.COMBAT_UI, null);
        assertGesture(registry, "fgt-cycle-prev",
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C | KeyMatch.S)), HotkeyContext.COMBAT_UI, null);

        assertGesture(registry, "stockpile.transfer_all",
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyContext.STOCKPILE, null);
        assertGesture(registry, "stockpile.transfer_out",
                InputGesture.wheel(-1, KeyMatch.MODS, 0), HotkeyContext.STOCKPILE, Integer.valueOf(0));
        assertGesture(registry, "stockpile.transfer_in",
                InputGesture.wheel(1, KeyMatch.MODS, 0), HotkeyContext.STOCKPILE, Integer.valueOf(0));
        assertGesture(registry, "inventory.stack.transfer_to_main",
                InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S), HotkeyContext.STACK_INVENTORY, null);
        assertGesture(registry, "inventory.stack.transfer_from_main",
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S), HotkeyContext.STACK_INVENTORY, null);
        assertGesture(registry, "buddy.pull_mode", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S),
                HotkeyContext.BUDDY_WINDOW, null);
        assertGesture(registry, "wound.find_treatment_storage", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C),
                HotkeyContext.WOUND_WINDOW, Integer.valueOf(UI.MOD_CTRL));
        assertGesture(registry, "layout.undo", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Z, KeyMatch.C)),
                HotkeyContext.LAYOUT_EDIT, null);
        assertGesture(registry, "layout.compass_resize", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C),
                HotkeyContext.COMPASS_WIDGET, null);
        assertGesture(registry, "world.survey.new_selection", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S),
                HotkeyContext.LAND_SURVEY, null);
        assertGesture(registry, "window.db_stats.toggle", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F11, 0)),
                HotkeyContext.GLOBAL, null);
        assertGesture(registry, "window.agent.toggle", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F10, 0)),
                HotkeyContext.GLOBAL, null);
        assertGesture(registry, "window.resource_timers.refresh", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F5, 0)),
                HotkeyContext.RESOURCE_TIMERS_WINDOW, null);
        assertGesture(registry, "window.map_icons.toggle_selected", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_SPACE, 0)),
                HotkeyContext.MAP_ICON_SETTINGS, null);
        assertGesture(registry, "system.rendering.toggle", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F8, 0)),
                HotkeyContext.GLOBAL, null);
    }

    private static void assertGesture(HotkeyRegistry registry, String id, InputGesture gesture,
                                      HotkeyContext context, Integer canonicalMods) {
        HotkeyAction action = registry.find(id);
        assertNotNull(action, id);
        assertEquals(gesture, action.defaultGesture(), id);
        assertTrue(action.contexts().contains(context), id);
        assertEquals(canonicalMods, action.canonicalMods(), id);
    }
}
