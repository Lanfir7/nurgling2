package haven.render.vk;

import haven.Loading;
import haven.render.BlendMode;
import haven.render.PrepareTaskQueue;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real admission, publication and retention; only native handles are substituted. */
class VkAsyncPipelineTest {
    static class TestProgram extends VkProgram {
        AtomicInteger creates;
        List<Long> destroyed;
        CountDownLatch entered, release;
        RuntimeException failure;
        Thread caller;
        boolean immediateDuplicate;
        PipeKey duplicateKey;

        private TestProgram() {super(null, null);}

        @Override long createPipeline(PipeKey key) {
            if(immediateDuplicate && Thread.currentThread() == caller) {
                creates.incrementAndGet();
                return 202;
            }
            int ordinal = creates.incrementAndGet();
            entered.countDown();
            await(release);
            if(failure != null)
                throw failure;
            return 100 + ordinal;
        }

        @Override void destroyPipeline(long handle) {
            if(duplicateKey != null)
                assertFalse(Thread.holdsLock(duplicateKey), "driver destruction held the pipe key monitor");
            destroyed.add(handle);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final VkEnvironment env;
        final TestProgram prog;

        Fixture(VkEnvironment env, TestProgram prog) {
            this.env = env;
            this.prog = prog;
        }

        VkProgram.PipeKey key(int topology) {
            return prog.pipekey(null, topology, new int[] {1}, 0,
                    new BlendMode[] {null}, new int[] {15});
        }

        @Override public void close() {
            prog.release.countDown();
            env.preparation.close();
        }
    }

    private static Fixture fixture(int capacity) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe)field.get(null);
        VkEnvironment env = (VkEnvironment)unsafe.allocateInstance(VkEnvironment.class);
        TestProgram prog = (TestProgram)unsafe.allocateInstance(TestProgram.class);
        set(VkEnvironment.class, env, "preparation", new PrepareTaskQueue("pipeline test", capacity));
        set(VkEnvironment.class, env, "npipes", new AtomicInteger());
        set(VkObject.class, prog, "env", env);
        set(VkProgram.class, prog, "pipes", new HashMap<>());
        set(VkProgram.class, prog, "locked", new AtomicInteger());
        prog.creates = new AtomicInteger();
        prog.destroyed = Collections.synchronizedList(new ArrayList<>());
        prog.entered = new CountDownLatch(1);
        prog.release = new CountDownLatch(1);
        prog.caller = Thread.currentThread();
        return new Fixture(env, prog);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int rc(VkProgram prog) throws Exception {
        Field field = VkObject.class.getDeclaredField("rc");
        field.setAccessible(true);
        return field.getInt(prog);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "worker did not reach latch");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test void firstRequestPollsWithoutCallerBuildAndSharesOnePendingJob() throws Exception {
        try(Fixture fixture = fixture(2)) {
            VkProgram.PipeKey key = fixture.key(1);
            assertFalse(assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> fixture.prog.pipelineReady(key)));
            await(fixture.prog.entered);
            PrepareTaskQueue.Job<Long> pending = key.pending;
            assertFalse(fixture.prog.pipelineReady(key));
            assertSame(pending, key.pending);
            assertEquals(1, fixture.prog.creates.get());
            assertEquals(1, rc(fixture.prog));
            assertEquals(1, fixture.prog.locked.get());
            fixture.prog.release.countDown();
            assertEquals(Long.valueOf(101), Loading.waitfor(key.pending));
            assertTrue(fixture.prog.pipelineReady(key));
            assertEquals(101, key.pipe);
            assertEquals(1, fixture.env.npipes.get());
            assertEquals(0, rc(fixture.prog));
            assertEquals(0, fixture.prog.locked.get());
        }
    }

    @Test void saturationReleasesRetentionAndRetriesWithoutCallerBuild() throws Exception {
        try(Fixture fixture = fixture(1)) {
            VkProgram.PipeKey first = fixture.key(1), second = fixture.key(2);
            assertFalse(fixture.prog.pipelineReady(first));
            await(fixture.prog.entered);
            assertFalse(assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> fixture.prog.pipelineReady(second)));
            assertNull(second.pending);
            assertEquals(1, fixture.prog.creates.get());
            assertEquals(1, rc(fixture.prog));
            assertEquals(1, fixture.prog.locked.get());
            fixture.prog.release.countDown();
            Loading.waitfor(first.pending);
            fixture.prog.pipelineReady(second);
            assertNotNull(second.pending);
            Loading.waitfor(second.pending);
            assertTrue(fixture.prog.pipelineReady(second));
            assertEquals(2, fixture.prog.creates.get());
            assertEquals(0, rc(fixture.prog));
            assertEquals(0, fixture.prog.locked.get());
        }
    }

    @Test void workerFailureReleasesRetentionAndReachesPollingCaller() throws Exception {
        try(Fixture fixture = fixture(1)) {
            VkProgram.PipeKey key = fixture.key(1);
            fixture.prog.failure = new IllegalArgumentException("pipeline rejected");
            assertFalse(fixture.prog.pipelineReady(key));
            await(fixture.prog.entered);
            fixture.prog.release.countDown();
            assertSame(fixture.prog.failure, assertThrows(IllegalArgumentException.class,
                    () -> Loading.waitfor(key.pending)));
            assertSame(fixture.prog.failure, assertThrows(IllegalArgumentException.class,
                    () -> fixture.prog.pipelineReady(key)));
            assertEquals(0, rc(fixture.prog));
            assertEquals(0, fixture.prog.locked.get());
            assertEquals(0, fixture.env.npipes.get());
        }
    }

    @Test void immediateDuplicatePublishesOnceAndDestroysOnlyUnusedHandle() throws Exception {
        try(Fixture fixture = fixture(1)) {
            fixture.prog.immediateDuplicate = true;
            VkProgram.PipeKey key = fixture.key(1);
            fixture.prog.duplicateKey = key;
            assertFalse(fixture.prog.pipelineReady(key));
            await(fixture.prog.entered);
            assertEquals(202, fixture.prog.pipeline(key));
            assertEquals(1, fixture.env.npipes.get());
            fixture.prog.release.countDown();
            assertEquals(Long.valueOf(202), Loading.waitfor(key.pending));
            assertEquals(202, key.pipe);
            assertEquals(Collections.singletonList(101L), fixture.prog.destroyed);
            assertEquals(1, fixture.env.npipes.get());
            assertEquals(0, rc(fixture.prog));
            assertEquals(0, fixture.prog.locked.get());
        }
    }
}
