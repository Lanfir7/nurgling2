package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.UI;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.InputGesture;
import nurgling.i18n.L10n;
import nurgling.widgets.NHotkeyCapture;

import java.util.function.Consumer;

/** One action row in the categorized hotkey settings page. */
public class HotkeyActionRow extends Panel {
    private final Label actionLabel;
    private final Label contextLabel;
    private final NHotkeyCapture capture;
    private final Button reset;

    public HotkeyActionRow(int width, HotkeyAction action, InputGesture gesture,
                           Consumer<HotkeyCapturePolicy.Decision> captureSink,
                           Runnable resetAction) {
        super();
        if(action == null || gesture == null || captureSink == null || resetAction == null)
            throw new NullPointerException();
        resize(Coord.of(width, UI.scale(38)));
        actionLabel = add(new Label(action.label()), Coord.of(0, UI.scale(3)));
        contextLabel = add(new Label(contexts(action)), Coord.of(0, UI.scale(20)));
        capture = add(new NHotkeyCapture(UI.scale(175), action, captureSink), Coord.z);
        capture.setGesture(gesture);
        reset = add(new Button(UI.scale(75), L10n.get("hotkeys.reset"), false).action(resetAction), Coord.z);
        resize(sz);
    }

    public NHotkeyCapture capture() { return capture; }
    public Button resetButton() { return reset; }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        if(actionLabel == null)
            return;
        int captureX = Math.max(0, size.x - capture.sz.x - reset.sz.x - UI.scale(8));
        capture.move(Coord.of(captureX, (size.y - capture.sz.y) / 2));
        reset.move(Coord.of(size.x - reset.sz.x, (size.y - reset.sz.y) / 2));
    }

    private static String contexts(HotkeyAction action) {
        StringBuilder result = new StringBuilder();
        for(HotkeyContext context : action.contexts()) {
            if(result.length() > 0)
                result.append(", ");
            result.append(context.label());
        }
        return result.toString();
    }
}
