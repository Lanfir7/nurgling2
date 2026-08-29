package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TextEntryVisualStyleTest {
    @Test
    void editableFieldHasVisibleFillAndFocusBorder() {
        assertNotEquals(TextEntryVisualStyle.background(),
                TextEntryVisualStyle.border(false));
        assertNotEquals(TextEntryVisualStyle.border(false),
                TextEntryVisualStyle.border(true));
    }

    @Test
    void scaledBorderCoversTheFullOuterBounds() {
        TextEntryVisualStyle.BorderFrame[] frames =
                TextEntryVisualStyle.borderFrames(Coord.of(10, 8), 2);

        assertEquals(2, frames.length);
        assertEquals(Coord.z, frames[0].position);
        assertEquals(Coord.of(10, 8), frames[0].size);
        assertEquals(Coord.of(1, 1), frames[1].position);
        assertEquals(Coord.of(8, 6), frames[1].size);
    }

    @Test
    void customFillCoversThemeCapsBeforeTextIsDrawn() {
        assertArrayEquals(new TextEntryVisualStyle.BaseLayer[] {
                        TextEntryVisualStyle.BaseLayer.THEME,
                        TextEntryVisualStyle.BaseLayer.CAPS,
                        TextEntryVisualStyle.BaseLayer.FILL
                }, TextEntryVisualStyle.BASE_LAYERS);
    }
}
