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

    @Test void directionsUseTheExpectedArrowExtents() {
        float[] right = NAreaDirectionArrow.arrowVertices(PileFillDirection.LEFT_TO_RIGHT);
        assertEquals(-19.0f, minX(right), 0.001f);
        assertEquals(19.0f, maxX(right), 0.001f);
        assertEquals(-11.0f, minY(right), 0.001f);
        assertEquals(11.0f, maxY(right), 0.001f);

        float[] left = NAreaDirectionArrow.arrowVertices(PileFillDirection.RIGHT_TO_LEFT);
        assertEquals(-19.0f, minX(left), 0.001f);
        assertEquals(19.0f, maxX(left), 0.001f);

        float[] down = NAreaDirectionArrow.arrowVertices(PileFillDirection.TOP_TO_BOTTOM);
        assertEquals(-11.0f, minX(down), 0.001f);
        assertEquals(11.0f, maxX(down), 0.001f);
        assertEquals(-19.0f, minY(down), 0.001f);
        assertEquals(19.0f, maxY(down), 0.001f);

        float[] up = NAreaDirectionArrow.arrowVertices(PileFillDirection.BOTTOM_TO_TOP);
        assertEquals(-19.0f, minY(up), 0.001f);
        assertEquals(19.0f, maxY(up), 0.001f);
    }

    @Test void clearsInheritedBackFaceCulling() {
        RenderTree tree = new RenderTree();
        ArrowList arrows = new ArrowList();
        tree.add(arrows, NAreaDirectionArrow.class);

        RenderTree.Slot parent = tree.add(null, new States.Facecull(States.Facecull.Mode.BACK));
        parent.add(new NAreaDirectionArrow(null, null));

        assertNull(arrows.slot.state().get(States.facecull));
    }

    private static class ArrowList implements RenderList<NAreaDirectionArrow> {
        private RenderList.Slot<? extends NAreaDirectionArrow> slot;

        @Override public void add(RenderList.Slot<? extends NAreaDirectionArrow> slot) {
            this.slot = slot;
        }

        @Override public void remove(RenderList.Slot<? extends NAreaDirectionArrow> slot) {}

        @Override public void update(RenderList.Slot<? extends NAreaDirectionArrow> slot) {}

        @Override public void update(Pipe group, int[] statemask) {}
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
