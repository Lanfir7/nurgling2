package nurgling.hotkeys;

import haven.KeyBinding;
import nurgling.conf.NToolBeltProp;

import haven.KeyMatch;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;

/** Definitions for the legacy key bindings exposed by the unified settings UI. */
public final class HotkeyCatalog {
    private static final EnumSet<InputGesture.Type> KEY = EnumSet.of(InputGesture.Type.KEY);
    private static final Map<KeyBinding, HotkeyBinding> WRAPPERS = new IdentityHashMap<>();

    private HotkeyCatalog() {
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
        String[] minimap = {"mwnd_night", "mwnd_fog", "mwnd_resourcetimers", "ol-eye", "ol-mgrid", "ol-mpath", "ol-treeharv", "ol-hidenature", "ol-minesup"};
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
        core(registry, binding("fgt-cycle", KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C), KeyMatch.S), HotkeyCategory.COMBAT, HotkeyContext.COMBAT_UI, order);
        core(registry, binding("scm-root", KeyMatch.forcode(KeyEvent.VK_ESCAPE, 0)), HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU, order);
        core(registry, binding("scm-back", KeyMatch.forcode(KeyEvent.VK_BACK_SPACE, 0)), HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU, order);
        core(registry, binding("scm-next", KeyMatch.forchar('N', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S)), HotkeyCategory.ACTION_MENU, HotkeyContext.ACTION_MENU, order);
        for(int i = 1; i <= 10; i++)
            core(registry, binding("session-" + i, KeyMatch.forcode(i == 10 ? KeyEvent.VK_0 : KeyEvent.VK_0 + i, KeyMatch.M)), HotkeyCategory.SESSIONS, HotkeyContext.SESSION_SWITCHER, order);
        core(registry, binding("session-next", KeyMatch.forcode(KeyEvent.VK_CLOSE_BRACKET, KeyMatch.M)), HotkeyCategory.SESSIONS, HotkeyContext.SESSION_SWITCHER, order);
        core(registry, binding("session-prev", KeyMatch.forcode(KeyEvent.VK_OPEN_BRACKET, KeyMatch.M)), HotkeyCategory.SESSIONS, HotkeyContext.SESSION_SWITCHER, order);

        for(int slot = 0; slot < 12; slot++)
            registerBelt(registry, KeyBinding.get("belt0" + slot,
                    NToolBeltProp.defaultKey("belt0", slot)), "belt0", slot);
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
        registry.register(new HotkeyAction(binding.id, null, binding.id, category,
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
