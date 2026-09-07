package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyConflict;
import nurgling.hotkeys.InputGesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable row data used to present staged hotkeys and their conflicts. */
public final class HotkeySettingsLayout {
    public static final class Row {
        public final HotkeyAction action;
        public final InputGesture gesture;
        public final List<HotkeyConflict> conflicts;

        private Row(HotkeyAction action, InputGesture gesture, List<HotkeyConflict> conflicts) {
            this.action = action;
            this.gesture = gesture;
            this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
        }

        public HotkeyAction action() { return action; }
        public InputGesture gesture() { return gesture; }
        public List<HotkeyConflict> conflicts() { return conflicts; }
        public boolean hasConflicts() { return !conflicts.isEmpty(); }
    }

    private final HotkeySettingsModel model;

    public HotkeySettingsLayout(HotkeySettingsModel model) {
        if(model == null)
            throw new NullPointerException("model");
        this.model = model;
    }

    public HotkeySettingsModel model() { return model; }

    public static HotkeySettingsLayout forModel(HotkeySettingsModel model) {
        return new HotkeySettingsLayout(model);
    }

    public List<Row> rows() {
        List<Row> result = new ArrayList<>();
        for(HotkeyAction action : model.visibleActions())
            result.add(new Row(action, model.draft().effective(action.id()),
                    model.draft().conflicts(action.id())));
        return Collections.unmodifiableList(result);
    }

    public List<HotkeyConflict> conflicts() {
        return model.draft().conflicts();
    }
}
