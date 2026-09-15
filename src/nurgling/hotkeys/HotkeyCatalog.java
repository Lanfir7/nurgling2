package nurgling.hotkeys;

import haven.KeyBinding;
import nurgling.conf.NToolBeltProp;

import haven.KeyMatch;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;

/** Definitions for the legacy key bindings exposed by the unified settings UI. */
public final class HotkeyCatalog {
    private static final EnumSet<InputGesture.Type> KEY = EnumSet.of(InputGesture.Type.KEY);
    private static final Map<KeyBinding, HotkeyBinding> WRAPPERS = new IdentityHashMap<>();
    private static final Map<String, HotkeyBinding> GESTURE_WRAPPERS = new HashMap<>();
    private static PreferenceStore gesturePreferences = PreferenceStore.SYSTEM;

    private HotkeyCatalog() {
    }

    /** Test-only: keep mouse/wheel bindings out of the process Java preferences. */
    public static synchronized void useGesturePreferences(PreferenceStore store) {
        gesturePreferences = store != null ? store : PreferenceStore.SYSTEM;
        GESTURE_WRAPPERS.clear();
    }

    /** Literal IDs owned by the static catalog, excluding runtime registrations. */
    public static Set<String> knownStaticIds() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registerCore(registry);
        Set<String> ids = new LinkedHashSet<>();
        for(HotkeyAction action : registry.snapshot())
            if(!action.dynamic())
                ids.add(action.id());
        return Collections.unmodifiableSet(ids);
    }

    /** Register every core action. Repeating this call is safe. */
    public static void registerCore(HotkeyRegistry registry) {
        if(registry == null)
            throw new NullPointerException("registry");
        int[] order = {0};

        // Resolve by id instead of initializing UI classes. KeyBinding.get is a
        // singleton lookup, so later class initialization receives these exact
        // objects while catalog construction stays headless-safe.
        core(registry, binding("inv", KeyMatch.forcode(KeyEvent.VK_TAB, 0)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("equ", KeyMatch.forchar('E', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("chr", KeyMatch.forchar('T', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("bud", KeyMatch.forchar('B', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("areas", KeyMatch.forchar('L', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("cookbook", KeyMatch.forchar('K', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("craft-atlas", KeyMatch.nil), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("searchWidget", KeyMatch.forchar('F', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("treegarden", KeyMatch.forchar('P', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("baseplanner", KeyMatch.nil), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("storage", KeyMatch.forchar('I', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("opt", KeyMatch.forchar('O', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("scm-srch", KeyMatch.forchar('Z', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("screenshot", KeyMatch.forchar('S', KeyMatch.M)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("ui-toggle", KeyMatch.nil), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("logout", KeyMatch.nil), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("logout-cs", KeyMatch.nil), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("instantLogoutKB", KeyMatch.forchar('L', KeyMatch.C)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("sort-inv", KeyMatch.nil), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("chat-quick", KeyMatch.forcode(KeyEvent.VK_ENTER, 0)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("login/savtoken", KeyMatch.forchar('R', KeyMatch.M)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);
        core(registry, binding("login/deltoken", KeyMatch.forchar('F', KeyMatch.M)), HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL, order);

        core(registry, binding("map", KeyMatch.forchar('A', KeyMatch.C)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("ol-claim", KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("ol-vil", KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("ol-rlm", KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("map-icons", KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("grid", KeyMatch.forchar('G', KeyMatch.C)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cam-left", KeyMatch.forcode(KeyEvent.VK_LEFT, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cam-right", KeyMatch.forcode(KeyEvent.VK_RIGHT, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cam-in", KeyMatch.forcode(KeyEvent.VK_UP, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cam-out", KeyMatch.forcode(KeyEvent.VK_DOWN, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cam-reset", KeyMatch.forcode(KeyEvent.VK_HOME, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("mapwnd/home", KeyMatch.forcode(KeyEvent.VK_HOME, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("mapwnd/mark", KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("mapwnd/hmark", KeyMatch.forchar('M', KeyMatch.C)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("mapwnd/compact", KeyMatch.forchar('A', KeyMatch.M)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("mapwnd/prov", KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        String[] minimap = {"mwnd_night", "mwnd_fog", "mwnd_resourcetimers", "ol-eye", "ol-mgrid", "ol-mpath", "ol-treeharv", "ol-hidenature", "ol-minesup", "ol-showzones", "ol-animals", "ol-flooroverlay"};
        for(String id : minimap)
            core(registry, binding(id, KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("quickaction", KeyMatch.forcode(KeyEvent.VK_Q, 0)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("quickignaction", KeyMatch.forcode(KeyEvent.VK_Q, 1)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("mousequickaction", KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.M)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        String[] nmapNil = {"pgridbox", "pfovbox", "gridbox"};
        for(String id : nmapNil)
            core(registry, binding(id, KeyMatch.nil), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("togglebb", KeyMatch.forcode(KeyEvent.VK_N, KeyMatch.C)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cyclebbmode", KeyMatch.forcode(KeyEvent.VK_N, KeyMatch.C | KeyMatch.S)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, bindingMigratedTogglenature(), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("cleardmg", KeyMatch.forcode(KeyEvent.VK_D, KeyMatch.C | KeyMatch.S)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);
        core(registry, binding("flatworld", KeyMatch.forcode(KeyEvent.VK_F, KeyMatch.C | KeyMatch.S)), HotkeyCategory.MAP, HotkeyContext.GLOBAL, order);

        core(registry, binding("speed-up", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.C)), HotkeyCategory.WORLD, HotkeyContext.GLOBAL, order);
        core(registry, binding("speed-down", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S | KeyMatch.C)), HotkeyCategory.WORLD, HotkeyContext.GLOBAL, order);
        for(int i = 0; i < 4; i++)
            core(registry, binding("speed-set/" + i, KeyMatch.nil), HotkeyCategory.WORLD, HotkeyContext.GLOBAL, order);
        core(registry, binding("make/one", KeyMatch.forcode(KeyEvent.VK_ENTER, 0)), HotkeyCategory.CRAFTING, HotkeyContext.CRAFT_WINDOW, order);
        core(registry, binding("make/all", KeyMatch.forcode(KeyEvent.VK_ENTER, KeyMatch.C)), HotkeyCategory.CRAFTING, HotkeyContext.CRAFT_WINDOW, order);
        core(registry, binding("scm-itemcraft", KeyMatch.nil), HotkeyCategory.CRAFTING, HotkeyContext.CRAFT_WINDOW, order);
        for(int i = 0; i < 10; i++)
            core(registry, binding("fgt/" + i, KeyMatch.forcode(KeyEvent.VK_1 + (i % 5), i < 5 ? 0 : KeyMatch.S)), HotkeyCategory.COMBAT, HotkeyContext.COMBAT_UI, order);
        core(registry, binding("fgt-cycle", KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C)), HotkeyCategory.COMBAT, HotkeyContext.COMBAT_UI, order);
        core(registry, binding("scm-root", KeyMatch.forcode(KeyEvent.VK_ESCAPE, 0)), HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU, order);
        core(registry, binding("scm-back", KeyMatch.forcode(KeyEvent.VK_BACK_SPACE, 0)), HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU, order);
        core(registry, binding("scm-next", KeyMatch.forchar('N', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S)), HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU, order);
        for(int i = 1; i <= 10; i++)
            core(registry, binding("session-" + i, KeyMatch.forcode(i == 10 ? KeyEvent.VK_0 : KeyEvent.VK_0 + i, KeyMatch.M)), HotkeyCategory.SESSIONS, HotkeyContext.SESSION_SWITCHER, order);
        core(registry, binding("session-next", KeyMatch.forcode(KeyEvent.VK_CLOSE_BRACKET, KeyMatch.M)), HotkeyCategory.SESSIONS, HotkeyContext.SESSION_SWITCHER, order);
        core(registry, binding("session-prev", KeyMatch.forcode(KeyEvent.VK_OPEN_BRACKET, KeyMatch.M)), HotkeyCategory.SESSIONS, HotkeyContext.SESSION_SWITCHER, order);

        registerItemActions(registry, order);
        registerGameplayGestures(registry, order);
        for(int i = 2; i < Hotkeys.PLACEMENT_KEYS.length; i++) {
            boolean fine = i >= 4;
            gesture(registry, Hotkeys.PLACEMENT_KEYS[i], InputGesture.key(KeyMatch.forcode(
                    (i % 2 == 0) ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT,
                    fine ? KeyMatch.S : KeyMatch.S | KeyMatch.C, fine ? KeyMatch.S : KeyMatch.C)),
                    HotkeyCategory.WORLD, EnumSet.of(HotkeyContext.WORLD_PLACEMENT), fine ? haven.UI.MOD_SHIFT : haven.UI.MOD_CTRL, order);
        }
        for(int i = 0; i < Hotkeys.PLACEMENT_WHEELS.length; i++) {
            boolean fine = i >= 2;
            gesture(registry, Hotkeys.PLACEMENT_WHEELS[i], InputGesture.wheel(i % 2 == 0 ? -1 : 1,
                    fine ? KeyMatch.S : KeyMatch.S | KeyMatch.C, fine ? KeyMatch.S : KeyMatch.C), HotkeyCategory.WORLD,
                    EnumSet.of(HotkeyContext.WORLD_PLACEMENT), fine ? haven.UI.MOD_SHIFT : haven.UI.MOD_CTRL, order);
        }
        gesture(registry, "world.placement.snap_neighbors", InputGesture.modifier(KeyMatch.M), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_PLACEMENT), haven.UI.MOD_META, order);
        gesture(registry, "world.placement.free_position", InputGesture.modifier(KeyMatch.S), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_PLACEMENT), haven.UI.MOD_SHIFT, order);
        gesture(registry, "world.placement.snap_edges", InputGesture.modifier(KeyMatch.C), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_PLACEMENT), haven.UI.MOD_CTRL, order);
        gesture(registry, Hotkeys.WORLD_REMOVE_STUMP, InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), haven.UI.MOD_META, order);
        gesture(registry, "map.clear_waypoints", InputGesture.mouse(3, KeyMatch.S, 0), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), null, order);

        for(int slot = 0; slot < 12; slot++)
            registerBelt(registry, KeyBinding.get("belt0" + slot,
                    NToolBeltProp.defaultKey("belt0", slot)), "belt0", slot);
    }

    private static void registerGameplayGestures(HotkeyRegistry registry, int[] order) {
        gesture(registry, Hotkeys.WORLD_PLANNER_REMOVE_GHOST,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_PLANNER_CLONE_GHOST,
                InputGesture.mouse(2, KeyMatch.MODS, 0), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_SHARE_CHAT_AREA,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), Integer.valueOf(haven.UI.MOD_CTRL | haven.UI.MOD_META), order);
        gesture(registry, Hotkeys.MAP_QUICK_MARKER,
                InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_TOGGLE_OBJECT_RING,
                InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_CONTEXT_MENU,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), Integer.valueOf(haven.UI.MOD_CTRL), order);
        gesture(registry, Hotkeys.WORLD_QUEUE_WAYPOINT,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), Integer.valueOf(haven.UI.MOD_META), order);
        gesture(registry, Hotkeys.WORLD_PING,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S | KeyMatch.M), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), Integer.valueOf(haven.UI.MOD_META | haven.UI.MOD_SHIFT), order);
        gesture(registry, Hotkeys.WORLD_PLACEMENT_ROTATE_LEFT,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_LEFT, 0)), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_PLACEMENT_ROTATE_RIGHT,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_RIGHT, 0)), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_SELECTION_ROTATE,
                InputGesture.key(KeyMatch.forchar('R', 0)), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);
        gesture(registry, Hotkeys.WORLD_SELECTION_TOGGLE_GRID,
                InputGesture.key(KeyMatch.forchar('C', 0)), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.WORLD_SURFACE), null, order);

        gesture(registry, Hotkeys.MAP_MARKER_DELETE,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), null, order);
        gesture(registry, Hotkeys.MAP_MARKER_EDIT,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), null, order);
        gesture(registry, Hotkeys.MAP_MARKER_WAYPOINT,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), Integer.valueOf(haven.UI.MOD_SHIFT), order);
        gesture(registry, Hotkeys.MAP_MARKER_NAVIGATE,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), null, order);
        gesture(registry, Hotkeys.MAP_MARKER_BEACON,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.S), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), null, order);
        gesture(registry, Hotkeys.MAP_PING,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S | KeyMatch.M), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_SURFACE), Integer.valueOf(haven.UI.MOD_META | haven.UI.MOD_SHIFT), order);

        gesture(registry, Hotkeys.FLOWER_FORCE_MANUAL, InputGesture.modifier(KeyMatch.S), HotkeyCategory.COMBAT,
                EnumSet.of(HotkeyContext.FLOWER_MENU_MODE), null, order);
        gesture(registry, Hotkeys.FLOWER_CONTROL_MODE, InputGesture.modifier(KeyMatch.C), HotkeyCategory.COMBAT,
                EnumSet.of(HotkeyContext.FLOWER_MENU_MODE), null, order);
        gesture(registry, Hotkeys.ACTION_MENU_KEEP_SEARCH_OPEN, InputGesture.modifier(KeyMatch.C), HotkeyCategory.ACTION_MENU,
                EnumSet.of(HotkeyContext.MENU_SEARCH_MODE), null, order);
        gesture(registry, Hotkeys.ACTION_MENU_OPEN_ALL_ROSTERS, InputGesture.modifier(KeyMatch.S), HotkeyCategory.ACTION_MENU,
                EnumSet.of(HotkeyContext.ROSTER_BUTTON_MODE), null, order);
        gesture(registry, Hotkeys.CRAFT_SHOW_RECIPES,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.CRAFTING,
                EnumSet.of(HotkeyContext.CRAFT_WINDOW), null, order);
        gesture(registry, Hotkeys.COMBAT_ACTION_POINTS_INCREASE,
                InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.COMBAT,
                EnumSet.of(HotkeyContext.COMBAT_UI), null, order);
        gesture(registry, Hotkeys.COMBAT_ACTION_POINTS_DECREASE,
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.COMBAT,
                EnumSet.of(HotkeyContext.COMBAT_UI), null, order);
        gesture(registry, Hotkeys.FGT_CYCLE_PREV,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C | KeyMatch.S)), HotkeyCategory.COMBAT,
                EnumSet.of(HotkeyContext.COMBAT_UI), null, order);

        gesture(registry, Hotkeys.STOCKPILE_TRANSFER_ALL,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.STOCKPILE), null, order);
        gesture(registry, Hotkeys.STOCKPILE_TRANSFER_OUT,
                InputGesture.wheel(-1, KeyMatch.MODS, 0), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.STOCKPILE), Integer.valueOf(0), order);
        gesture(registry, Hotkeys.STOCKPILE_TRANSFER_IN,
                InputGesture.wheel(1, KeyMatch.MODS, 0), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.STOCKPILE), Integer.valueOf(0), order);
        gesture(registry, Hotkeys.STOCKPILE_TRANSFER_OUT_ALL,
                InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.STOCKPILE), Integer.valueOf(haven.UI.MOD_SHIFT), order);
        gesture(registry, Hotkeys.STOCKPILE_TRANSFER_IN_ALL,
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.STOCKPILE), Integer.valueOf(haven.UI.MOD_SHIFT), order);
        gesture(registry, Hotkeys.INVENTORY_STACK_TRANSFER_TO_MAIN,
                InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_BACKGROUND), null, order);
        gesture(registry, Hotkeys.INVENTORY_STACK_TRANSFER_FROM_MAIN,
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_BACKGROUND), null, order);
        gesture(registry, Hotkeys.BUDDY_PULL_MODE,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.BUDDY_WINDOW), null, order);
        gesture(registry, Hotkeys.WOUND_FIND_TREATMENT_STORAGE,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.WOUND_WINDOW), Integer.valueOf(haven.UI.MOD_CTRL), order);
        gesture(registry, Hotkeys.LAYOUT_UNDO,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Z, KeyMatch.C)), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.LAYOUT_EDIT), null, order);
        gesture(registry, Hotkeys.LAYOUT_COMPASS_RESIZE,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.COMPASS_WIDGET), null, order);
        gesture(registry, Hotkeys.WORLD_SURVEY_NEW_SELECTION,
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.WORLD,
                EnumSet.of(HotkeyContext.LAND_SURVEY), null, order);
        gesture(registry, Hotkeys.WINDOW_DB_STATS_TOGGLE,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F11, 0)), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.GLOBAL), null, order);
        gesture(registry, Hotkeys.WINDOW_AGENT_TOGGLE,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F10, 0)), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.GLOBAL), null, order);
        gesture(registry, Hotkeys.WINDOW_RESOURCE_TIMERS_REFRESH,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F5, 0)), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.RESOURCE_TIMERS_WINDOW), null, order);
        gesture(registry, Hotkeys.WINDOW_MAP_ICONS_TOGGLE_SELECTED,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_SPACE, 0)), HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.MAP_ICON_SETTINGS), null, order);
        gesture(registry, Hotkeys.SYSTEM_RENDERING_TOGGLE,
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F8, 0)), HotkeyCategory.WINDOWS,
                EnumSet.of(HotkeyContext.GLOBAL), null, order);
    }

    private static void registerItemActions(HotkeyRegistry registry, int[] order) {
        EnumSet<HotkeyContext> itemContexts = EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC,
                HotkeyContext.INVENTORY_ITEM_NURGLING);
        gesture(registry, "item.take", InputGesture.mouse(1, KeyMatch.MODS, 0), HotkeyCategory.INVENTORY,
                itemContexts, null, order);
        gesture(registry, "item.interact", InputGesture.mouse(3, KeyMatch.MODS, 0), HotkeyCategory.INVENTORY,
                itemContexts, Integer.valueOf(0), order);
        gesture(registry, Hotkeys.ITEM_INTERACT_SHIFT,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                itemContexts, Integer.valueOf(haven.UI.MOD_SHIFT), order);
        gesture(registry, Hotkeys.ITEM_INTERACT_ALL,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.INVENTORY,
                itemContexts, Integer.valueOf(haven.UI.MOD_CTRL), order);
        gesture(registry, "item.transfer.one", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                itemContexts, null, order);
        gesture(registry, "item.transfer.all", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.S), HotkeyCategory.INVENTORY,
                itemContexts, null, order);
        gesture(registry, "item.drop.one", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.INVENTORY,
                itemContexts, null, order);
        gesture(registry, "item.drop.all", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC), null, order);
        gesture(registry, "item.recipes", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_NURGLING), null, order);
        gesture(registry, "item.transfer_same.desc", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.M | KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_NURGLING), null, order);
        gesture(registry, "item.transfer_same.asc", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M | KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_NURGLING), null, order);
        gesture(registry, "item.drop_same.desc", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.M), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_NURGLING), null, order);
        gesture(registry, "item.drop_same.asc", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.M), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_NURGLING), null, order);
        gesture(registry, "inventory.transfer_to_main", InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_BACKGROUND), null, order);
        gesture(registry, "inventory.transfer_from_main", InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_BACKGROUND), null, order);
        gesture(registry, "held.drop_on_target", InputGesture.mouse(1, KeyMatch.MODS, 0), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), null, order);
        gesture(registry, Hotkeys.HELD_DROP_ON_GROUND, InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), haven.UI.MOD_CTRL, order);
        gesture(registry, Hotkeys.HELD_OPEN_WITH_CONTROL, InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), 0, order);
        gesture(registry, "held.interact_with_target", InputGesture.mouse(3, KeyMatch.MODS, 0), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), Integer.valueOf(0), order);
        gesture(registry, Hotkeys.HELD_INTERACT_ONE_WITH_TARGET,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), Integer.valueOf(haven.UI.MOD_SHIFT), order);
        gesture(registry, Hotkeys.HELD_INTERACT_ALL_WITH_TARGET,
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.S), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), Integer.valueOf(haven.UI.MOD_CTRL | haven.UI.MOD_SHIFT), order);
        gesture(registry, "held.open_without_using", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), Integer.valueOf(0), order);
        gesture(registry, "held.light_from_fire", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.M), HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.HELD_ITEM), Integer.valueOf(haven.UI.MOD_CTRL | haven.UI.MOD_META), order);
    }

    private static void gesture(HotkeyRegistry registry, String id, InputGesture defaultGesture,
                                HotkeyCategory category, Set<HotkeyContext> contexts,
                                Integer canonicalMods, int[] order) {
        if(id.startsWith("world.placement.")) contexts = EnumSet.of(HotkeyContext.WORLD_PLACEMENT);
        if(id.equals(Hotkeys.WORLD_PLACEMENT_ROTATE_LEFT) || id.equals(Hotkeys.WORLD_PLACEMENT_ROTATE_RIGHT)) canonicalMods = haven.UI.MOD_CTRL;
        if(id.startsWith("inventory.stack.")) contexts = EnumSet.of(HotkeyContext.STACK_INVENTORY);
        if(id.startsWith("stockpile.")) contexts = EnumSet.of(HotkeyContext.STOCKPILE);
        InputGesture.Type type = defaultGesture.type();
        if(type == InputGesture.Type.KEY) {
            KeyBinding kb = KeyBinding.get(id, defaultGesture.key());
            registry.register(new HotkeyAction(id, labelKey(id), null, category, contexts,
                    KEY, wrapper(kb), canonicalMods, order[0]++, false));
            return;
        }
        HotkeyBinding binding = GESTURE_WRAPPERS.get(id);
        if(binding == null) {
            binding = new GestureBinding(id, defaultGesture, gesturePreferences);
            GESTURE_WRAPPERS.put(id, binding);
        }
        registry.register(new HotkeyAction(id, labelKey(id), null, category, contexts,
                EnumSet.of(type), binding, canonicalMods, order[0]++, false));
    }

    public static void registerMenuAction(HotkeyRegistry registry, KeyBinding binding, String label) {
        registerDynamic(registry, binding, label, HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU);
    }

    public static void registerWidgetAction(HotkeyRegistry registry, KeyBinding binding, String label) {
        registerDynamic(registry, binding, label, HotkeyCategory.WINDOWS, HotkeyContext.GLOBAL);
    }

    public static void registerBelt(HotkeyRegistry registry, KeyBinding binding, String label) {
        registerDynamic(registry, binding, label, HotkeyCategory.BELTS, HotkeyContext.BELT);
    }

    public static void registerBelt(HotkeyRegistry registry, KeyBinding binding, String beltName, int slot) {
        registerBelt(registry, binding, beltName + " slot " + (slot + 1));
    }

    private static void core(HotkeyRegistry registry, KeyBinding binding,
                             HotkeyCategory category, HotkeyContext context, int[] order) {
        if(binding == null)
            return;
        if(binding.id.startsWith("cam-")) context = HotkeyContext.WORLD_CAMERA;
        if(binding.id.startsWith("mapwnd/")) context = HotkeyContext.MAP_WINDOW;
        if(binding.id.startsWith("login/")) context = HotkeyContext.LOGIN;
        registry.register(new HotkeyAction(binding.id, labelKey(binding.id), null, category,
                EnumSet.of(context), KEY, wrapper(binding), null, order[0]++, false));
    }

    private static void registerDynamic(HotkeyRegistry registry, KeyBinding binding, String label,
                                        HotkeyCategory category, HotkeyContext context) {
        if(registry == null)
            throw new NullPointerException("registry");
        if(binding == null)
            throw new NullPointerException("binding");
        String text = label == null ? binding.id : label;
        registry.register(new HotkeyAction(binding.id, null, text, category,
                EnumSet.of(context), KEY, wrapper(binding), null, Integer.MAX_VALUE, true));
    }

    private static String labelKey(String id) {
        return "hotkeys.action." + id;
    }

    private static KeyBinding binding(String id) {
        return KeyBinding.get(id, haven.KeyMatch.nil);
    }

    private static KeyBinding binding(String id, KeyMatch defaultKey) {
        return KeyBinding.get(id, defaultKey);
    }

    private static KeyBinding binding(String id, KeyMatch defaultKey, int modign) {
        return KeyBinding.get(id, defaultKey, modign);
    }

    private static KeyBinding bindingMigratedTogglenature() {
        return KeyBinding.getMigrated("togglenature",
                KeyMatch.forcode(KeyEvent.VK_H, KeyMatch.C), "mwnd_nature");
    }

    private static synchronized HotkeyBinding wrapper(KeyBinding binding) {
        HotkeyBinding result = WRAPPERS.get(binding);
        if(result == null) {
            result = new KeyBindingHotkey(binding);
            WRAPPERS.put(binding, result);
        }
        return result;
    }
}
