package nurgling.hotkeys.presets;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Programmed, immutable hotkey presets shipped with the client. */
public final class HotkeyPresetCatalog {
    public static final String DEFAULT_ID = "builtin.default";
    public static final String ENDER_ID = "builtin.ender";

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

    public static List<HotkeyPreset> builtIns(HotkeyRegistry registry) {
        return Collections.unmodifiableList(Arrays.asList(defaultPreset(registry), enderPreset(registry)));
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
}
