package nurgling.widgets.craftatlas;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasWindowLayoutTest {
    @Test
    void scrollingBodyStartsBelowItsFixedHeader() {
        CraftAtlasLayout.Rect body = CraftAtlasLayout.scrollBody(600, 500, 104);

        assertEquals(0, body.x);
        assertEquals(104, body.y);
        assertEquals(600, body.w);
        assertEquals(396, body.h);
    }

    @Test
    void favoriteStarSitsImmediatelyAfterTheProductName() {
        CraftAtlasLayout.Rect details = new CraftAtlasLayout.Rect(400, 56, 500, 500);

        CraftAtlasLayout.Rect star = CraftAtlasLayout.favoriteAfterTitle(
                details, 98, 125, 28, 6, 10);

        assertEquals(629, star.x);
        assertEquals(66, star.y);
        assertEquals(28, star.w);
        assertEquals(28, star.h);
    }

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
                footer, 54, 150, 170, 8, 12);

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
                footer, 54, 160, 170, 8, 12);

        for(int i = 0; i < controls.length; i++) {
            assertTrue(controls[i].x >= footer.x);
            assertTrue(controls[i].x + controls[i].w <= footer.x + footer.w);
            if(i > 0) assertTrue(controls[i - 1].x + controls[i - 1].w <= controls[i].x);
        }
    }
}
