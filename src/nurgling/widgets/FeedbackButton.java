package nurgling.widgets;

import haven.Button;

import java.awt.Color;

public final class FeedbackButton extends Button {
    private static final Color BASE = new Color(255, 178, 62);
    private static final Color HOVER = new Color(255, 225, 135);
    private boolean hovered;

    public FeedbackButton(int width, String text, Runnable action) {
        super(width, text, false, action);
        tint = BASE;
        change(text, new Color(255, 248, 220));
    }

    @Override
    public boolean mousehover(MouseHoverEvent event, boolean hovering) {
        if(hovering != hovered) {
            hovered = hovering;
            tint = hovered ? HOVER : BASE;
            redraw();
        }
        return super.mousehover(event, hovering);
    }
}
