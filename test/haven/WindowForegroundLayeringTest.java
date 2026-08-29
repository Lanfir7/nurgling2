package haven;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowForegroundLayeringTest {
    @Test
    void windowPaintsForegroundOverlayAfterContentChildren() {
        List<String> passes = new ArrayList<>();
        WindowLayering.paint(new WindowLayering.Target() {
            public void drawContent(GOut g) {
                passes.add("content");
            }

            public void drawForeground(GOut g) {
                passes.add("foreground");
            }
        }, null);

        assertEquals(List.of("content", "foreground"), passes);
    }
}
