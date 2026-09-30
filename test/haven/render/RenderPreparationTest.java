package haven.render;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class RenderPreparationTest {
    @Test void instancingSeparatesImmediateAndDeferredPoliciesWithoutSplittingObjects() {
        State.Instancer<RenderPreparation> objects = RenderPreparation.slot.instanced.instid(RenderPreparation.OBJECT);
        assertSame(objects, RenderPreparation.slot.instanced.instid(RenderPreparation.OBJECT));
        assertNotSame(objects, RenderPreparation.slot.instanced.instid(RenderPreparation.IMMEDIATE));
        assertNotSame(objects, RenderPreparation.slot.instanced.instid(null));
        assertSame(RenderPreparation.OBJECT, objects.inststate(RenderPreparation.OBJECT, null));
    }

    @Test void instancedObjectsKeepTheirDeferredHint() {
        BufPipe original = new BufPipe();
        original.prep(RenderPreparation.OBJECT);
        assertTrue(RenderPreparation.deferred(batchSlot(new BufPipe(), original)));
    }

    @Test void criticalBatchHintOverridesDeferredOriginalInstance() {
        BufPipe original = new BufPipe(), critical = new BufPipe();
        original.prep(RenderPreparation.OBJECT);
        critical.prep(RenderPreparation.IMMEDIATE);
        assertFalse(RenderPreparation.deferred(batchSlot(critical, original)));
    }

    @SuppressWarnings("unchecked")
    private RenderList.Slot<Rendered> batchSlot(Pipe current, Pipe original) {
        GroupPipe state = (GroupPipe)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {GroupPipe.class},
                (proxy, method, args) -> {
                    if(method.getName().equals("get")) return current.get((State.Slot)args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
        return (RenderList.Slot<Rendered>)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {RenderList.Slot.class, InstanceBatch.class},
                (proxy, method, args) -> {
                    switch(method.getName()) {
                        case "state": return state;
                        case "obj": return (Rendered)(p, g) -> {};
                        case "instances": return 2;
                        case "inststate": return original;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test void defaultWorldIsImmediateAndObjectHintDefersOnlyObjects() {
        BufPipe world = new BufPipe();
        assertFalse(RenderPreparation.deferred(world));
        world.prep(RenderPreparation.OBJECT);
        assertTrue(RenderPreparation.deferred(world));
    }

    @Test void dangerOverlayOverridesItsObjectsHintWithoutChangingShaders() {
        BufPipe object = new BufPipe();
        object.prep(RenderPreparation.OBJECT);
        object.prep(RenderPreparation.IMMEDIATE);
        assertFalse(RenderPreparation.deferred(object));
        assertNull(RenderPreparation.IMMEDIATE.shader());
        assertNull(RenderPreparation.OBJECT.shader());
    }
}
