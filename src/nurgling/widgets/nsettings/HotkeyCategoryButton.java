package nurgling.widgets.nsettings;

import haven.UI;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Localized category tab with an unmistakable selected state. */
class HotkeyCategoryButton extends HotkeyTextButton {
    private static final Color ACTIVE_TINT = new Color(205, 235, 255);
    private static final Color ACTIVE_TEXT = new Color(255, 232, 150);
    private static final Color ACTIVE_OVERLAY = new Color(45, 125, 170, 90);
    private static final Color ACTIVE_LINE = new Color(255, 190, 55);

    private final String label;
    private boolean selected;

    HotkeyCategoryButton(String label) {
        super(UI.scale(72), label);
        this.label = label;
    }

    void setSelected(boolean selected) {
        if(this.selected == selected)
            return;
        this.selected = selected;
        tint = selected ? ACTIVE_TINT : null;
        change(label, selected ? ACTIVE_TEXT : Color.WHITE);
    }

    @Override
    public void draw(BufferedImage image) {
        super.draw(image);
        if(!selected)
            return;
        Graphics2D graphics = image.createGraphics();
        int inset = UI.scale(2);
        int lineHeight = Math.max(1, UI.scale(2));
        graphics.setColor(ACTIVE_OVERLAY);
        graphics.fillRect(inset, inset, Math.max(0, image.getWidth() - inset * 2),
                Math.max(0, image.getHeight() - inset * 2 - lineHeight));
        graphics.setColor(ACTIVE_LINE);
        graphics.fillRect(inset, image.getHeight() - inset - lineHeight,
                Math.max(0, image.getWidth() - inset * 2), lineHeight);
        graphics.dispose();
    }
}
