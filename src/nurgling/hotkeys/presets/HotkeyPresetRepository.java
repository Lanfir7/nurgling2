package nurgling.hotkeys.presets;

import java.io.IOException;

/** Persistence boundary used by the settings save transaction. */
public interface HotkeyPresetRepository {
    HotkeyPresetStore.LoadResult load();
    void save(HotkeyPresetLibrary library) throws IOException;
    Checkpoint checkpoint() throws IOException;
    void restore(Checkpoint checkpoint) throws IOException;

    interface Checkpoint {}
}
