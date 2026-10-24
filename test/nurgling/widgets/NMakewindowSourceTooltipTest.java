package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class NMakewindowSourceTooltipTest {
    private static int field(NMakewindow window, String name) throws Exception {
        Field field = NMakewindow.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(window);
    }

    private static void hoverStarted(NMakewindow window) throws Exception {
        Field field = NMakewindow.class.getDeclaredField("hoverstart");
        field.setAccessible(true);
        field.setLong(window, System.currentTimeMillis() - 2000);
    }

    private static Tex image(Object tip) {
        assertTrue(tip instanceof Indir);
        return (Tex) ((Indir<?>) tip).get();
    }

    @Test void realTooltipRefreshesBothDurationsAndKeepsManualAndOutputTips() throws Exception {
        NConfig previous = NConfig.current;
        NConfig.current = new NConfig();
        try {
        NMakewindow window = new NMakewindow("Test recipe");
        int[] calls = new int[2];
        NMakewindow.Spec spec = window.new Spec(() -> null, MessageBuf.nil, -1, new Object[0]) {
            @Override public boolean opt() { return false; }
            @Override public BufferedImage shorttip() {
                calls[0]++;
                return new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
            }
            @Override public BufferedImage longtip() {
                calls[1]++;
                return new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
            }
        };
        spec.name = "Barley Seed";
        spec.isInventory = true;
        window.inputs = Collections.singletonList(spec);
        window.autoMode = true;
        Coord input = new Coord(field(window, "xoff") + 1, 1);

        assertTrue(image(window.tooltip(input, null)).sz().y > 12);
        assertEquals(1, calls[0]);
        image(window.tooltip(input, null));
        assertEquals(1, calls[0]);
        hoverStarted(window);
        assertTrue(image(window.tooltip(input, window)).sz().y > 12);
        assertEquals(1, calls[1]);

        spec.isInventory = false;
        spec.isSubCraft = true;
        assertTrue(image(window.tooltip(input, null)).sz().y > 12);
        hoverStarted(window);
        image(window.tooltip(input, window));
        assertArrayEquals(new int[] {2, 2}, calls);

        spec.name = "Rye Seed";
        image(window.tooltip(input, null));
        hoverStarted(window);
        image(window.tooltip(input, window));
        assertArrayEquals(new int[] {3, 3}, calls);

        window.autoMode = false;
        assertEquals(12, image(window.tooltip(input, null)).sz().y);
        assertEquals(4, calls[0]);

        window.autoMode = true;
        window.inputs = Collections.emptyList();
        window.outputs = Collections.singletonList(spec);
        Coord output = new Coord(field(window, "xoff") + 1, field(window, "outy") + 1);
        assertEquals(12, image(window.tooltip(output, null)).sz().y);
        assertEquals(5, calls[0]);
        } finally {
            NConfig.current = previous;
        }
    }
}
