package haven.render.vk;

import haven.Loading;
import haven.render.*;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Real draw-list registration, retry and removal; only native builds are replaced. */
class VkDeferredDrawListTest {
    @Test void objectShadowRegistrationDefersProgramBuildAndRemovalCancelsItsDraw() throws Exception {
        VkAsyncProgramTest.TestEnvironment env = new VkAsyncProgramTest().fixture();
        VkDrawList list = new VkDrawList(env);
        BufPipe pipe = new BufPipe();
        pipe.prep(RenderPreparation.OBJECT);
        GroupPipe state = new GroupPipe() {
            public Pipe group(int group) {return pipe;}
            public int nstates() {return pipe.states().length;}
            public int gstate(int id) {
                State[] values = pipe.states();
                return id < values.length && values[id] != null ? 0 : -1;
            }
        };
        RenderList.Slot<Rendered> original = new RenderList.Slot<Rendered>() {
            public GroupPipe state() {return state;}
            public Rendered obj() {return (p, g) -> {};}
        };
        haven.ShadowMap.ShadowList shadows = new haven.ShadowMap.ShadowList(new RenderTree());
        shadows.basic(haven.ShadowMap.ShadowList.shadowbasic);
        RenderList.Slot<Rendered> shadow = shadows.new Shadowslot(original);
        Field uf = Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        VkRender frame = (VkRender)((Unsafe)uf.get(null)).allocateInstance(VkRender.class);
        Field envField = VkRender.class.getDeclaredField("env");
        envField.setAccessible(true);
        envField.set(frame, env);
        try {
            list.add(shadow);
            assertEquals(0, env.builds.get(), "Shadow registration must not compile under the render-tree lock");
            list.draw(frame);
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertEquals("0", list.stats());
            list.remove(shadow);
            env.release.countDown();
            env.preparation.close();
            list.draw(frame);
            assertEquals("0", list.stats(), "A removed shadow must not appear after background completion");
            assertEquals(1, env.builds.get());
        } finally {
            env.release.countDown();
            env.preparation.close();
            list.dispose();
            shadows.dispose();
        }
    }

    @Test void removedObjectCannotReappearAfterItsBackgroundProgramCompletes() throws Exception {
        VkAsyncProgramTest.TestEnvironment env = new VkAsyncProgramTest().fixture();
        VkDrawList list = new VkDrawList(env);
        BufPipe pipe = new BufPipe();
        pipe.prep(RenderPreparation.OBJECT);
        GroupPipe state = new GroupPipe() {
            public Pipe group(int group) {return pipe;}
            public int nstates() {return pipe.states().length;}
            public int gstate(int id) {
                State[] values = pipe.states();
                return id < values.length && values[id] != null ? 0 : -1;
            }
        };
        RenderList.Slot<Rendered> slot = new RenderList.Slot<Rendered>() {
            public GroupPipe state() {return state;}
            public Rendered obj() {return (p, g) -> {};}
        };
        Field uf = Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        VkRender frame = (VkRender)((Unsafe)uf.get(null)).allocateInstance(VkRender.class);
        Field envField = VkRender.class.getDeclaredField("env");
        envField.setAccessible(true);
        envField.set(frame, env);
        try {
            list.add(slot);
            assertEquals(0, env.builds.get());
            list.draw(frame);
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertEquals("0", list.stats());
            Field pendingField = VkEnvironment.class.getDeclaredField("pendingPrograms");
            pendingField.setAccessible(true);
            Object pending = ((Map<?, ?>)pendingField.get(env)).values().iterator().next();
            Loading wait = assertThrows(Loading.class, () -> ((PrepareTaskQueue.Job<?>)pending).get());
            list.remove(slot);
            env.release.countDown();
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> wait.waitfor());
            list.draw(frame);
            assertEquals("0", list.stats());
            assertEquals(1, env.builds.get());
            assertThrows(IllegalStateException.class, () -> list.remove(slot));
        } finally {
            env.release.countDown();
            env.preparation.close();
            list.dispose();
        }
    }
}
