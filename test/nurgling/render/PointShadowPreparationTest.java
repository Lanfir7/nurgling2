package nurgling.render;

import haven.ShadowMap;
import haven.render.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointShadowPreparationTest {
    private RenderList.Slot<Rendered> slot(Pipe pipe) {
        GroupPipe state = new GroupPipe() {
            public Pipe group(int group) {return pipe;}
            public int nstates() {return pipe.states().length;}
            public int gstate(int id) {
                State[] states = pipe.states();
                return id < states.length && states[id] != null ? 0 : -1;
            }
        };
        return new RenderList.Slot<Rendered>() {
            public GroupPipe state() {return state;}
            public Rendered obj() {return (p, out) -> {};}
        };
    }

    private RenderList.Slot<Rendered> shadow(Pipe pipe, int side) {
        PointShadows shadows = new PointShadows(new RenderTree());
        PointShadows.Face face = shadows.new Face(side);
        face.basic(ShadowMap.ShadowList.shadowbasic);
        return face.new FSlot(slot(pipe));
    }

    @Test void allPointShadowSidesKeepObjectPreparationInBackground() {
        // Point-light shadows use the four walls and ceiling, not the ground face.
        for(int side = 0; side < 5; side++) {
            Pipe pipe = new BufPipe().prep(RenderPreparation.OBJECT);
            assertTrue(RenderPreparation.deferred(shadow(pipe, side)), "Point shadow side " + side);
        }
    }

    @Test void criticalAndTerrainPointShadowsRemainImmediate() {
        assertFalse(RenderPreparation.deferred(shadow(new BufPipe(), 0)));
        Pipe pipe = new BufPipe().prep(RenderPreparation.OBJECT).prep(RenderPreparation.IMMEDIATE);
        assertFalse(RenderPreparation.deferred(shadow(pipe, 0)));
    }

    @Test void pointShadowKeepsItsOwnViewportNotMainPassSettings() {
        States.Viewport shadowViewport = new States.Viewport(haven.Area.sized(new haven.Coord(16, 16)));
        Pipe pipe = new BufPipe().prep(RenderPreparation.OBJECT)
            .prep(new States.Viewport(haven.Area.sized(new haven.Coord(1024, 1024))));
        PointShadows shadows = new PointShadows(new RenderTree());
        PointShadows.Face face = shadows.new Face(0);
        face.basic(shadowViewport);
        Pipe state = face.new FSlot(slot(pipe)).state();
        assertSame(shadowViewport, state.get(States.viewport));
        assertSame(RenderPreparation.OBJECT, state.get(RenderPreparation.slot));
    }
}
