package haven;

import org.junit.jupiter.api.Test;

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
}
