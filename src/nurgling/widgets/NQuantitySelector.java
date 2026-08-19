package nurgling.widgets;

import haven.*;
import nurgling.i18n.L10n;

import java.util.function.Consumer;

/**
 * A dialog widget for selecting a quantity using a slider
 */
public class NQuantitySelector extends Window {

    private final int maxValue;
    private int currentValue;
    private final Consumer<Integer> onConfirm;
    private final Runnable onDelete;

    private final Label valueLabel;

    public NQuantitySelector(String title, int maxValue, Consumer<Integer> onConfirm) {
        this(title, maxValue, onConfirm, null);
    }

    public NQuantitySelector(String title, int maxValue, Consumer<Integer> onConfirm, Runnable onDelete) {
        super(UI.scale(new Coord(260, 140)), title);
        z(1);
        this.maxValue = Math.max(1, maxValue);
        this.currentValue = Math.min(this.maxValue, 1);
        this.onConfirm = onConfirm;
        this.onDelete = onDelete;

        int y = UI.scale(10);
        int margin = UI.scale(10);
        int winW = UI.scale(260);

        add(new Label("1"), new Coord(margin, y));
        valueLabel = add(new Label(String.valueOf(currentValue)), new Coord(winW / 2 - UI.scale(10), y));
        add(new Label(String.valueOf(this.maxValue)), new Coord(winW - UI.scale(40), y));

        y += UI.scale(25);

        if (this.maxValue > 1) {
            add(new HSlider(winW - margin * 2, 1, this.maxValue, currentValue) {
                @Override
                public void changed() {
                    currentValue = val;
                    valueLabel.settext(String.valueOf(currentValue));
                }
            }, new Coord(margin, y));
            y += UI.scale(30);
        } else {
            y += UI.scale(8);
        }

        int buttonWidth = UI.scale(100);
        int spacing = UI.scale(12);
        int startX = (winW - buttonWidth * 2 - spacing) / 2;

        Button ok = add(new Button(buttonWidth, L10n.get("common.ok")) {
            @Override
            public void click() {
                confirm();
            }
        }, new Coord(startX, y));

        add(new Button(buttonWidth, L10n.get("common.cancel")) {
            @Override
            public void click() {
                cancel();
            }
        }, new Coord(startX + buttonWidth + spacing, y));

        y += ok.sz.y + UI.scale(8);

        if (onDelete != null) {
            Button del = add(new Button(buttonWidth * 2 + spacing, L10n.get("storage.menu_delete")) {
                @Override
                public void click() {
                    delete();
                }
            }, new Coord(startX, y));
            y += del.sz.y;
        }

        resize(new Coord(winW, y + margin));
    }

    private void confirm() {
        hide();
        onConfirm.accept(currentValue);
        reqdestroy();
    }

    private void cancel() {
        hide();
        reqdestroy();
    }

    private void delete() {
        hide();
        reqdestroy();
        if (onDelete != null) {
            onDelete.run();
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            cancel();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    @Override
    public boolean keydown(KeyDownEvent ev) {
        if (ev.code == java.awt.event.KeyEvent.VK_ESCAPE) {
            cancel();
            return true;
        } else if (ev.code == java.awt.event.KeyEvent.VK_ENTER) {
            confirm();
            return true;
        }
        return super.keydown(ev);
    }
}
