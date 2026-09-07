package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.Text;
import haven.UI;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.InputGesture;
import nurgling.i18n.L10n;
import nurgling.widgets.NHotkeyCapture;

import java.awt.Color;
import java.util.function.Consumer;

/** One action row in the categorized hotkey settings page. */
public class HotkeyActionRow extends Panel {
    private final Label actionLabel;
    private final Label contextLabel;
    private final NHotkeyCapture capture;
    private final Button reset;
    private final String actionText;
    private final String contextText;

    public HotkeyActionRow(int width, HotkeyAction action, InputGesture gesture,
                           Consumer<HotkeyCapturePolicy.Decision> captureSink,
                           Runnable resetAction) {
        super();
        if(action == null || gesture == null || captureSink == null || resetAction == null)
            throw new NullPointerException();
        resize(Coord.of(width, UI.scale(38)));
        actionText = action.label();
        contextText = contexts(action);
        actionLabel = add(new Label(actionText), Coord.of(0, UI.scale(3)));
        contextLabel = add(new Label(contextText), Coord.of(0, UI.scale(20)));
        contextLabel.setcolor(new Color(150, 150, 150));
        capture = add(new NHotkeyCapture(UI.scale(175), action, captureSink), Coord.z);
        capture.setGesture(gesture);
        reset = add(new HotkeyTextButton(UI.scale(75), L10n.get("hotkeys.reset")).action(resetAction), Coord.z);
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
        fitLabel(actionLabel, actionText, captureX - UI.scale(4));
        fitLabel(contextLabel, contextText, captureX - UI.scale(4));
    }

    private static void fitLabel(Label label, String fullText, int maxWidth) {
        Text.Line fitted = label.f.ellipsize(fullText, Math.max(1, maxWidth));
        String displayed = fitted.text;
        fitted.dispose();
        label.settext(displayed);
        label.tooltip = displayed.equals(fullText) ? null : fullText;
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
