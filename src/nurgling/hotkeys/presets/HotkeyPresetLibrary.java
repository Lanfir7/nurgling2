package nurgling.hotkeys.presets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Persisted user-preset collection and selected preset identity. */
public final class HotkeyPresetLibrary {
    private final String selectedPresetId;
    private final List<HotkeyPreset> userPresets;

    public HotkeyPresetLibrary(String selectedPresetId, Collection<HotkeyPreset> presets) {
        if(selectedPresetId == null || selectedPresetId.trim().isEmpty() || presets == null)
            throw new IllegalArgumentException("invalid preset library");
        List<HotkeyPreset> copy = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for(HotkeyPreset preset : presets) {
            if(preset == null || preset.builtIn() || preset.id().startsWith("builtin.") || !ids.add(preset.id()))
                throw new IllegalArgumentException("invalid user preset");
            copy.add(preset);
        }
        this.selectedPresetId = selectedPresetId;
        this.userPresets = Collections.unmodifiableList(copy);
    }

    public String selectedPresetId() { return selectedPresetId; }
    public List<HotkeyPreset> userPresets() { return userPresets; }

    @Override
    public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof HotkeyPresetLibrary)) return false;
        HotkeyPresetLibrary that = (HotkeyPresetLibrary)other;
        return selectedPresetId.equals(that.selectedPresetId) && userPresets.equals(that.userPresets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectedPresetId, userPresets);
    }
}
