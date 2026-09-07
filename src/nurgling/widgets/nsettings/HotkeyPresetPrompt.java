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
                               String acceptLabel, String cancelLabel,
                               Consumer<String> accept, Runnable cancel, Runnable closed) {
        super(Coord.of(UI.scale(500), UI.scale(asksName ? 92 : 76)), title);
        this.closed = closed == null ? () -> { } : closed;
        add(new Label(message), Coord.of(UI.scale(6), UI.scale(6)));
        entry = asksName ? add(new TextEntry(UI.scale(480), ""),
                Coord.of(UI.scale(6), UI.scale(28))) : null;
        int buttonY = UI.scale(asksName ? 58 : 42);
        Button cancelButton = new HotkeyTextButton(UI.scale(90), cancelLabel)
                .action(() -> finish(cancel));
        Button acceptButton = new HotkeyTextButton(UI.scale(90), acceptLabel)
                .action(() -> submit(accept));
        int cancelX = sz.x - UI.scale(6) - cancelButton.sz.x;
        int acceptX = cancelX - UI.scale(6) - acceptButton.sz.x;
        add(acceptButton, Coord.of(Math.max(0, acceptX), buttonY));
        add(cancelButton, Coord.of(Math.max(0, cancelX), buttonY));
    }

    public static HotkeyPresetPrompt name(Consumer<String> accept, Runnable cancel,
                                          Runnable closed) {
        return new HotkeyPresetPrompt(L10n.get("hotkeys.presets.name.title"),
                L10n.get("hotkeys.presets.name.question"), true,
                L10n.get("hotkeys.presets.confirm"), L10n.get("hotkeys.cancel"),
                accept, cancel, closed);
    }

    public static HotkeyPresetPrompt confirm(Runnable accept, Runnable cancel,
                                             Runnable closed) {
        return new HotkeyPresetPrompt(L10n.get("hotkeys.presets.discard.title"),
                L10n.get("hotkeys.presets.discard.question"), false,
                L10n.get("hotkeys.presets.discard"), L10n.get("hotkeys.presets.keep_editing"),
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
