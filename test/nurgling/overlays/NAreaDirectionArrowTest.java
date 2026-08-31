package nurgling.overlays;

import haven.render.Pipe;
import haven.render.RenderList;
import haven.render.RenderTree;
import haven.render.States;
import nurgling.areas.PileFillDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NAreaDirectionArrowTest {
    @Test void visibleOnlyForSelectedLoadedZoneInOpenEditor() {
        assertTrue(NAreaDirectionArrow.shouldDraw(true, true, true));
        assertFalse(NAreaDirectionArrow.shouldDraw(false, true, true));
        assertFalse(NAreaDirectionArrow.shouldDraw(true, false, true));
        assertFalse(NAreaDirectionArrow.shouldDraw(true, true, false));
    }

    @Test void oppositeDirectionsMirrorTheirLongAxis() {
        float[] right = NAreaDirectionArrow.arrowVertices(PileFillDirection.LEFT_TO_RIGHT);
        float[] left = NAreaDirectionArrow.arrowVertices(PileFillDirection.RIGHT_TO_LEFT);
        assertEquals(maxX(right), -minX(left), 0.001f);

        float[] down = NAreaDirectionArrow.arrowVertices(PileFillDirection.TOP_TO_BOTTOM);
        float[] up = NAreaDirectionArrow.arrowVertices(PileFillDirection.BOTTOM_TO_TOP);
        assertEquals(maxY(down), -minY(up), 0.001f);
    }

    @Test void directionsUseHalfScaleArrowExtents() {
        float[] right = NAreaDirectionArrow.arrowVertices(PileFillDirection.LEFT_TO_RIGHT);
        assertEquals(-9.5f, minX(right), 0.001f);
        assertEquals(9.5f, maxX(right), 0.001f);
        assertEquals(-5.5f, minY(right), 0.001f);
        assertEquals(5.5f, maxY(right), 0.001f);

        float[] left = NAreaDirectionArrow.arrowVertices(PileFillDirection.RIGHT_TO_LEFT);
        assertEquals(-9.5f, minX(left), 0.001f);
        assertEquals(9.5f, maxX(left), 0.001f);

        float[] down = NAreaDirectionArrow.arrowVertices(PileFillDirection.TOP_TO_BOTTOM);
        assertEquals(-5.5f, minX(down), 0.001f);
        assertEquals(5.5f, maxX(down), 0.001f);
        assertEquals(-9.5f, minY(down), 0.001f);
        assertEquals(9.5f, maxY(down), 0.001f);
        // Default tip (9.5, 0) rotates via (x,y)->(y,-x) to (0, -9.5).
        assertTrue(hasXY(down, 0.0f, -9.5f));
        assertFalse(hasXY(down, 0.0f, 9.5f));

        float[] up = NAreaDirectionArrow.arrowVertices(PileFillDirection.BOTTOM_TO_TOP);
        assertEquals(-9.5f, minY(up), 0.001f);
        assertEquals(9.5f, maxY(up), 0.001f);
        // Default tip (9.5, 0) rotates via (x,y)->(-y,x) to (0, 9.5).
        assertTrue(hasXY(up, 0.0f, 9.5f));
        assertFalse(hasXY(up, 0.0f, -9.5f));
    }

    @Test void clearsInheritedBackFaceCulling() {
        RenderTree tree = new RenderTree();
        ArrowList arrows = new ArrowList();
        tree.add(arrows, NAreaDirectionArrow.class);

        RenderTree.Slot parent = tree.add(null, new States.Facecull(States.Facecull.Mode.BACK));
        parent.add(new NAreaDirectionArrow(null, null));

        assertNull(arrows.slot.state().get(States.facecull));
    }

    @Test void invalidatesWorldDrawListWhenVisibilityOrDirectionChanges() {
        RenderTree tree = new RenderTree();
        ArrowList arrows = new ArrowList();
        tree.add(arrows, NAreaDirectionArrow.class);
        NAreaDirectionArrow arrow = new NAreaDirectionArrow(null, null);
        tree.add(arrow);

        arrow.refreshRenderState(false, false, false, PileFillDirection.LEFT_TO_RIGHT);
        assertEquals(1, arrows.updates);

        arrow.refreshRenderState(true, true, true, PileFillDirection.LEFT_TO_RIGHT);
        assertEquals(2, arrows.updates);

        arrow.refreshRenderState(true, true, true, PileFillDirection.LEFT_TO_RIGHT);
        assertEquals(2, arrows.updates);

        arrow.refreshRenderState(true, true, true, PileFillDirection.RIGHT_TO_LEFT);
        assertEquals(3, arrows.updates);

        arrow.refreshRenderState(true, false, true, PileFillDirection.RIGHT_TO_LEFT);
        assertEquals(4, arrows.updates);
    }

    private static class ArrowList implements RenderList<NAreaDirectionArrow> {
        private RenderList.Slot<? extends NAreaDirectionArrow> slot;

        @Override public void add(RenderList.Slot<? extends NAreaDirectionArrow> slot) {
            this.slot = slot;
        }

        @Override public void remove(RenderList.Slot<? extends NAreaDirectionArrow> slot) {}

        private int updates;

        @Override public void update(RenderList.Slot<? extends NAreaDirectionArrow> slot) {
            updates++;
        }

        @Override public void update(Pipe group, int[] statemask) {}
    }

    private static boolean hasXY(float[] vertices, float x, float y) {
        for (int index = 0; index < vertices.length; index += 3) {
            if (Math.abs(vertices[index] - x) < 0.001f && Math.abs(vertices[index + 1] - y) < 0.001f)
                return true;
        }
        return false;
    }

    private static float minX(float[] vertices) {
        float result = Float.POSITIVE_INFINITY;
        for (int index = 0; index < vertices.length; index += 3)
            result = Math.min(result, vertices[index]);
        return result;
    }

    private static float maxX(float[] vertices) {
        float result = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < vertices.length; index += 3)
            result = Math.max(result, vertices[index]);
        return result;
    }

    private static float minY(float[] vertices) {
        float result = Float.POSITIVE_INFINITY;
        for (int index = 1; index < vertices.length; index += 3)
            result = Math.min(result, vertices[index]);
        return result;
    }

    private static float maxY(float[] vertices) {
        float result = Float.NEGATIVE_INFINITY;
        for (int index = 1; index < vertices.length; index += 3)
            result = Math.max(result, vertices[index]);
        return result;
    }
}
