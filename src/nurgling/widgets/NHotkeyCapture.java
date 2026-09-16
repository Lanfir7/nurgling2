package nurgling.widgets;

import haven.Button;
import haven.UI;
import haven.Widget;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.InputGesture;
import nurgling.i18n.L10n;
import nurgling.widgets.nsettings.HotkeyCapturePolicy;

import java.util.function.Consumer;

/** A staged hotkey capture button for keyboard, mouse and wheel gestures. */
public class NHotkeyCapture extends Button {
    private final HotkeyAction action;
    private final Consumer<HotkeyCapturePolicy.Decision> sink;
    private InputGesture gesture;
    private UI.Grab keyGrab;
    private UI.Grab mouseGrab;

    public NHotkeyCapture(int width, HotkeyAction action,
                          Consumer<HotkeyCapturePolicy.Decision> sink) {
        super(width, action.current().displayName(), false);
        if(action == null || sink == null)
            throw new NullPointerException();
        this.action = action;
        this.sink = sink;
        this.gesture = action.current();
    }

    public HotkeyAction action() { return action; }
    public InputGesture gesture() { return gesture; }
    public boolean armed() { return keyGrab != null || mouseGrab != null; }

    public void setGesture(InputGesture gesture) {
        this.gesture = gesture == null ? InputGesture.none() : gesture;
        if(!armed())
            change(this.gesture.displayName());
    }

    @Override
    public void click() {
        if(armed()) {
            cancelCapture();
            return;
        }
        if(ui == null)
            return;
        change(L10n.get("hotkeys.capture"));
        keyGrab = ui.grabkeys(this);
        mouseGrab = ui.grabmouse(this);
    }

    private void finish(HotkeyCapturePolicy.Decision decision) {
        if(decision == null)
            return;
        switch(decision.kind()) {
        case IGNORE_MODIFIER:
            return;
        case REJECT_TYPE:
            change(String.format(L10n.get("hotkeys.capture.wrong_type"), requiredTypes()));
            return;
        case CANCEL:
            releaseGrabs();
            change(gesture.displayName());
            return;
        case RESET:
        case DISABLE:
        case ASSIGN:
            releaseGrabs();
            if(decision.kind() == HotkeyCapturePolicy.ASSIGN && decision.gesture() != null)
                gesture = decision.gesture();
            change(gesture.displayName());
            sink.accept(decision);
            return;
        default:
            throw new AssertionError(decision.kind());
        }
    }

    private String requiredTypes() {
        StringBuilder result = new StringBuilder();
        for(InputGesture.Type type : action.allowedTypes()) {
            if(type == InputGesture.Type.NONE)
                continue;
            if(result.length() > 0)
                result.append(", ");
            result.append(L10n.get("hotkeys.type." + type.name().toLowerCase(java.util.Locale.ROOT)));
        }
        return result.toString();
    }

    public void cancelCapture() {
        releaseGrabs();
        change(gesture.displayName());
    }

    private void releaseGrabs() {
        if(keyGrab != null) {
            keyGrab.remove();
            keyGrab = null;
        }
        if(mouseGrab != null) {
            mouseGrab.remove();
            mouseGrab = null;
        }
    }

    @Override
    public boolean keydown(KeyDownEvent ev) {
        if(!ev.grabbed || !armed())
            return super.keydown(ev);
        finish(HotkeyCapturePolicy.key(ev.code, ui.modflags(), action));
        return true;
    }

    @Override
    public boolean keyup(KeyUpEvent ev) {
        return armed() && ev.grabbed;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(!ev.grabbed || !armed())
            return super.mousedown(ev);
        finish(HotkeyCapturePolicy.mouse(ev.b, ui.modflags(), action));
        return true;
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if(armed() && ev.grabbed)
            return true;
        return super.mouseup(ev);
    }

    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
        if(!ev.grabbed || !armed())
            return super.mousewheel(ev);
        finish(HotkeyCapturePolicy.wheel(ev.a, ui.modflags(), action));
        return true;
    }

    @Override
    public void hide() {
        releaseGrabs();
        super.hide();
    }

    @Override
    public void reqdestroy() {
        releaseGrabs();
        super.reqdestroy();
    }

    @Override
    public void remove() {
        releaseGrabs();
        super.remove();
    }
}
