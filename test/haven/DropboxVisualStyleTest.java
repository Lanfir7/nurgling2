package haven;

import org.junit.jupiter.api.Test;
import haven.render.BufPipe;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropboxVisualStyleTest {
    @Test
    void fieldAndArrowReadAsSeparateClickableAreas() {
        assertNotEquals(DropboxVisualStyle.fieldBackground(),
                DropboxVisualStyle.arrowBackground());
        assertNotEquals(DropboxVisualStyle.fieldBackground(),
                DropboxVisualStyle.popupBackground());
    }

    @Test
    void openDropdownAndPopupRowsHaveVisibleStates() {
        assertNotEquals(DropboxVisualStyle.border(false),
                DropboxVisualStyle.border(true));
        assertNotEquals(DropboxVisualStyle.popupBackground(),
                DropboxVisualStyle.hoverBackground());
        assertNotEquals(DropboxVisualStyle.hoverBackground(),
                DropboxVisualStyle.selectedBackground());
        assertTrue(DropboxVisualStyle.border(true).getAlpha() >= 200);
    }

    @Test
    void borderFramesCoverOuterBoundsAndClampToControlSize() {
        DropboxVisualStyle.BorderFrame[] frames =
                DropboxVisualStyle.borderFrames(Coord.of(10, 8), 2);

        assertEquals(2, frames.length);
        assertEquals(Coord.z, frames[0].position);
        assertEquals(Coord.of(10, 8), frames[0].size);
        assertEquals(Coord.of(1, 1), frames[1].position);
        assertEquals(Coord.of(8, 6), frames[1].size);
        assertEquals(1,
                DropboxVisualStyle.borderFrames(Coord.of(3, 2), 5).length);
    }

    @Test
    void borderIsDrawnBeforeSelectedText() {
        RecordingGOut g = new RecordingGOut(Coord.of(100, 16));
        Dropbox<String> dropdown = new Dropbox<String>(100, 1, 16) {
            protected String listitem(int i) {
                return "Type";
            }

            protected int listitems() {
                return 1;
            }

            protected void drawitem(GOut out, String item, int idx) {
                ((RecordingGOut)out).events.add("content");
            }
        };
        dropdown.sel = "Type";

        dropdown.draw(g);

        assertTrue(g.events.indexOf("border") < g.events.indexOf("content"),
                "the border must not paint over the bottom of the text");
    }

    private static final class RecordingGOut extends GOut {
        private final List<String> events = new ArrayList<>();

        private RecordingGOut(Coord size) {
            super(null, new BufPipe(), size);
        }

        public void chcolor(Color color) {
        }

        public void chcolor() {
        }

        public void frect(Coord ul, Coord size) {
        }

        public void rect(Coord ul, Coord size) {
            events.add("border");
        }

        public void image(Tex tex, Coord c) {
        }

        public GOut reclip(Coord ul, Coord size) {
            return this;
        }
    }
}
