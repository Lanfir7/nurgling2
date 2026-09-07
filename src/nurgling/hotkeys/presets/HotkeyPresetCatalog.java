package nurgling.hotkeys.presets;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Programmed, immutable hotkey presets shipped with the client. */
public final class HotkeyPresetCatalog {
    public static final String DEFAULT_ID = "builtin.default";

    private HotkeyPresetCatalog() {}

    public static HotkeyPreset defaultPreset(HotkeyRegistry registry) {
        if(registry == null) throw new NullPointerException("registry");
        Map<String, InputGesture> values = new TreeMap<>();
        for(HotkeyAction action : registry.snapshot())
            values.put(action.id(), action.defaultGesture());
        return new HotkeyPreset(DEFAULT_ID, "Default", true, values);
    }

    public static List<HotkeyPreset> builtIns(HotkeyRegistry registry) {
        return Collections.singletonList(defaultPreset(registry));
    }
}
