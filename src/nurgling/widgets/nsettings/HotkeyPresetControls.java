package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;
import haven.Dropbox;
import haven.GOut;
import haven.Label;
import haven.ReadLine;
import haven.UI;
import haven.Widget;
import haven.iosys.tk.Clipboard;
import nurgling.hotkeys.presets.HotkeyPreset;
import nurgling.i18n.L10n;

import java.util.List;

/** Responsive preset selector and its create/share/delete actions. */
public final class HotkeyPresetControls extends Widget {
    public interface Actions {
        List<HotkeyPreset> presets();
        String selectedPresetId();
        void select(String presetId);
        void discardChanges();
        void create(String name);
        String copyCode();
        void importCode(String code);
        void deleteSelected();
        boolean hasUnsavedChanges();
    }

    private final Actions actions;
    private final Label label;
    private final Dropbox<HotkeyPreset> selector;
    private final Button createButton;
    private final Button copyButton;
    private final Button pasteButton;
    private final Button deleteButton;
    private HotkeyPresetPrompt prompt;
    private boolean acceptsClipboardResult = true;

    public HotkeyPresetControls(int width, Actions actions) {
        super(Coord.of(Math.max(1, width), UI.scale(26)));
        if(actions == null) throw new NullPointerException("actions");
        this.actions = actions;
        label = add(new Label(L10n.get("hotkeys.presets.label")), Coord.z);
        selector = add(new Dropbox<HotkeyPreset>(UI.scale(150), 8, UI.scale(20)) {
            protected HotkeyPreset listitem(int index) { return actions.presets().get(index); }
            protected int listitems() { return actions.presets().size(); }
            protected void drawitem(GOut g, HotkeyPreset item, int index) {
                g.text(item.name(), Coord.of(UI.scale(3), UI.scale(2)));
            }
            public Object tooltip(Coord c, Widget prev) {
                return sel == null ? null : sel.name();
            }
            public void change(HotkeyPreset item) {
                if(item == null) return;
                HotkeyPreset previous = selectedPreset();
                super.change(item);
                if(previous != null && previous.id().equals(item.id())) return;
                requestSelect(item.id());
            }
        }, Coord.z);
        createButton = add(new HotkeyTextButton(UI.scale(64), L10n.get("hotkeys.presets.create"))
                .action(this::openCreatePrompt), Coord.z);
        copyButton = add(new HotkeyTextButton(UI.scale(70), L10n.get("hotkeys.presets.copy"))
                .action(this::copy), Coord.z);
        pasteButton = add(new HotkeyTextButton(UI.scale(70), L10n.get("hotkeys.presets.paste"))
                .action(this::paste), Coord.z);
        deleteButton = add(new HotkeyTextButton(UI.scale(64), L10n.get("hotkeys.presets.delete"))
                .action(() -> { actions.deleteSelected(); refresh(); }), Coord.z);
        layout(width);
        refresh();
    }

    public String selectedName() {
        HotkeyPreset selected = selectedPreset();
        return selected == null ? "" : selected.name();
    }

    public boolean copyEnabled() { return !isBuiltInSelection(); }
    public boolean deleteEnabled() { return !isBuiltInSelection(); }
    public boolean hasOpenPrompt() { return prompt != null && prompt.parent != null; }
    public boolean acceptsClipboardResult() { return acceptsClipboardResult; }

    public void select(String id) { requestSelect(id); }

    public void openCreatePrompt() {
        if(ui == null || prompt != null) return;
        showPrompt(HotkeyPresetPrompt.name(name -> {
            try {
                actions.create(name);
                refresh();
            } catch(RuntimeException failure) {
                reportError("hotkeys.presets.error.create");
            }
        }, this::refresh, this::promptClosed));
    }

    public void disposeLifecycle() {
        acceptsClipboardResult = false;
        if(prompt != null) {
            HotkeyPresetPrompt closing = prompt;
            prompt = null;
            closing.reqdestroy();
        }
    }

    public int preferredHeight() { return sz.y; }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        if(label != null) layout(size.x);
    }

    private void requestSelect(String id) {
        if(id == null || id.equals(actions.selectedPresetId())) {
            refresh();
            return;
        }
        Runnable select = () -> {
            try {
                actions.select(id);
            } catch(RuntimeException failure) {
                reportError("hotkeys.presets.error.select");
            }
            refresh();
        };
        if(actions.hasUnsavedChanges() && ui != null && prompt == null) {
            showPrompt(HotkeyPresetPrompt.confirm(() -> {
                actions.discardChanges();
                select.run();
            }, this::refresh, this::promptClosed));
        } else if(!actions.hasUnsavedChanges()) {
            select.run();
        } else {
            refresh();
        }
    }

    private void copy() {
        if(!copyEnabled() || ui == null) return;
        try {
            ui.wnd.clipboard(Clipboard.Std.CLIPBOARD).put(new Clipboard.Contents(
                    new Clipboard.Item<CharSequence>(Clipboard.Format.TEXT, actions.copyCode())));
        } catch(RuntimeException failure) {
            reportError("hotkeys.presets.error.clipboard");
        }
    }

    private void paste() {
        if(ui == null) return;
        ReadLine.PCLine.cliptext(ui.wnd.clipboard(Clipboard.Std.CLIPBOARD)).callback(text -> {
            synchronized(ui) {
                if(!acceptsClipboardResult) return;
                importCode(text == null ? "" : text.toString());
            }
        }, failure -> reportError("hotkeys.presets.error.clipboard"), () -> { });
    }

    private void importCode(String code) {
        Runnable apply = () -> {
            try {
                actions.importCode(code);
                refresh();
            } catch(RuntimeException failure) {
                reportError("hotkeys.presets.error.invalid_code");
            }
        };
        if(actions.hasUnsavedChanges() && ui != null && prompt == null) {
            showPrompt(HotkeyPresetPrompt.confirm(() -> {
                actions.discardChanges();
                apply.run();
            }, this::refresh, this::promptClosed));
        } else if(!actions.hasUnsavedChanges()) {
            apply.run();
        }
    }

    public void refreshSelection() { refresh(); }

    private void refresh() {
        HotkeyPreset selected = selectedPreset();
        if(selected != null) selector.change(selected);
        boolean disabled = selected == null || selected.builtIn();
        copyButton.disable(disabled);
        deleteButton.disable(disabled);
    }

    private HotkeyPreset selectedPreset() {
        String id = actions.selectedPresetId();
        for(HotkeyPreset preset : actions.presets())
            if(preset.id().equals(id)) return preset;
        return null;
    }

    private boolean isBuiltInSelection() {
        HotkeyPreset selected = selectedPreset();
        return selected == null || selected.builtIn();
    }

    private void showPrompt(HotkeyPresetPrompt value) {
        prompt = value;
        ui.root.add(value, ui.root.sz.sub(value.sz).div(2));
        value.raise();
    }

    private void promptClosed() { prompt = null; }

    private void reportError(String key) {
        if(ui != null) ui.error(L10n.get(key));
    }

    private void layout(int width) {
        int gap = UI.scale(4);
        int y = 0;
        label.move(Coord.of(0, UI.scale(3)));
        selector.move(Coord.of(label.sz.x + gap, 0));
        int x = selector.c.x + selector.sz.x + gap;
        Button[] buttons = {createButton, copyButton, pasteButton, deleteButton};
        int buttonsWidth = 0;
        for(Button button : buttons) buttonsWidth += button.sz.x + gap;
        if(x + buttonsWidth > width) {
            x = 0;
            y = selector.sz.y + gap;
        }
        for(Button button : buttons) {
            button.move(Coord.of(x, y));
            x += button.sz.x + gap;
        }
        int height = Math.max(selector.sz.y, y + createButton.sz.y);
        super.resize(Coord.of(Math.max(1, width), height));
    }
}
