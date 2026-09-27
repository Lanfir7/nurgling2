package nurgling.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JColorChooser;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NColorWidgetTest {
    private static final class RecordingWidget extends NColorWidget {
        final List<Color> pickerInitialColors = new ArrayList<>();
        boolean accept;
        Color choice;

        RecordingWidget() {
            super("Color");
        }

        @Override
        protected JColorChooser createColorChooser(Color initial) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            pickerInitialColors.add(initial);
            return super.createColorChooser(initial);
        }

        @Override
        protected void showColorChooser(JColorChooser chooser, Runnable onAccept) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            if (pickerInitialColors.size() == 1)
                cb.click(); // A click while this picker is open must not queue a second one.
            chooser.setColor(choice);
            if (accept)
                onAccept.run();
        }
    }

    @Test
    void constructingWidgetsDoesNotCreateSwingColorPickers() {
        for (int i = 0; i < 24; i++) {
            RecordingWidget widget = new RecordingWidget();
            assertTrue(widget.pickerInitialColors.isEmpty());
        }
    }

    @Test
    void pickerStartsFromCurrentColorAndOnlyAcceptPublishesIt() throws Exception {
        RecordingWidget widget = new RecordingWidget();
        widget.color = Color.RED;
        widget.choice = Color.BLUE;
        widget.accept = true;
        SwingUtilities.invokeAndWait(() -> {
            widget.cb.click();
            widget.cb.click();
        });
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(List.of(Color.RED), widget.pickerInitialColors);
        assertEquals(Color.BLUE, widget.color);

        widget.color = Color.GREEN; // External settings refresh before the next opening.
        widget.choice = Color.YELLOW;
        widget.accept = false;
        widget.cb.click();
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(List.of(Color.RED, Color.GREEN), widget.pickerInitialColors);
        assertEquals(Color.GREEN, widget.color);
    }
}
