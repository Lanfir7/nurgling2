package nurgling.hotkeys;

import haven.KeyMatch;

import java.awt.event.KeyEvent;

/** Entry point for the unified keyboard-action registry. */
public final class Hotkeys {
    public static final String INV = "inv";
    public static final String EQU = "equ";
    public static final String AREAS = "areas";
    public static final String COOKBOOK = "cookbook";
    public static final String CRAFT_ATLAS = "craft-atlas";
    public static final String STORAGE = "storage";
    public static final String CAM_LEFT = "cam-left";
    public static final String MAPWND_PROV = "mapwnd/prov";
    public static final String MAKE_ONE = "make/one";
    public static final String MAKE_ALL = "make/all";
    public static final String FIGHT_0 = "fgt/0";
    public static final String QUICK_ACTION = "quickaction";
    public static final String MINIMAP_FOG = "mwnd_fog";
    public static final String SESSION_NEXT = "session-next";
    public static final String ITEM_TAKE = "item.take";
    public static final String ITEM_INTERACT = "item.interact";
    public static final String ITEM_INTERACT_SHIFT = "item.interact.shift";
    public static final String ITEM_INTERACT_ALL = "item.interact.all";
    public static final String ITEM_TRANSFER_ONE = "item.transfer.one";
    public static final String ITEM_TRANSFER_ALL = "item.transfer.all";
    public static final String ITEM_DROP_ONE = "item.drop.one";
    public static final String ITEM_DROP_ALL = "item.drop.all";
    public static final String ITEM_RECIPES = "item.recipes";
    public static final String ITEM_TRANSFER_SAME_DESC = "item.transfer_same.desc";
    public static final String ITEM_TRANSFER_SAME_ASC = "item.transfer_same.asc";
    public static final String ITEM_DROP_SAME_DESC = "item.drop_same.desc";
    public static final String ITEM_DROP_SAME_ASC = "item.drop_same.asc";
    public static final String INVENTORY_TRANSFER_TO_MAIN = "inventory.transfer_to_main";
    public static final String INVENTORY_TRANSFER_FROM_MAIN = "inventory.transfer_from_main";
    public static final String HELD_DROP_ON_TARGET = "held.drop_on_target";
    public static final String HELD_DROP_ON_GROUND = "held.drop_on_ground";
    public static final String HELD_OPEN_WITH_CONTROL = "held.open_with_control";
    public static final String HELD_INTERACT_WITH_TARGET = "held.interact_with_target";
    public static final String HELD_INTERACT_ONE_WITH_TARGET = "held.interact_one_with_target";
    public static final String HELD_INTERACT_ALL_WITH_TARGET = "held.interact_all_with_target";
    public static final String HELD_OPEN_WITHOUT_USING = "held.open_without_using";
    public static final String HELD_LIGHT_FROM_FIRE = "held.light_from_fire";
    public static final String WORLD_PLANNER_REMOVE_GHOST = "world.planner.remove_ghost";
    public static final String WORLD_PLANNER_CLONE_GHOST = "world.planner.clone_ghost";
    public static final String WORLD_SHARE_CHAT_AREA = "world.share_chat_area";
    public static final String MAP_QUICK_MARKER = "map.quick_marker";
    public static final String WORLD_TOGGLE_OBJECT_RING = "world.toggle_object_ring";
    public static final String WORLD_CONTEXT_MENU = "world.context_menu";
    public static final String WORLD_QUEUE_WAYPOINT = "world.queue_waypoint";
    public static final String WORLD_PING = "world.ping";
    public static final String WORLD_REMOVE_STUMP = "world.remove_stump";
    public static final String WORLD_PLACEMENT_ROTATE_LEFT = "world.placement.rotate_left";
    public static final String WORLD_PLACEMENT_ROTATE_RIGHT = "world.placement.rotate_right";
    public static final String WORLD_SELECTION_ROTATE = "world.selection.rotate";
    public static final String WORLD_SELECTION_TOGGLE_GRID = "world.selection.toggle_grid";
    public static final String MAP_MARKER_DELETE = "map.marker.delete";
    public static final String MAP_MARKER_EDIT = "map.marker.edit";
    public static final String MAP_MARKER_WAYPOINT = "map.marker.waypoint";
    public static final String MAP_MARKER_NAVIGATE = "map.marker.navigate";
    public static final String MAP_MARKER_BEACON = "map.marker.beacon";
    public static final String MAP_PING = "map.ping";
    public static final String FLOWER_FORCE_MANUAL = "flower.force_manual";
    public static final String FLOWER_CONTROL_MODE = "flower.control_mode";
    public static final String ACTION_MENU_KEEP_SEARCH_OPEN = "action_menu.keep_search_open";
    public static final String ACTION_MENU_OPEN_ALL_ROSTERS = "action_menu.open_all_rosters";
    public static final String CRAFT_SHOW_RECIPES = "craft.show_recipes";
    public static final String COMBAT_ACTION_POINTS_INCREASE = "combat.action_points.increase";
    public static final String COMBAT_ACTION_POINTS_DECREASE = "combat.action_points.decrease";
    public static final String FGT_CYCLE = "fgt-cycle";
    public static final String FGT_CYCLE_PREV = "fgt-cycle-prev";
    public static final String STOCKPILE_TRANSFER_ALL = "stockpile.transfer_all";
    public static final String STOCKPILE_TRANSFER_OUT = "stockpile.transfer_out";
    public static final String STOCKPILE_TRANSFER_IN = "stockpile.transfer_in";
    public static final String STOCKPILE_TRANSFER_OUT_ALL = "stockpile.transfer_out_all";
    public static final String STOCKPILE_TRANSFER_IN_ALL = "stockpile.transfer_in_all";
    public static final String INVENTORY_STACK_TRANSFER_TO_MAIN = "inventory.stack.transfer_to_main";
    public static final String INVENTORY_STACK_TRANSFER_FROM_MAIN = "inventory.stack.transfer_from_main";
    public static final String BUDDY_PULL_MODE = "buddy.pull_mode";
    public static final String WOUND_FIND_TREATMENT_STORAGE = "wound.find_treatment_storage";
    public static final String LAYOUT_UNDO = "layout.undo";
    public static final String LAYOUT_COMPASS_RESIZE = "layout.compass_resize";
    public static final String LAYOUT_ADJUST = "layout.adjust";
    public static final String LAYOUT_SCALE_UP = "layout.scale_up";
    public static final String LAYOUT_SCALE_DOWN = "layout.scale_down";
    public static final String WORLD_SURVEY_NEW_SELECTION = "world.survey.new_selection";
    public static final String WINDOW_DB_STATS_TOGGLE = "window.db_stats.toggle";
    public static final String WINDOW_AGENT_TOGGLE = "window.agent.toggle";
    public static final String WINDOW_RESOURCE_TIMERS_REFRESH = "window.resource_timers.refresh";
    public static final String WINDOW_MAP_ICONS_TOGGLE_SELECTED = "window.map_icons.toggle_selected";
    public static final String SYSTEM_RENDERING_TOGGLE = "system.rendering.toggle";
    public static final String SESSION_STOP_MACROS = "session-stop-macros";

    private static final HotkeyRegistry REGISTRY = new HotkeyRegistry();
    private static boolean initialized;

    private Hotkeys() {
    }

    /** Return the process-wide registry, populated with the core catalog lazily. */
    public static synchronized HotkeyRegistry registry() {
        if(!initialized) {
            initialized = true;
            HotkeyCatalog.registerCore(REGISTRY);
        }
        return REGISTRY;
    }

    public static HotkeyAction action(String id) {
        HotkeyAction action = registry().find(id);
        if(action == null)
            throw new IllegalStateException("unknown hotkey: " + id);
        return action;
    }

    /** Match the map-frame drag/waypoint gesture without imposing a button on the action. */
    public static boolean matchesMapMarkerWaypoint(int button, int mods, boolean frameHit) {
        return ((button == 1) && frameHit) || action(MAP_MARKER_WAYPOINT).current().matchesMouse(button, mods);
    }

    /** Dispatch a map waypoint/drag event after matching the actual event button. */
    public static boolean dispatchMapMarkerWaypoint(int button, int mods, boolean frameHit, Runnable handler) {
        if(!matchesMapMarkerWaypoint(button, mods, frameHit))
            return false;
        handler.run();
        return true;
    }

    /** Match a waypoint mouse action's modifiers when a surrounding widget has no mouse button. */
    public static boolean matchesMapMarkerWaypointModifiers(int mods) {
        InputGesture gesture = action(MAP_MARKER_WAYPOINT).current();
        return gesture.type() == InputGesture.Type.MOUSE_BUTTON &&
                (mods & gesture.modmask()) == (gesture.modmatch() & gesture.modmask());
    }

    /** Match labeled-marker deletion using the action's currently bound mouse button. */
    public static boolean matchesMapMarkerDelete(int button, int mods) {
        return action(MAP_MARKER_DELETE).current().matchesMouse(button, mods);
    }

    /** Match the configurable marker-navigation gesture in either map view. */
    public static boolean matchesMapMarkerNavigate(int button, int mods) {
        return action(MAP_MARKER_NAVIGATE).current().matchesMouse(button, mods);
    }

    /** Match the configurable marker-beacon gesture in either map view. */
    public static boolean matchesMapMarkerBeacon(int button, int mods) {
        return action(MAP_MARKER_BEACON).current().matchesMouse(button, mods);
    }

    /** Plain LMB path recording must yield to a matching marker-delete action. */
    public static boolean allowsForagerPathRecording(int button, int mods) {
        return button == 1 && (mods & KeyMatch.MODS) == 0 && !matchesMapMarkerDelete(button, mods);
    }

    /** Match a registered keyboard action, including runtime belt registrations. */
    public static boolean matchesKey(String id, KeyEvent event) {
        HotkeyAction action = registry().find(id);
        return action != null && action.current().matches(event, 0);
    }

    /** Convert independent held placement modes into semantic flags. */
    public static int placementPositionMods(int physicalMods) {
        int result = 0;
        for(String id : new String[]{"world.placement.snap_neighbors", "world.placement.free_position"}) {
            HotkeyAction action = action(id);
            InputGesture gesture = action.current();
            if(gesture.type() == InputGesture.Type.MODIFIER && (physicalMods & gesture.code()) != 0)
                result |= action.canonicalMods();
        }
        return result;
    }

    public static boolean placementMode(String id, int physicalMods) {
        InputGesture gesture = action(id).current();
        return gesture.type() == InputGesture.Type.MODIFIER && (physicalMods & gesture.code()) != 0;
    }

    /** Interpret explicit command flags; these are not physical input matching. */
    public static boolean semanticModifier(int commandMods, int flag) { return (commandMods & flag) != 0; }

    public static final String[] PLACEMENT_KEYS = {
            WORLD_PLACEMENT_ROTATE_LEFT, WORLD_PLACEMENT_ROTATE_RIGHT,
            "world.placement.coarse_left", "world.placement.coarse_right",
            "world.placement.fine_left", "world.placement.fine_right"};
    public static final String[] PLACEMENT_WHEELS = {
            "world.placement.coarse_wheel_left", "world.placement.coarse_wheel_right",
            "world.placement.fine_wheel_left", "world.placement.fine_wheel_right"};

    public static int placementDirection(String id) { return id.endsWith("left") ? -1 : 1; }

    /** Resolve semantic world clicks before basic waypoint, icon, steering or camera routing. */
    public static HotkeyAction worldClickAction(int button, int mods) {
        return new HotkeyResolver(registry()).firstMouse(java.util.Arrays.asList(
                action(WORLD_CONTEXT_MENU), action(WORLD_PING), action(WORLD_QUEUE_WAYPOINT),
                action(WORLD_REMOVE_STUMP)), button, mods);
    }

    /** Match an unmodified mouse click used by fixed map routing. */
    public static boolean isPlainMouseClick(int button, int mods) {
        return (button == 1 || button == 3) && (mods & KeyMatch.MODS) == 0;
    }

    public static boolean isPlainLeftClick(int button, int mods) {
        return button == 1 && (mods & KeyMatch.MODS) == 0;
    }

    public static boolean isPlainRightClick(int button, int mods) {
        return button == 3 && (mods & KeyMatch.MODS) == 0;
    }

}
