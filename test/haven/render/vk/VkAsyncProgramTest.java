package haven.render.vk;

import haven.Loading;
import haven.render.PrepareTaskQueue;
import haven.render.sl.ShaderMacro;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Only the native program build is substituted; cache/admission/polling are real. */
class VkAsyncProgramTest {
    static class TestEnvironment extends VkEnvironment {
        AtomicInteger builds;
        CountDownLatch entered, release;
        VkProgram result;
        RuntimeException failure;
        Thread caller;
        VkShaderCompiler selectedCompiler;

        private TestEnvironment() {super(null, null);}
        @Override VkProgram buildprog(Collection<ShaderMacro> mods) {
            assertNotSame(caller, Thread.currentThread());
            selectedCompiler = shaderCompiler();
            builds.incrementAndGet();
            entered.countDown();
            try {assertTrue(release.await(5, TimeUnit.SECONDS));}
            catch(InterruptedException e) {throw new AssertionError(e);}
            if(failure != null) throw failure;
            return result;
        }
    }

    TestEnvironment fixture() throws Exception {
        Field uf = Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        Unsafe unsafe = (Unsafe)uf.get(null);
        TestEnvironment env = (TestEnvironment)unsafe.allocateInstance(TestEnvironment.class);
        set(env, "pmon", new Object());
        set(env, "ptab", new VkEnvironment.SavedProg[32]);
        set(env, "pendingPrograms", new HashMap<>());
        set(env, "preparation", new PrepareTaskQueue("test graphics prep", 1));
        set(env, "backgroundProgram", new ThreadLocal<Boolean>());
        set(env, "compiler", unsafe.allocateInstance(VkShaderCompiler.class));
        set(env, "preparationCompiler", unsafe.allocateInstance(VkShaderCompiler.class));
        env.builds = new AtomicInteger();
        env.entered = new CountDownLatch(1);
        env.release = new CountDownLatch(1);
        env.result = (VkProgram)unsafe.allocateInstance(VkProgram.class);
        env.caller = Thread.currentThread();
        return env;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = VkEnvironment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void await(Loading pending) {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> pending.waitfor());
    }

    private static Map<?, ?> pending(TestEnvironment env) throws Exception {
        Field field = VkEnvironment.class.getDeclaredField("pendingPrograms");
        field.setAccessible(true);
        return (Map<?, ?>)field.get(env);
    }

    private static void cleanPrograms(TestEnvironment env) throws Exception {
        Method method = VkEnvironment.class.getDeclaredMethod("cleanprogs");
        method.setAccessible(true);
        method.invoke(env);
    }

    @Test void cacheMaintenanceDropsAbandonedFailuresButPreservesRunningWork() throws Exception {
        TestEnvironment env = fixture();
        env.failure = new IllegalArgumentException("abandoned bad shader");
        ShaderMacro a = p -> {};
        try {
            Loading first = assertThrows(Loading.class,
                    () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            cleanPrograms(env);
            assertEquals(1, pending(env).size());
            assertThrows(Loading.class, () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertEquals(1, env.builds.get());
            env.release.countDown();
            await(first);
            cleanPrograms(env);
            assertTrue(pending(env).isEmpty());
            env.failure = null;
            env.entered = new CountDownLatch(1);
            env.release = new CountDownLatch(1);
            assertThrows(Loading.class, () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertEquals(2, env.builds.get());
        } finally {env.release.countDown(); env.preparation.close();}
    }

    @Test void programMissNeverBuildsOnCallerAndIdenticalRequestsShareWork() throws Exception {
        TestEnvironment env = fixture();
        ShaderMacro a = p -> {};
        int hash = System.identityHashCode(a);
        try {
            Loading first = assertThrows(Loading.class, () -> env.getprogAsync(hash, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertThrows(Loading.class, () -> env.getprogAsync(hash, new ShaderMacro[] {a, null}));
            assertEquals(1, env.builds.get());
            env.release.countDown();
            await(first);
            assertSame(env.result, env.getprogAsync(hash, new ShaderMacro[] {a}));
            assertSame(env.result, env.getprogAsync(hash, new ShaderMacro[] {a, null}));
            assertEquals(1, env.builds.get());
        } finally {env.release.countDown(); env.preparation.close();}
    }

    @Test void backgroundCompilationUsesItsOwnCompilerAndDropsCompletedTicket() throws Exception {
        TestEnvironment env = fixture();
        ShaderMacro a = p -> {};
        try {
            assertSame(env.compiler, env.shaderCompiler());
            Loading first = assertThrows(Loading.class, () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertNotSame(env.compiler, env.selectedCompiler);
            env.release.countDown();
            await(first);
            Field field = VkEnvironment.class.getDeclaredField("pendingPrograms");
            field.setAccessible(true);
            assertTrue(((Map<?, ?>)field.get(env)).isEmpty());
            assertSame(env.compiler, env.shaderCompiler());
        } finally {env.release.countDown(); env.preparation.close();}
    }

    @Test void saturatedQueueDoesNotCompileSecondVariantOnCaller() throws Exception {
        TestEnvironment env = fixture();
        ShaderMacro a = p -> {}, b = p -> {};
        try {
            Loading first = assertThrows(Loading.class,
                    () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertThrows(Loading.class, () -> env.getprogAsync(2, new ShaderMacro[] {b}));
            assertEquals(1, env.builds.get());
            env.release.countDown();
            await(first);
        } finally {env.release.countDown(); env.preparation.close();}
    }

    @Test void failedBuildReportsItsRealErrorInsteadOfEndlessLoading() throws Exception {
        TestEnvironment env = fixture();
        env.failure = new IllegalArgumentException("bad shader");
        ShaderMacro a = p -> {};
        try {
            Loading first = assertThrows(Loading.class,
                    () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            env.release.countDown();
            await(first);
            assertSame(env.failure, assertThrows(IllegalArgumentException.class,
                    () -> env.getprogAsync(1, new ShaderMacro[] {a})));
            assertTrue(pending(env).isEmpty(), "delivered failures must not pin requests");
        } finally {env.release.countDown(); env.preparation.close();}
    }

    @Test void resourceLoadingFailureCanRetryInsteadOfPinningFailedJobForever() throws Exception {
        TestEnvironment env = fixture();
        env.failure = new Loading("shader resource pending");
        ShaderMacro a = p -> {};
        try {
            Loading first = assertThrows(Loading.class,
                    () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            env.release.countDown();
            await(first);
            assertThrows(Loading.class, () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            env.failure = null;
            env.entered = new CountDownLatch(1);
            env.release = new CountDownLatch(1);
            assertThrows(Loading.class, () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertEquals(2, env.builds.get());
        } finally {env.release.countDown(); env.preparation.close();}
    }

    @Test void evictedUnconsumedCompletionIsRebuiltNotReturnedAsOldHandle() throws Exception {
        TestEnvironment env = fixture();
        ShaderMacro a = p -> {};
        try {
            Loading first = assertThrows(Loading.class,
                    () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            env.release.countDown();
            await(first);
            set(env, "ptab", new VkEnvironment.SavedProg[32]);
            env.entered = new CountDownLatch(1);
            env.release = new CountDownLatch(1);
            assertThrows(Loading.class, () -> env.getprogAsync(1, new ShaderMacro[] {a}));
            assertTrue(env.entered.await(5, TimeUnit.SECONDS));
            assertEquals(2, env.builds.get());
        } finally {env.release.countDown(); env.preparation.close();}
    }
}
