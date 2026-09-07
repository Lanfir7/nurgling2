package nurgling.widgets.nsettings;

import haven.Button;
import haven.CheckBox;
import haven.Coord;
import haven.Label;
import haven.Scrollport;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import nurgling.NConfig;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyConflict;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;
import nurgling.i18n.L10n;
import nurgling.widgets.AdaptiveSettingsPanel;

import java.util.ArrayList;
import java.util.List;

/** Categorized, conflict-aware editor for the unified hotkey registry. */
public class HotkeySettings extends Panel implements AdaptiveSettingsPanel {
    private final HotkeySettingsModel model;
    private final TextEntry search;
    private final CheckBox conflictsOnly;
    private final Scrollport rowsScroll;
    private final Widget rows;
    private Widget conflictBox;
    private int contentWidth;

    public HotkeySettings() {
        this(new HotkeySettingsModel(nurgling.hotkeys.Hotkeys.registry()));
    }

    public HotkeySettings(HotkeySettingsModel model) {
        super();
        if(model == null)
            throw new NullPointerException("model");
        this.model = model;
        int width = UI.scale(560);
        search = add(new TextEntry(UI.scale(210), "") {
            @Override
            protected void changed() {
                super.changed();
                model.setQuery(text());
                rebuildRows();
            }

            @Override
            public void draw(haven.GOut g) {
                super.draw(g);
                if(text().isEmpty()) {
                    g.chcolor(180, 180, 180, 180);
                    g.atext(L10n.get("hotkeys.search"), Coord.of(UI.scale(6), sz.y / 2), 0, 0.5);
                    g.chcolor();
                }
            }
        }, Coord.z);
        conflictsOnly = add(new CheckBox(L10n.get("hotkeys.conflicts_only")) {
            @Override
            public void set(boolean value) {
                a = value;
                model.setConflictsOnly(value);
                rebuildRows();
            }
        }, Coord.z);

        int tabsY = UI.scale(30);
        int tabX = 0;
        for(final HotkeyCategory category : HotkeyCategory.values()) {
            final Button tab = new Button(UI.scale(72), tabLabel(category), false);
            add(tab, Coord.of(tabX, tabsY));
            tab.action(() -> {
                model.selectCategory(category);
                rebuildRows();
            });
            tabX += tab.sz.x + UI.scale(2);
        }

        add(new Button(UI.scale(110), L10n.get("hotkeys.reset_category"), false)
                .action(() -> {
                    model.draft().resetCategory(model.selectedCategory());
                    rebuildRows();
                }), Coord.of(0, UI.scale(60)));
        add(new Button(UI.scale(90), L10n.get("hotkeys.reset_all"), false)
                .action(() -> {
                    model.draft().resetAll();
                    rebuildRows();
                }), Coord.of(UI.scale(115), UI.scale(60)));

        rowsScroll = add(new Scrollport(Coord.of(width, UI.scale(420))), Coord.of(0, UI.scale(94)));
        rows = rowsScroll.cont;
        contentWidth = width;
        resize(Coord.of(width, UI.scale(530)));
        rebuildRows();
    }

    public HotkeySettingsModel model() { return model; }

    @Override
    public void load() {
        model.draft().cancel();
        conflictsOnly.a = model.conflictsOnly();
        search.rsettext(model.query());
        rebuildRows();
    }

    @Override
    public void save() {
        if(!model.draft().conflicts().isEmpty())
            throw new IllegalStateException("hotkey conflicts must be resolved before save");
        model.draft().save();
        NConfig.needUpdate();
        rebuildRows();
    }

    @Override
    public void fitToWidth(int width, int columns) {
        resize(Coord.of(Math.max(1, width), sz.y));
    }

    @Override
    public void fitToViewport(Coord viewport, int columns) {
        resize(viewport);
    }

    @Override
    public boolean ownsVerticalScroll() { return true; }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        if(search == null)
            return;
        int margin = UI.scale(4);
        search.move(Coord.of(0, 0));
        conflictsOnly.move(Coord.of(search.sz.x + margin, 0));
        rowsScroll.move(Coord.of(0, UI.scale(94)));
        rowsScroll.resize(Coord.of(Math.max(1, size.x), Math.max(1, size.y - rowsScroll.c.y)));
        contentWidth = rowsScroll.cont.sz.x;
        rebuildRows();
    }

    private void rebuildRows() {
        if(rows == null)
            return;
        for(Widget child : new ArrayList<>(rows.children()))
            child.destroy();
        int y = 0;
        for(final HotkeyAction action : model.visibleActions()) {
            final HotkeyActionRow row = new HotkeyActionRow(contentWidth, action,
                    model.draft().effective(action.id()),
                    decision -> handleCapture(action, decision),
                    () -> {
                        model.draft().reset(action.id());
                        rebuildRows();
                    });
            rows.add(row, Coord.of(0, y));
            y += row.sz.y + UI.scale(2);
        }
        rowsScroll.cont.update();
    }

    private void handleCapture(HotkeyAction action, HotkeyCapturePolicy.Decision decision) {
        switch(decision.kind()) {
        case RESET:
            model.draft().reset(action.id());
            rebuildRows();
            return;
        case DISABLE:
            model.draft().assign(action.id(), InputGesture.none());
            rebuildRows();
            return;
        case ASSIGN:
            List<HotkeyConflict> conflicts = model.draft().assign(action.id(), decision.gesture());
            if(conflicts.isEmpty())
                rebuildRows();
            else
                showConflict(action, conflicts.get(0),
                        () -> {
                            model.draft().replace(conflicts.get(0));
                            rebuildRows();
                        }, this::rebuildRows);
            return;
        default:
            return;
        }
    }

    private void showConflict(HotkeyAction action, HotkeyConflict conflict,
                              Runnable replace, Runnable cancel) {
        if(conflictBox != null)
            conflictBox.destroy();
        conflictBox = add(new Widget(Coord.of(sz.x, UI.scale(28))), Coord.of(0, UI.scale(64)));
        conflictBox.add(new Label(action.label() + " / " + conflict.conflictingAction().label()), Coord.z);
        conflictBox.add(new Button(UI.scale(80), L10n.get("hotkeys.replace"), false)
                .action(() -> { replace.run(); conflictBox.destroy(); conflictBox = null; }),
                Coord.of(UI.scale(250), 0));
        conflictBox.add(new Button(UI.scale(70), L10n.get("hotkeys.cancel"), false)
                .action(() -> { cancel.run(); conflictBox.destroy(); conflictBox = null; }),
                Coord.of(UI.scale(335), 0));
    }

    private static String tabLabel(HotkeyCategory category) {
        String suffix = category.name().toLowerCase();
        return L10n.get("hotkeys.tab." + suffix);
    }
}
