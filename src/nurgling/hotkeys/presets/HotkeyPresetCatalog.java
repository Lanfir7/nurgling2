package nurgling.hotkeys.presets;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Programmed, immutable hotkey presets shipped with the client. */
public final class HotkeyPresetCatalog {
    public static final String DEFAULT_ID = "builtin.default";
    public static final String ENDER_ID = "builtin.ender";
    public static final String HURRICANE_ID = "builtin.hurricane";

    private HotkeyPresetCatalog() {}

    public static HotkeyPreset defaultPreset(HotkeyRegistry registry) {
        if(registry == null) throw new NullPointerException("registry");
        Map<String, InputGesture> values = new TreeMap<>();
        for(HotkeyAction action : registry.snapshot())
            values.put(action.id(), action.defaultGesture());
        return new HotkeyPreset(DEFAULT_ID, "Default", true, values);
    }

    /** EnderWiggin/hafen-client defaults, pinned to commit 133e6bda5. */
    public static HotkeyPreset enderPreset(HotkeyRegistry registry) {
        if(registry == null) throw new NullPointerException("registry");
        Map<String, InputGesture> values = snapshot(registry);
        replace(values, "screenshot", InputGesture.key(KeyMatch.forchar('S', KeyMatch.C)));
        replace(values, "scm-srch", InputGesture.none());
        replace(values, "craft-atlas", InputGesture.key(KeyMatch.forchar('X', KeyMatch.M)));
        replace(values, "togglebb", InputGesture.key(KeyMatch.forchar('H', KeyMatch.C)));
        replace(values, "togglenature", InputGesture.none());
        replace(values, "cleardmg", InputGesture.none());
        return new HotkeyPreset(ENDER_ID, "Ender", true, values);
    }

    /** Nightdawg/Hurricane defaults, pinned to commit 7e83df724. */
    public static HotkeyPreset hurricanePreset(HotkeyRegistry registry) {
        if(registry == null) throw new NullPointerException("registry");
        Map<String, InputGesture> values = snapshot(registry);

        replace(values, "inv", key('D', KeyMatch.M));
        replace(values, "equ", key('E', KeyMatch.M));
        replace(values, "chr", key('A', KeyMatch.M));
        replace(values, "bud", key('B', KeyMatch.C));
        replace(values, "opt", key('O', KeyMatch.C));
        replace(values, "scm-srch", key('F', KeyMatch.C));
        replace(values, "searchWidget", InputGesture.none());
        replace(values, "instantLogoutKB", InputGesture.none());

        replace(values, "map", key('W', KeyMatch.C));
        replace(values, "ol-claim", code(KeyEvent.VK_F9, KeyMatch.C));
        replace(values, "ol-vil", code(KeyEvent.VK_F10, KeyMatch.C));
        replace(values, "ol-rlm", code(KeyEvent.VK_F11, KeyMatch.C));
        replace(values, "map-icons", key('I', KeyMatch.C));
        replace(values, "storage", InputGesture.none());
        replace(values, "grid", key('G', KeyMatch.C));
        replace(values, "cam-left", InputGesture.none());
        replace(values, "cam-right", InputGesture.none());
        replace(values, "cam-in", InputGesture.none());
        replace(values, "cam-out", InputGesture.none());
        replace(values, "cam-reset", code(KeyEvent.VK_HOME, 0));
        replace(values, "mapwnd/home", code(KeyEvent.VK_HOME, 0));
        replace(values, "mapwnd/mark", InputGesture.none());
        replace(values, "mapwnd/hmark", InputGesture.none());
        replace(values, "mapwnd/compact", key('W', KeyMatch.M));
        replace(values, "mapwnd/prov", code(KeyEvent.VK_F11, KeyMatch.C | KeyMatch.S));

        replace(values, "speed-up", InputGesture.none());
        replace(values, "speed-down", InputGesture.none());
        replace(values, "speed-set/0", InputGesture.none());
        replace(values, "speed-set/1", key('W', KeyMatch.S));
        replace(values, "speed-set/2", key('E', KeyMatch.S));
        replace(values, "speed-set/3", key('R', KeyMatch.S));

        replace(values, "make/one", code(KeyEvent.VK_ENTER, 0));
        replace(values, "make/all", code(KeyEvent.VK_ENTER, KeyMatch.C));
        replace(values, "fgt/0", code(KeyEvent.VK_1, 0));
        replace(values, "fgt/1", code(KeyEvent.VK_2, 0));
        replace(values, "fgt/2", code(KeyEvent.VK_3, 0));
        replace(values, "fgt/3", key('R', 0));
        replace(values, "fgt/4", key('F', 0));
        replace(values, "fgt/5", code(KeyEvent.VK_1, KeyMatch.S));
        replace(values, "fgt/6", code(KeyEvent.VK_2, KeyMatch.S));
        replace(values, "fgt/7", code(KeyEvent.VK_3, KeyMatch.S));
        replace(values, "fgt/8", code(KeyEvent.VK_F2, 0));
        replace(values, "fgt/9", code(KeyEvent.VK_F1, 0));
        replace(values, "fgt-cycle", code(KeyEvent.VK_TAB, 0));

        replace(values, "scm-root", code(KeyEvent.VK_ESCAPE, 0));
        replace(values, "scm-back", code(KeyEvent.VK_BACK_SPACE, 0));
        replace(values, "scm-next", InputGesture.key(KeyMatch.forchar('N',
                KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S)));
        return new HotkeyPreset(HURRICANE_ID, "Hurricane", true, values);
    }

    public static List<HotkeyPreset> builtIns(HotkeyRegistry registry) {
        return Collections.unmodifiableList(Arrays.asList(
                defaultPreset(registry), enderPreset(registry), hurricanePreset(registry)));
    }

    private static Map<String, InputGesture> snapshot(HotkeyRegistry registry) {
        Map<String, InputGesture> values = new TreeMap<>();
        for(HotkeyAction action : registry.snapshot())
            values.put(action.id(), action.defaultGesture());
        return values;
    }

    private static void replace(Map<String, InputGesture> values, String id, InputGesture gesture) {
        if(values.containsKey(id)) values.put(id, gesture);
    }

    private static InputGesture key(char key, int mods) {
        return InputGesture.key(KeyMatch.forchar(key, mods));
    }

    private static InputGesture code(int key, int mods) {
        return InputGesture.key(KeyMatch.forcode(key, mods));
    }
}
