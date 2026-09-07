package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyConflict;
import nurgling.hotkeys.InputGesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable row data used to present staged hotkeys and their conflicts. */
public final class HotkeySettingsLayout {
    /** Geometry of the pinned page header and the scrollable row viewport. */
    public static final class Rect {
        public final int x, y, w, h;
        private Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    public final Rect viewport;
    public final Rect filter;
    public final Rect rows;
    public final Rect capture;

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
        this.viewport = null;
        this.filter = null;
        this.rows = null;
        this.capture = null;
    }

    private HotkeySettingsLayout(Rect viewport, Rect filter, Rect rows, Rect capture) {
        this.model = null;
        this.viewport = viewport;
        this.filter = filter;
        this.rows = rows;
        this.capture = capture;
    }

    public static HotkeySettingsLayout calculate(int width, int viewportHeight,
                                                  int searchHeight, int tabsHeight,
                                                  int filterHeight, int rowHeight) {
        if(width < 0 || viewportHeight < 0 || searchHeight < 0 || tabsHeight < 0 ||
                filterHeight < 0 || rowHeight < 0)
            throw new IllegalArgumentException("negative hotkey layout dimension");
        Rect viewport = new Rect(0, 0, width, viewportHeight);
        Rect filter = new Rect(0, searchHeight + tabsHeight, width, filterHeight);
        int rowsY = filter.y + filter.h;
        Rect rows = new Rect(0, rowsY, width, Math.max(0, viewportHeight - rowsY));
        int captureWidth = Math.min(UI_SCALE_CAPTURE, width);
        Rect capture = new Rect(Math.max(0, width - captureWidth), rowsY,
                captureWidth, rowHeight);
        return new HotkeySettingsLayout(viewport, filter, rows, capture);
    }

    private static final int UI_SCALE_CAPTURE = 175;

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
