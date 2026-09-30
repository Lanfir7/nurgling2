package haven;

import haven.render.*;
import haven.render.sl.ShaderMacro;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowPreparationTest {
    private static class OtherState extends State {
        static final Slot<OtherState> sys = new Slot<>(Slot.Type.SYS, OtherState.class);
        static final Slot<OtherState> draw = new Slot<>(Slot.Type.DRAW, OtherState.class);
        static final Slot<OtherState> geom = new Slot<>(Slot.Type.GEOM, OtherState.class);
        final Slot<OtherState> target;
        OtherState(Slot<OtherState> target) {this.target = target;}
        public void apply(Pipe pipe) {pipe.put(target, this);}
        public ShaderMacro shader() {return null;}
    }

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

    private RenderList.Slot<Rendered> shadow(Pipe pipe) {
        ShadowMap.ShadowList list = new ShadowMap.ShadowList(new RenderTree());
        list.basic(ShadowMap.ShadowList.shadowbasic);
        return list.new Shadowslot(slot(pipe));
    }

    @Test void objectShadowKeepsBackgroundPreparationPolicy() {
        Pipe pipe = new BufPipe().prep(RenderPreparation.OBJECT);
        assertTrue(RenderPreparation.deferred(shadow(pipe)), "Object shadows must not prepare synchronously");
    }

    @Test void criticalShadowOverridesObjectPolicy() {
        Pipe pipe = new BufPipe().prep(RenderPreparation.OBJECT).prep(RenderPreparation.IMMEDIATE);
        assertFalse(RenderPreparation.deferred(shadow(pipe)));
    }

    @Test void terrainWithoutObjectHintRemainsImmediate() {
        assertFalse(RenderPreparation.deferred(shadow(new BufPipe())));
    }

    @Test void shadowOnlyInheritsGeometryAndSchedulingNotOtherMainPassStates() {
        OtherState sys = new OtherState(OtherState.sys), draw = new OtherState(OtherState.draw);
        OtherState geom = new OtherState(OtherState.geom);
        Pipe pipe = new BufPipe().prep(sys).prep(draw).prep(geom).prep(RenderPreparation.OBJECT);
        Pipe state = shadow(pipe).state();
        assertNull(state.get(OtherState.sys));
        assertNull(state.get(OtherState.draw));
        assertSame(geom, state.get(OtherState.geom));
        assertSame(RenderPreparation.OBJECT, state.get(RenderPreparation.slot));
    }

    @Test void changedObjectHintIsSeenThroughExistingShadowAdapter() {
        Pipe pipe = new BufPipe().prep(RenderPreparation.OBJECT);
        RenderList.Slot<Rendered> entry = shadow(pipe);
        pipe.prep(RenderPreparation.IMMEDIATE);
        assertFalse(RenderPreparation.deferred(entry));
        pipe.prep(RenderPreparation.OBJECT);
        assertTrue(RenderPreparation.deferred(entry));
    }
}
