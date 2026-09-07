package nurgling.widgets.nsettings;

import haven.Resource;
import nurgling.hotkeys.presets.HotkeyPreset;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyPresetControlsTest {
    static {
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                    Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if(stream == null) throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch(java.net.MalformedURLException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test void controlsShowSelectionAndRestrictBuiltInButtons() {
        FakeActions actions = new FakeActions();
        HotkeyPresetControls controls = new HotkeyPresetControls(560, actions);
        assertEquals(nurgling.i18n.L10n.get("hotkeys.presets.default"), controls.selectedName());
        assertFalse(controls.copyEnabled());
        assertFalse(controls.deleteEnabled());

        controls.select("user-1");

        assertEquals("Custom", controls.selectedName());
        assertTrue(controls.copyEnabled());
        assertTrue(controls.deleteEnabled());
    }

    @Test void lifecycleRejectsLateClipboardResults() {
        HotkeyPresetControls controls = new HotkeyPresetControls(560, new FakeActions());
        assertTrue(controls.acceptsClipboardResult());
        controls.disposeLifecycle();
        assertFalse(controls.acceptsClipboardResult());
        assertFalse(controls.hasOpenPrompt());
    }

    private static final class FakeActions implements HotkeyPresetControls.Actions {
        final List<HotkeyPreset> presets = Arrays.asList(
                new HotkeyPreset("builtin.default", "Default", true, Collections.emptyMap()),
                new HotkeyPreset("user-1", "Custom", false, Collections.emptyMap()));
        String selected = "builtin.default";
        public List<HotkeyPreset> presets() { return presets; }
        public String selectedPresetId() { return selected; }
        public void select(String presetId) { selected = presetId; }
        public void discardChanges() { }
        public void create(String name) { }
        public String copyCode() { return ""; }
        public void importCode(String code) { }
        public void deleteSelected() { selected = "builtin.default"; }
        public boolean hasUnsavedChanges() { return false; }
    }
}
