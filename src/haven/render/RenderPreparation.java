package haven.render;

import haven.render.sl.ShaderMacro;

/** Scheduling hint only; does not change shaders or object simulation. */
public final class RenderPreparation extends State {
    public static final RenderPreparation OBJECT = new RenderPreparation(true);
    public static final RenderPreparation IMMEDIATE = new RenderPreparation(false);
    private static final Instancer<RenderPreparation> objects = (state, batch) -> OBJECT;
    private static final Instancer<RenderPreparation> immediate = (state, batch) -> IMMEDIATE;
    public static final Slot<RenderPreparation> slot =
            new Slot<>(Slot.Type.SYS, RenderPreparation.class)
                    .instanced(state -> state != null && state.deferred ? objects : immediate);
    private final boolean deferred;

    private RenderPreparation(boolean deferred) {this.deferred = deferred;}
    public void apply(Pipe pipe) {pipe.put(slot, this);}
    public ShaderMacro shader() {return null;}

    public static boolean deferred(Pipe pipe) {
        RenderPreparation hint = pipe.get(slot);
        return hint != null && hint.deferred;
    }

    public static boolean deferred(RenderList.Slot<? extends Rendered> entry) {
        Pipe state = entry.state();
        RenderPreparation hint = state.get(slot);
        if(hint != null)
            return hint.deferred;
        /* Also support an adapter that strips SYS hints from its batch state.
         * Policy-specific instancers keep critical draws out of object batches. */
        if(entry instanceof InstanceBatch) {
            InstanceBatch batch = (InstanceBatch)entry;
            if(batch.instances() > 0)
                return deferred(batch.inststate(0));
        }
        return false;
    }
}
