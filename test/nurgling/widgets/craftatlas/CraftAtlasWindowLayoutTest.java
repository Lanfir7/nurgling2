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

    @Test
    void plannerFooterKeepsQuantityCollectAndCraftControlsInsideWithoutOverlap() {
        CraftAtlasLayout.Rect footer = new CraftAtlasLayout.Rect(552, 644, 608, 56);

        CraftAtlasLayout.Rect[] controls = CraftAtlasLayout.footerControls(
                footer, 42, 54, 150, 170, 8, 12);

        for(CraftAtlasLayout.Rect control : controls) {
            assertTrue(control.x >= footer.x);
            assertTrue(control.x + control.w <= footer.x + footer.w);
        }
        for(int i = 1; i < controls.length; i++)
            assertTrue(controls[i - 1].x + controls[i - 1].w <= controls[i].x);
    }

    @Test
    void plannerFooterShrinksControlsWhenDetailsPaneIsNarrow() {
        CraftAtlasLayout.Rect footer = new CraftAtlasLayout.Rect(552, 644, 420, 56);

        CraftAtlasLayout.Rect[] controls = CraftAtlasLayout.footerControls(
                footer, 42, 54, 160, 170, 8, 12);

        for(int i = 0; i < controls.length; i++) {
            assertTrue(controls[i].x >= footer.x);
            assertTrue(controls[i].x + controls[i].w <= footer.x + footer.w);
            if(i > 0) assertTrue(controls[i - 1].x + controls[i - 1].w <= controls[i].x);
        }
    }
}
