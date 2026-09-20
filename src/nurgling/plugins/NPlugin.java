package nurgling.plugins;

import nurgling.NGameUI;
import nurgling.widgets.charsel.NCharselScreen;

/**
 * Contract every external plugin implements. The public client knows nothing
 * about what a plugin does — it only loads signed plugin jars and notifies them
 * when a session reaches character selection or its UI is ready.
 *
 * The entry class is named in the jar's {@code plugin.properties} (key
 * {@code main=...}) and must have a public no-arg constructor.
 */
public interface NPlugin {

    /** Human-readable name for logging/UI. */
    String name();

    /**
     * Called once for each game session whose UI has finished initializing.
     * Implementations typically add their widgets here and register listeners.
     */
    void onLoad(NGameUI gui);

    /**
     * Called once for each character-selection screen after its character list is in place.
     * Plugins can add actions or replace the list temporarily with their own panel. Optional.
     */
    default void onCharsel(NCharselScreen screen) {}

    /** Called when a session UI is being torn down. Optional. */
    default void onUnload(NGameUI gui) {}
}
