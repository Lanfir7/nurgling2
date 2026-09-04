package nurgling.widgets.craftatlas;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasWindowLayoutTest {
    @Test
    void visibleControlsStayInsideTheDecoratedWindowContentArea() {
        Coord content = Coord.of(900, 600);
        CraftAtlasLayout layout = CraftAtlasLayout.compute(content.x, content.y, 1.0);

        assertTrue(layout.footer.x + layout.footer.w <= content.x);
        assertTrue(layout.footer.y + layout.footer.h <= content.y);
        assertTrue(layout.details.x + layout.details.w <= content.x);
        assertTrue(layout.details.y + layout.details.h <= content.y);
    }
}
