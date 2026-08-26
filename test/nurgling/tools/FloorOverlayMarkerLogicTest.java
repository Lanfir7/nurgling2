package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FloorOverlayMarkerLogicTest {
    @Test
    void srcTileAddsOffset() {
        assertEquals(new Coord(150, 80),
                FloorOverlayMarkerLogic.srcTile(new Coord(100, 100), new Coord(50, -20)));
    }

    @Test
    void destToScreenMatchesOverlayTileFormula() {
        Coord destTc = new Coord(10, 20);
        Coord offset = new Coord(50, -20);
        Coord dlocTc = new Coord(100, 100);
        float scalef = 2f;
        float currentScale = 0.5f;
        Coord hsz = new Coord(200, 150);
        float uiScale = 1f;

        Coord screen = FloorOverlayMarkerLogic.destToScreen(
                destTc, offset, dlocTc, scalef, currentScale, hsz, uiScale);

        Coord src = destTc.add(offset);
        Coord dlocDiv = dlocTc.div((double) scalef);
        int x = (int) Math.round(src.x * uiScale * currentScale - dlocDiv.x + hsz.x);
        int y = (int) Math.round(src.y * uiScale * currentScale - dlocDiv.y + hsz.y);
        assertEquals(new Coord(x, y), screen);
    }

    @Test
    void onScreenRejectsOutside() {
        Coord sz = new Coord(100, 80);
        assertTrue(FloorOverlayMarkerLogic.onScreen(new Coord(0, 0), sz));
        assertTrue(FloorOverlayMarkerLogic.onScreen(new Coord(100, 80), sz));
        assertFalse(FloorOverlayMarkerLogic.onScreen(new Coord(-1, 10), sz));
        assertFalse(FloorOverlayMarkerLogic.onScreen(new Coord(10, 81), sz));
    }

    @Test
    void searchEmptyShowsAllNullNameHiddenWhenSearching() {
        assertTrue(FloorOverlayMarkerLogic.matchesSearch("Iron", null));
        assertTrue(FloorOverlayMarkerLogic.matchesSearch("Iron", "  "));
        assertTrue(FloorOverlayMarkerLogic.matchesSearch("Cassiterite", "cass"));
        assertFalse(FloorOverlayMarkerLogic.matchesSearch("Iron", "gold"));
        assertFalse(FloorOverlayMarkerLogic.matchesSearch(null, "x"));
    }

    @Test
    void prospectingRespectsToggleAndHideAll() {
        assertTrue(FloorOverlayMarkerLogic.shouldShowProspecting(true, false));
        assertFalse(FloorOverlayMarkerLogic.shouldShowProspecting(false, false));
        assertFalse(FloorOverlayMarkerLogic.shouldShowProspecting(true, true));
    }

    @Test
    void overlayInactiveOnSameSegmentOrDisabled() {
        assertTrue(FloorOverlayMarkerLogic.overlayActive(true, 20L, 10L));
        assertFalse(FloorOverlayMarkerLogic.overlayActive(true, 10L, 10L));
        assertFalse(FloorOverlayMarkerLogic.overlayActive(false, 20L, 10L));
    }

    @Test
    void hoverUsesPixelThreshold() {
        Coord mark = new Coord(50, 50);
        assertTrue(FloorOverlayMarkerLogic.hoverHit(new Coord(55, 50), mark, 10));
        assertFalse(FloorOverlayMarkerLogic.hoverHit(new Coord(70, 50), mark, 10));
    }

    @Test
    void overlayTooltipPrefersProspectingThenVanilla() {
        assertEquals(1, FloorOverlayMarkerLogic.overlayTooltipKind(true, true));
        assertEquals(1, FloorOverlayMarkerLogic.overlayTooltipKind(true, false));
        assertEquals(2, FloorOverlayMarkerLogic.overlayTooltipKind(false, true));
        assertEquals(0, FloorOverlayMarkerLogic.overlayTooltipKind(false, false));
    }
}
