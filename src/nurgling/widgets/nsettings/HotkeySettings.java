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
import nurgling.hotkeys.presets.HotkeyPreset;
import nurgling.hotkeys.presets.HotkeyPresetStore;
import nurgling.i18n.L10n;
import nurgling.widgets.AdaptiveSettingsPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Categorized, conflict-aware editor for the unified hotkey registry. */
public class HotkeySettings extends Panel implements AdaptiveSettingsPanel {
    private final HotkeySettingsModel model;
    private final HotkeyPresetControls controls;
    private final TextEntry search;
    private final CheckBox conflictsOnly;
    private final Scrollport rowsScroll;
    private final Widget rows;
    private final Widget tabsHost;
    private final Button tabsLeft;
    private final Button tabsRight;
    private final Button resetCategory;
    private final Button resetAll;
    private final List<Button> tabButtons = new ArrayList<>();
    private final Consumer<List<HotkeyAction>> registryListener = ignored -> rebuildRows();
    private boolean registryListening;
    private Widget conflictBox;
    private int contentWidth;

    public HotkeySettings() {
        this(HotkeySettingsModel.open(nurgling.hotkeys.Hotkeys.registry(),
                HotkeyPresetStore.global()));
    }

    public HotkeySettings(HotkeySettingsModel model) {
        super();
        if(model == null)
            throw new NullPointerException("model");
        this.model = model;
        int width = UI.scale(560);
        controls = add(new HotkeyPresetControls(width, new HotkeyPresetControls.Actions() {
            public List<HotkeyPreset> presets() { return model.presets().presets(); }
            public String selectedPresetId() { return model.presets().selected().id(); }
            public void select(String presetId) { model.selectPreset(presetId); rebuildRows(); }
            public void discardChanges() { model.cancel(); }
            public void create(String name) { model.createPreset(name); rebuildRows(); }
            public String copyCode() { return model.copyPresetCode(); }
            public void importCode(String code) { model.importPreset(code); rebuildRows(); }
            public void deleteSelected() { model.deleteSelectedPreset(); rebuildRows(); }
            public boolean hasUnsavedChanges() { return model.hasUnsavedChanges(); }
        }), Coord.z);
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

        int tabsY = UI.scale(56);
        tabsLeft = add(new Button(UI.scale(24), "<", false), Coord.of(0, tabsY));
        tabsHost = add(new Widget(Coord.of(width - UI.scale(48), UI.scale(1))),
                Coord.of(UI.scale(24), tabsY));
        tabsRight = add(new Button(UI.scale(24), ">", false), Coord.of(width - UI.scale(24), tabsY));
        tabsLeft.action(() -> moveCategory(-1));
        tabsRight.action(() -> moveCategory(1));
        for(final HotkeyCategory category : HotkeyCategory.values()) {
            final Button tab = new HotkeyCategoryButton(tabLabel(category));
            tabsHost.add(tab, Coord.z);
            tabButtons.add(tab);
            tab.action(() -> {
                model.selectCategory(category);
                layoutTabs();
                rebuildRows();
            });
        }

        resetCategory = add(new HotkeyTextButton(UI.scale(110), L10n.get("hotkeys.reset_category"))
                .action(() -> {
                    model.resetCategory(model.selectedCategory());
                    rebuildRows();
                }), Coord.of(0, UI.scale(60)));
        resetAll = add(new HotkeyTextButton(UI.scale(90), L10n.get("hotkeys.reset_all"))
                .action(() -> {
                    model.resetAll();
                    rebuildRows();
                }), Coord.of(resetCategory.sz.x + UI.scale(5), UI.scale(60)));

        rowsScroll = add(new Scrollport(Coord.of(width, UI.scale(420))), Coord.of(0, UI.scale(94)));
        rows = rowsScroll.cont;
        contentWidth = width;
        resize(Coord.of(width, UI.scale(530)));
        layoutTabs();
        rebuildRows();
    }

    public HotkeySettingsModel model() { return model; }
    public HotkeyPresetControls controls() { return controls; }

    @Override
    protected void added() {
        super.added();
        if(!registryListening) {
            model.registry().addListener(registryListener);
            registryListening = true;
        }
    }

    @Override
    public void load() {
        model.cancel();
        controls.refreshSelection();
        conflictsOnly.a = model.conflictsOnly();
        search.rsettext(model.query());
        rebuildRows();
    }

    @Override
    public void save() {
        List<HotkeyConflict> conflicts = model.draft().conflicts();
        if(!conflicts.isEmpty()) {
            HotkeyConflict conflict = conflicts.get(0);
            showConflict(conflict.action(), conflict,
                    () -> { model.replace(conflict); rebuildRows(); }, this::rebuildRows);
            return;
        }
        try {
            model.save();
            controls.refreshSelection();
            NConfig.needUpdate();
            rebuildRows();
        } catch(RuntimeException failure) {
            if(ui != null) ui.error(L10n.get("hotkeys.presets.error.save"));
        }
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

    public void cancelCaptures() {
        if(rows == null)
            return;
        for(Widget child : new ArrayList<>(rows.children())) {
            if(child instanceof HotkeyActionRow)
                ((HotkeyActionRow)child).capture().cancelCapture();
        }
    }

    /** Idempotent lifecycle cleanup used when an owning settings window is destroyed. */
    public void disposeLifecycle() {
        cancelCaptures();
        if(controls != null)
            controls.disposeLifecycle();
        stopListening();
    }

    private void stopListening() {
        if(registryListening) {
            model.registry().removeListener(registryListener);
            registryListening = false;
        }
    }

    @Override
    public void hide() {
        cancelCaptures();
        super.hide();
    }

    @Override
    public void remove() {
        disposeLifecycle();
        super.remove();
    }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        if(search == null)
            return;
        int margin = UI.scale(4);
        controls.resize(Coord.of(size.x, controls.sz.y));
        int presetHeight = controls.preferredHeight() + margin;
        int searchHeight = Math.max(search.sz.y, conflictsOnly.sz.y) + margin;
        int tabHeight = Math.max(tabsLeft.sz.y, tabsRight.sz.y);
        for(Button tab : tabButtons)
            tabHeight = Math.max(tabHeight, tab.sz.y);
        int resetHeight = Math.max(resetCategory.sz.y, resetAll.sz.y);
        HotkeySettingsLayout geometry = HotkeySettingsLayout.calculate(size.x, size.y,
                presetHeight, searchHeight, tabHeight + margin,
                resetHeight + UI.scale(2), UI.scale(30));
        controls.move(Coord.of(geometry.presets.x, geometry.presets.y));
        search.move(Coord.of(geometry.search.x, geometry.search.y));
        conflictsOnly.move(Coord.of(search.sz.x + margin, geometry.search.y));
        int tabsY = geometry.tabs.y;
        int arrowWidth = UI.scale(24);
        tabsHost.move(Coord.of(arrowWidth, tabsY));
        tabsHost.resize(Coord.of(Math.max(1, size.x - arrowWidth * 2), tabHeight));
        tabsLeft.move(Coord.of(0, tabsY));
        tabsRight.move(Coord.of(Math.max(0, size.x - arrowWidth), tabsY));
        layoutTabs();
        int resetY = geometry.filter.y;
        resetCategory.move(Coord.of(0, resetY));
        resetAll.move(Coord.of(resetCategory.sz.x + UI.scale(5), resetY));
        rowsScroll.move(Coord.of(geometry.rows.x, geometry.rows.y));
        rowsScroll.resize(Coord.of(Math.max(1, geometry.rows.w), Math.max(1, geometry.rows.h)));
        contentWidth = rowsScroll.cont.sz.x;
        rebuildRows();
    }

    private void moveCategory(int direction) {
        int index = model.selectedCategory().ordinal() + direction;
        index = Math.max(0, Math.min(HotkeyCategory.values().length - 1, index));
        model.selectCategory(HotkeyCategory.values()[index]);
        layoutTabs();
        rebuildRows();
    }

    private void layoutTabs() {
        if(tabsHost == null || tabButtons.isEmpty())
            return;
        int[] widths = new int[tabButtons.size()];
        for(int i = 0; i < tabButtons.size(); i++)
            widths[i] = tabButtons.get(i).sz.x;
        HotkeyTabLayout layout = HotkeyTabLayout.calculate(widths, tabsHost.sz.x,
                model.selectedCategory().ordinal(), UI.scale(2));
        HotkeyTabLayout.VisibleRange visible = layout.visibleRange();
        for(int i = 0; i < tabButtons.size(); i++) {
            HotkeyTabLayout.Rect rect = layout.rect(i);
            Button tab = tabButtons.get(i);
            tab.move(Coord.of(rect.x, 0));
            tab.visible = visible.contains(i);
            ((HotkeyCategoryButton)tab).setSelected(i == model.selectedCategory().ordinal());
        }
        tabsLeft.visible = layout.canScrollLeft();
        tabsRight.visible = layout.canScrollRight();
    }

    private void rebuildRows() {
        if(rows == null)
            return;
        controls.refreshSelection();
        for(Widget child : new ArrayList<>(rows.children()))
            child.destroy();
        int y = 0;
        for(final HotkeyAction action : model.visibleActions()) {
            final HotkeyActionRow row = new HotkeyActionRow(contentWidth, action,
                    model.draft().effective(action.id()),
                    decision -> handleCapture(action, decision),
                    () -> {
                        model.reset(action.id());
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
            model.reset(action.id());
            rebuildRows();
            return;
        case DISABLE:
            model.assign(action.id(), InputGesture.none());
            rebuildRows();
            return;
        case ASSIGN:
            HotkeyDraftModel.Checkpoint beforeConflict = model.draft().checkpoint();
            List<HotkeyConflict> conflicts = model.assign(action.id(), decision.gesture());
            if(conflicts.isEmpty())
                rebuildRows();
            else
                showConflict(action, conflicts.get(0),
                        () -> {
                            model.replace(conflicts.get(0));
                            rebuildRows();
                        }, () -> {
                            model.draft().restore(beforeConflict);
                            rebuildRows();
                        });
            return;
        default:
            return;
        }
    }

    private void showConflict(HotkeyAction action, HotkeyConflict conflict,
                              Runnable replace, Runnable cancel) {
        if(conflictBox != null)
            conflictBox.destroy();
        conflictBox = add(new Widget(Coord.of(sz.x, UI.scale(28))),
                Coord.of(0, resetCategory.c.y));
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
