package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import haven.Window;
import haven.Widget;
import nurgling.i18n.L10n;

import java.util.function.Consumer;

/** Small modal-style prompt used by preset create and discard flows. */
public final class HotkeyPresetPrompt extends Window {
    private final TextEntry entry;
    private final Runnable closed;
    private boolean completed;

    private HotkeyPresetPrompt(String title, String message, boolean asksName,
                               Consumer<String> accept, Runnable cancel, Runnable closed) {
        super(Coord.of(UI.scale(340), UI.scale(asksName ? 92 : 76)), title);
        this.closed = closed == null ? () -> { } : closed;
        add(new Label(message), Coord.of(UI.scale(6), UI.scale(6)));
        entry = asksName ? add(new TextEntry(UI.scale(320), ""),
                Coord.of(UI.scale(6), UI.scale(28))) : null;
        int buttonY = UI.scale(asksName ? 58 : 42);
        add(new Button(UI.scale(90), L10n.get("hotkeys.presets.confirm"), false)
                .action(() -> submit(accept)), Coord.of(UI.scale(142), buttonY));
        add(new Button(UI.scale(90), L10n.get("hotkeys.cancel"), false)
                .action(() -> finish(cancel)), Coord.of(UI.scale(238), buttonY));
    }

    public static HotkeyPresetPrompt name(Consumer<String> accept, Runnable cancel,
                                          Runnable closed) {
        return new HotkeyPresetPrompt(L10n.get("hotkeys.presets.name.title"),
                L10n.get("hotkeys.presets.name.question"), true, accept, cancel, closed);
    }

    public static HotkeyPresetPrompt confirm(Runnable accept, Runnable cancel,
                                             Runnable closed) {
        return new HotkeyPresetPrompt(L10n.get("hotkeys.presets.discard.title"),
                L10n.get("hotkeys.presets.discard.question"), false,
                ignored -> accept.run(), cancel, closed);
    }

    private void submit(Consumer<String> accept) {
        String value = entry == null ? "" : entry.text().trim();
        if(entry != null && value.isEmpty()) {
            if(ui != null) ui.error(L10n.get("hotkeys.presets.name.empty"));
            return;
        }
        finish(() -> accept.accept(value));
    }

    private void finish(Runnable action) {
        if(completed) return;
        completed = true;
        try {
            action.run();
        } finally {
            reqdestroy();
        }
    }

    @Override
    public void destroy() {
        if(!completed) completed = true;
        try {
            super.destroy();
        } finally {
            closed.run();
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if(sender == this && msg == "close") finish(() -> { });
        else super.wdgmsg(sender, msg, args);
    }
}
