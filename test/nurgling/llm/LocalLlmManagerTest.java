package nurgling.llm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLlmManagerTest {
    @Test
    void startIsNonBlockingAndBecomesReadyOnlyAfterHealth200() throws Exception {
        FakeReadiness readiness = new FakeReadiness(503, 503, 200);
        FakeProcess process = new FakeProcess(true);
        FakeProcessStarter starter = new FakeProcessStarter(process);
        LocalLlmManager manager = newManager(starter, readiness, true, 2000L);

        long startedAt = System.currentTimeMillis();
        manager.start();
        long elapsed = System.currentTimeMillis() - startedAt;

        assertTrue(elapsed < 200L);
        waitForState(manager, LocalLlmState.READY, 2000L);
        assertTrue(manager.isReady());
        assertTrue(manager.isAvailable());
        assertEquals(Optional.of(new URI("http://127.0.0.1:8080")), manager.getEndpoint());
        assertEquals(3, readiness.calls.get());
        manager.stop();
    }

    @Test
    void duplicateAndConcurrentStartCreateSingleChild() throws Exception {
        FakeReadiness readiness = new FakeReadiness(200);
        FakeProcessStarter starter = new FakeProcessStarter(new FakeProcess(true));
        LocalLlmManager manager = newManager(starter, readiness, true, 2000L);
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 8; i++) {
                callers.submit(manager::start);
            }
        } finally {
            callers.shutdown();
            assertTrue(callers.awaitTermination(2, TimeUnit.SECONDS));
        }

        waitForState(manager, LocalLlmState.READY, 2000L);
        assertEquals(1, starter.starts.get());
        manager.stop();
    }

    @Test
    void earlyExitTransitionsToFailedWithoutThrowing() throws Exception {
        FakeProcess process = new FakeProcess(false);
        process.exitCode = 17;
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), new FakeReadiness(503, 503), true, 500L);

        assertDoesNotThrow(manager::start);
        waitForState(manager, LocalLlmState.FAILED, 1500L);
        assertFalse(manager.isAvailable());
        assertFalse(manager.isReady());
    }

    @Test
    void startupTimeoutTransitionsToFailed() throws Exception {
        FakeProcess process = new FakeProcess(true);
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), new FakeReadiness(503, 503, 503, 503), true, 120L);

        manager.start();
        waitForState(manager, LocalLlmState.FAILED, 2000L);
        assertTrue(process.destroyCalled.get() > 0);
    }

    @Test
    void unexpectedHealthStatusFailsImmediately() throws Exception {
        FakeProcess process = new FakeProcess(true);
        FakeReadiness readiness = new FakeReadiness(500, 500, 500);
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), readiness, true, 2000L);

        manager.start();
        waitForState(manager, LocalLlmState.FAILED, 1000L);
        assertEquals(1, readiness.calls.get());
        assertTrue(process.destroyCalled.get() > 0);
    }

    @Test
    void unavailablePortFailsWithoutLaunchingProcess() throws Exception {
        FakeProcessStarter starter = new FakeProcessStarter(new FakeProcess(true));
        LocalLlmManager manager = newManager(starter, new FakeReadiness(200), false, 2000L);

        manager.start();
        waitForState(manager, LocalLlmState.FAILED, 1000L);
        assertEquals(0, starter.starts.get());
    }

    @Test
    void stopGracefulThenForcedFallback() throws Exception {
        FakeProcess process = new FakeProcess(true);
        process.waitReturns = false;
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), new FakeReadiness(200), true, 2000L);
        manager.start();
        waitForState(manager, LocalLlmState.READY, 2000L);

        manager.stop();
        assertEquals(LocalLlmState.STOPPED, manager.getState());
        assertTrue(process.destroyCalled.get() > 0);
        assertTrue(process.forceDestroyCalled.get() > 0);
    }

    @Test
    void stopGracefulPathDoesNotForceKill() throws Exception {
        FakeProcess process = new FakeProcess(true);
        process.waitReturns = true;
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), new FakeReadiness(200), true, 2000L);
        manager.start();
        waitForState(manager, LocalLlmState.READY, 2000L);

        manager.stop();
        assertEquals(LocalLlmState.STOPPED, manager.getState());
        assertTrue(process.destroyCalled.get() > 0);
        assertEquals(0, process.forceDestroyCalled.get());
    }

    @Test
    void stopIsIdempotentAndClearsEndpoint() throws Exception {
        FakeProcess process = new FakeProcess(true);
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), new FakeReadiness(200), true, 2000L);
        manager.start();
        waitForState(manager, LocalLlmState.READY, 2000L);

        manager.stop();
        manager.stop();

        assertEquals(LocalLlmState.STOPPED, manager.getState());
        assertEquals(Optional.empty(), manager.getEndpoint());
    }

    @Test
    void lateReadinessCannotResurrectStoppedManager() throws Exception {
        CountDownLatch allowReady = new CountDownLatch(1);
        FakeReadiness readiness = new FakeReadiness(allowReady, Arrays.asList(503, 200));
        FakeProcess process = new FakeProcess(true);
        LocalLlmManager manager = newManager(new FakeProcessStarter(process), readiness, true, 2000L);

        manager.start();
        waitForState(manager, LocalLlmState.STARTING, 1000L);
        manager.stop();
        allowReady.countDown();
        Thread.sleep(120L);

        assertEquals(LocalLlmState.STOPPED, manager.getState());
        assertFalse(manager.isReady());
        assertFalse(manager.isAvailable());
    }

    @Test
    void launchFailureNeverEscapesToCaller() throws Exception {
        FakeProcessStarter starter = new FakeProcessStarter(null);
        starter.startException = new RuntimeException("boom");
        LocalLlmManager manager = newManager(starter, new FakeReadiness(200), true, 1000L);

        assertDoesNotThrow(manager::start);
        waitForState(manager, LocalLlmState.FAILED, 1500L);
    }

    @Test
    void invalidConfigFailsWithoutThrowingOrLaunching() throws Exception {
        Path base = Files.createTempDirectory("llm-invalid");
        LocalLlmConfig cfg = new LocalLlmConfig(true,
                base.resolve("missing-server.exe"),
                base.resolve("missing-model.gguf"),
                "127.0.0.1",
                8080,
                1000L,
                Arrays.asList("missing files"));
        FakeProcessStarter starter = new FakeProcessStarter(new FakeProcess(true));
        LocalLlmManager manager = new LocalLlmManager(
                () -> cfg,
                starter,
                new FakeReadiness(200),
                (host, port) -> true,
                20L,
                50L
        );
        assertDoesNotThrow(manager::start);
        waitForState(manager, LocalLlmState.FAILED, 1000L);
        assertEquals(0, starter.starts.get());
    }

    @Test
    void commandContainsExpectedArguments() throws Exception {
        Path base = Files.createTempDirectory("llm-manager-cmd");
        Path server = base.resolve("server path").resolve("llama-server.exe");
        Path model = base.resolve("model path").resolve("model.gguf");
        Files.createDirectories(server.getParent());
        Files.createDirectories(model.getParent());
        Files.write(server, new byte[]{1});
        Files.write(model, new byte[]{1});
        LocalLlmConfig cfg = new LocalLlmConfig(true, server, model, "127.0.0.1", 19090, 2000L, Collections.<String>emptyList());
        FakeProcessStarter starter = new FakeProcessStarter(new FakeProcess(true));
        LocalLlmManager manager = new LocalLlmManager(
                () -> cfg,
                starter,
                new FakeReadiness(200),
                (host, port) -> true,
                30L,
                30L
        );

        manager.start();
        waitForState(manager, LocalLlmState.READY, 2000L);
        assertEquals(Arrays.asList(
                server.toString(),
                "--model",
                model.toString(),
                "--host",
                "127.0.0.1",
                "--port",
                "19090"
        ), starter.lastCommand);
        manager.stop();
    }

    @Test
    void diagnosticsIncludeEnabledPathsStartingAndReadyEndpoint() throws Exception {
        Path base = Files.createTempDirectory("llm-manager-diagnostics");
        Path server = base.resolve("ai").resolve("llama-server.exe");
        Path model = base.resolve("ai").resolve("model.gguf");
        Files.createDirectories(server.getParent());
        Files.write(server, new byte[]{1});
        Files.write(model, new byte[]{1});
        LocalLlmConfig cfg = new LocalLlmConfig(true, server, model, "127.0.0.1", 28080, 1000L,
                Collections.<String>emptyList());
        FakeProcessStarter starter = new FakeProcessStarter(new FakeProcess(true));
        CapturingDiagnostics diagnostics = new CapturingDiagnostics();
        LocalLlmManager manager = new LocalLlmManager(
                () -> cfg,
                starter,
                new FakeReadiness(200),
                (host, port) -> true,
                20L,
                50L,
                diagnostics,
                diagnostics,
                false
        );

        manager.start();
        waitForState(manager, LocalLlmState.READY, 1500L);

        assertTrue(diagnostics.hasInfoContaining("enabled"));
        assertTrue(diagnostics.hasInfoContaining(server.toString()));
        assertTrue(diagnostics.hasInfoContaining(model.toString()));
        assertTrue(diagnostics.hasInfoContaining("starting"));
        assertTrue(diagnostics.hasInfoContaining("ready endpoint=http://127.0.0.1:28080"));
        manager.stop();
    }

    @Test
    void diagnosticsLogDisabledAndNormalizedUnavailableReason() throws Exception {
        Path base = Files.createTempDirectory("llm-manager-disabled");
        Path server = base.resolve("ai").resolve("llama-server.exe");
        Path model = base.resolve("ai").resolve("model.gguf");
        LocalLlmConfig disabledCfg = new LocalLlmConfig(false, server, model, "127.0.0.1", 8080, 1000L,
                Collections.<String>emptyList());
        CapturingDiagnostics disabledDiagnostics = new CapturingDiagnostics();
        LocalLlmManager disabledManager = new LocalLlmManager(
                () -> disabledCfg,
                new FakeProcessStarter(new FakeProcess(true)),
                new FakeReadiness(200),
                (host, port) -> true,
                20L,
                50L,
                disabledDiagnostics,
                disabledDiagnostics,
                false
        );
        disabledManager.start();
        waitForState(disabledManager, LocalLlmState.STOPPED, 1000L);
        assertTrue(disabledDiagnostics.hasInfoContaining("disabled"));
        assertTrue(disabledDiagnostics.hasInfoContaining(server.toString()));
        assertTrue(disabledDiagnostics.hasInfoContaining(model.toString()));

        CapturingDiagnostics unavailableDiagnostics = new CapturingDiagnostics();
        LocalLlmManager unavailableManager = newManagerWithDiagnostics(
                new FakeProcessStarter(new FakeProcess(true)),
                new FakeReadiness(200),
                false,
                1000L,
                unavailableDiagnostics,
                false
        );
        unavailableManager.start();
        waitForState(unavailableManager, LocalLlmState.FAILED, 1500L);
        assertTrue(unavailableDiagnostics.hasInfoContaining("unavailable: port-busy"));
    }

    @Test
    void diagnosticsLogNormalizedFailureReasonOnLaunchException() throws Exception {
        FakeProcessStarter starter = new FakeProcessStarter(null);
        starter.startException = new RuntimeException("boom");
        CapturingDiagnostics diagnostics = new CapturingDiagnostics();
        LocalLlmManager manager = newManagerWithDiagnostics(
                starter,
                new FakeReadiness(200),
                true,
                1000L,
                diagnostics,
                false
        );

        manager.start();
        waitForState(manager, LocalLlmState.FAILED, 1500L);
        assertTrue(diagnostics.hasErrorContaining("failure: launch-error"));
    }

    @Test
    void injectedManagerCanSkipShutdownHookInstallation() throws Exception {
        CapturingDiagnostics diagnostics = new CapturingDiagnostics();
        newManagerWithDiagnostics(
                new FakeProcessStarter(new FakeProcess(true)),
                new FakeReadiness(200),
                true,
                1000L,
                diagnostics,
                false
        );
        assertEquals(0, diagnostics.hookRegistrations.get());
    }

    @Test
    void injectedManagerRegistersExactlyOneShutdownHookWhenEnabled() throws Exception {
        CapturingDiagnostics diagnostics = new CapturingDiagnostics();
        newManagerWithDiagnostics(
                new FakeProcessStarter(new FakeProcess(true)),
                new FakeReadiness(200),
                true,
                1000L,
                diagnostics,
                true
        );
        assertEquals(1, diagnostics.hookRegistrations.get());
    }

    private static LocalLlmManager newManager(FakeProcessStarter starter, FakeReadiness readiness,
                                              boolean portFree, long startupTimeoutMs) throws Exception {
        Path base = Files.createTempDirectory("llm-manager");
        Path server = base.resolve("ai").resolve("llama-server.exe");
        Path model = base.resolve("ai").resolve("model.gguf");
        Files.createDirectories(server.getParent());
        Files.write(server, new byte[]{1});
        Files.write(model, new byte[]{1});
        LocalLlmConfig cfg = new LocalLlmConfig(true, server, model, "127.0.0.1", 8080, startupTimeoutMs,
                Collections.<String>emptyList());
        return new LocalLlmManager(
                () -> cfg,
                starter,
                readiness,
                (host, port) -> portFree,
                20L,
                50L
        );
    }

    private static LocalLlmManager newManagerWithDiagnostics(FakeProcessStarter starter,
                                                              FakeReadiness readiness,
                                                              boolean portFree,
                                                              long startupTimeoutMs,
                                                              CapturingDiagnostics diagnostics,
                                                              boolean registerShutdownHook) throws Exception {
        Path base = Files.createTempDirectory("llm-manager-diag");
        Path server = base.resolve("ai").resolve("llama-server.exe");
        Path model = base.resolve("ai").resolve("model.gguf");
        Files.createDirectories(server.getParent());
        Files.write(server, new byte[]{1});
        Files.write(model, new byte[]{1});
        LocalLlmConfig cfg = new LocalLlmConfig(
                true,
                server,
                model,
                "127.0.0.1",
                8080,
                startupTimeoutMs,
                Collections.<String>emptyList()
        );
        return new LocalLlmManager(
                () -> cfg,
                starter,
                readiness,
                (host, port) -> portFree,
                20L,
                50L,
                diagnostics,
                diagnostics,
                registerShutdownHook
        );
    }

    private static void waitForState(LocalLlmManager manager, LocalLlmState expected, long timeoutMs)
            throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (manager.getState() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, manager.getState());
    }

    private static final class FakeReadiness implements LocalLlmManager.HealthProbe {
        private final CountDownLatch latch;
        private final List<Integer> codes;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeReadiness(int... statuses) {
            this(null, toList(statuses));
        }

        private FakeReadiness(CountDownLatch latch, List<Integer> codes) {
            this.latch = latch;
            this.codes = codes;
        }

        @Override
        public int getHealthStatus(URI endpoint) throws Exception {
            if (latch != null) {
                latch.await(1, TimeUnit.SECONDS);
            }
            int idx = calls.getAndIncrement();
            if (idx >= codes.size()) {
                return codes.get(codes.size() - 1);
            }
            return codes.get(idx);
        }

        private static List<Integer> toList(int[] statuses) {
            Integer[] boxed = new Integer[statuses.length];
            for (int i = 0; i < statuses.length; i++) {
                boxed[i] = statuses[i];
            }
            return Arrays.asList(boxed);
        }
    }

    private static final class FakeProcessStarter implements LocalLlmManager.ProcessStarter {
        private final FakeProcess process;
        private final AtomicInteger starts = new AtomicInteger();
        private volatile RuntimeException startException;
        private volatile List<String> lastCommand = Collections.emptyList();

        private FakeProcessStarter(FakeProcess process) {
            this.process = process;
        }

        @Override
        public Process start(List<String> command) {
            starts.incrementAndGet();
            lastCommand = command;
            if (startException != null) {
                throw startException;
            }
            return process;
        }
    }

    private static final class FakeProcess extends Process {
        private final AtomicInteger destroyCalled = new AtomicInteger();
        private final AtomicInteger forceDestroyCalled = new AtomicInteger();
        private volatile boolean alive;
        private volatile int exitCode = 0;
        private volatile boolean waitReturns = true;

        private FakeProcess(boolean alive) {
            this.alive = alive;
        }

        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            while (alive) {
                Thread.sleep(5L);
            }
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (waitReturns) {
                alive = false;
                return true;
            }
            return false;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("still alive");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyCalled.incrementAndGet();
            if (waitReturns) {
                alive = false;
            }
        }

        @Override
        public Process destroyForcibly() {
            forceDestroyCalled.incrementAndGet();
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }

    private static final class CapturingDiagnostics
            implements LocalLlmManager.LifecycleLogger, LocalLlmManager.ShutdownHookInstaller {
        private final List<String> infos = new CopyOnWriteArrayList<>();
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final AtomicInteger hookRegistrations = new AtomicInteger();

        @Override
        public void info(String message) {
            infos.add(message);
        }

        @Override
        public void error(String message, Throwable error) {
            errors.add(message + " | " + (error == null ? "" : error.getClass().getSimpleName()));
        }

        @Override
        public void install(String hookName, Runnable hook) {
            hookRegistrations.incrementAndGet();
        }

        private boolean hasInfoContaining(String expected) {
            for (String msg : infos) {
                if (msg.contains(expected)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasErrorContaining(String expected) {
            for (String msg : errors) {
                if (msg.contains(expected)) {
                    return true;
                }
            }
            return false;
        }
    }
}
