package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import java.awt.event.KeyEvent;

/** Fixed widget editing and tooltip presentation, never gameplay dispatch. */
public final class InputNavigation {
    private InputNavigation() { }
    public static boolean cancel(int code) { return code == KeyEvent.VK_ESCAPE; }
    public static boolean confirm(int code) { return code == KeyEvent.VK_ENTER; }
    public static boolean previousRow(int code) { return code == KeyEvent.VK_UP; }
    public static boolean nextRow(int code) { return code == KeyEvent.VK_DOWN; }
    public static boolean tooltipModifier(int code) { return code == KeyEvent.VK_SHIFT; }
    public static boolean expandedTooltip(UI ui) { return ui != null && ui.modshift; }
    public static boolean tooltipChanged(UI ui, boolean previous) { return expandedTooltip(ui) != previous; }
    public static boolean defined(KeyMatch key) { return key != null && key != KeyMatch.nil && key.code != KeyEvent.VK_UNDEFINED; }
}
