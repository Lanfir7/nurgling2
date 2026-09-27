package nurgling.widgets;

import haven.*;
import haven.Button;
import haven.Label;
import javax.swing.*;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class NColorWidget extends Widget
{
    public NColorButton cb;
    public Label label;

    public volatile Color color = Color.BLACK;

    /** Where the swatch sits when the label is short enough, so rows line up in a column. */
    private static final int SWATCH_X = UI.scale(80);
    private static final int LABEL_GAP = UI.scale(6);

    public NColorWidget(String text){
        cb = new NColorButton();
        label = new Label(text + ":");
        add(label, UI.scale(0,8));
        // A fixed swatch position draws the swatch straight over any label wider than it.
        // Longer labels push it right instead; shorter ones keep the shared column, and
        // callers that pass no label at all are unaffected.
        add(cb, new Coord(Math.max(SWATCH_X, label.sz.x + LABEL_GAP), 0));
        pack();
    }
    public class NColorButton extends Button {
        private final AtomicBoolean opening = new AtomicBoolean();

        public NColorButton(){
            super(Inventory.sqsz.x, "");
            sz.y = Inventory.sqsz.y;
        }

        @Override
        public void draw(GOut g) {
            int delta = 2;
            Coord size = new Coord(sz.x-2*delta,sz.y-2*delta);
            g.chcolor(color);
            g.frect(new Coord(delta,  delta), size);
            g.chcolor();
            g.chcolor(Color.BLACK);
            g.frect(new Coord(0,0), new Coord(sz.x,delta));
            g.frect(new Coord(0,sz.y-delta), new Coord(sz.x,delta));
            g.frect(new Coord(0,delta), new Coord(delta,size.y));
            g.frect(new Coord(sz.x-delta,delta), new Coord(delta,size.y));
            g.chcolor();
        }

        @Override
        public void click() {
            if (!opening.compareAndSet(false, true))
                return;
            SwingUtilities.invokeLater(() -> {
                try {
                    JColorChooser chooser = createColorChooser(color);
                    showColorChooser(chooser, () -> color = chooser.getColor());
                } finally {
                    opening.set(false);
                }
            });
        }
    }

    /** Created only when the player opens the picker, on the Swing event thread. */
    protected JColorChooser createColorChooser(Color initial) {
        JColorChooser chooser = new JColorChooser(initial == null ? Color.WHITE : initial);
        for (AbstractColorChooserPanel panel : chooser.getChooserPanels()) {
            if (!"RGB".equals(panel.getDisplayName()))
                chooser.removeChooserPanel(panel);
        }
        chooser.setPreviewPanel(new JPanel());
        return chooser;
    }

    protected void showColorChooser(JColorChooser chooser, Runnable accept) {
        JDialog dialog = JColorChooser.createDialog(null, "SelectColor", true, chooser,
                event -> accept.run(), event -> {});
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
    }
}
