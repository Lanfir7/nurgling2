package nurgling.llm;

import haven.error.FileLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-global lifecycle owner for local llama.cpp server process.
 */
public class LocalLlmManager {
    interface ConfigProvider {
        LocalLlmConfig resolve();
    }

    interface ProcessStarter {
        Process start(List<String> command) throws Exception;
    }

    interface HealthProbe {
        int getHealthStatus(URI endpoint) throws Exception;
    }

    interface PortProbe {
        boolean isPortAvailable(String host, int port);
    }

    interface LifecycleLogger {
        void info(String message);

        void error(String message, Throwable error);
    }

    interface ShutdownHookInstaller {
        void install(String hookName, Runnable hook);
    }

    private static final long DEFAULT_POLL_DELAY_MS = 250L;
    private static final long DEFAULT_STOP_GRACE_MS = 1500L;
    private static final int DIAGNOSTIC_TAIL_LINES = 40;

    private final Object lock = new Object();
    private final ExecutorService startupExecutor;
    private final ConfigProvider configProvider;
    private final ProcessStarter processStarter;
    private final HealthProbe healthProbe;
    private final PortProbe portProbe;
    private final LifecycleLogger logger;
    private final ShutdownHookInstaller shutdownHookInstaller;
    private final boolean registerShutdownHook;
    private final long pollDelayMs;
    private final long stopGraceMs;
    private final Deque<String> outputTail = new ArrayDeque<>();
    private final AtomicInteger drainerSeq = new AtomicInteger(0);
    private final AtomicInteger hookSeq = new AtomicInteger(0);

    private volatile LocalLlmState state = LocalLlmState.STOPPED;
    private volatile Process process;
    private volatile URI endpoint;
    private volatile long generation = 0L;

    public LocalLlmManager() {
        this(new ConfigProvider() {
                 private final LocalLlmConfigResolver resolver = new LocalLlmConfigResolver();

                 @Override
                 public LocalLlmConfig resolve() {
                     return resolver.fromGlobalConfig();
                 }
             },
             command -> new ProcessBuilder(command).start(),
             LocalLlmManager::probeHealth,
             LocalLlmManager::isPortAvailable,
             DEFAULT_POLL_DELAY_MS,
             DEFAULT_STOP_GRACE_MS,
             defaultLifecycleLogger(),
             defaultShutdownHookInstaller(),
             true);
    }

    LocalLlmManager(ConfigProvider configProvider,
                    ProcessStarter processStarter,
                    HealthProbe healthProbe,
                    PortProbe portProbe,
                    long pollDelayMs,
                    long stopGraceMs) {
        this(configProvider,
             processStarter,
             healthProbe,
             portProbe,
             pollDelayMs,
             stopGraceMs,
             noOpLifecycleLogger(),
             noOpShutdownHookInstaller(),
             false);
    }

    LocalLlmManager(ConfigProvider configProvider,
                    ProcessStarter processStarter,
                    HealthProbe healthProbe,
                    PortProbe portProbe,
                    long pollDelayMs,
                    long stopGraceMs,
                    LifecycleLogger logger,
                    ShutdownHookInstaller shutdownHookInstaller,
                    boolean registerShutdownHook) {
        this.configProvider = configProvider;
        this.processStarter = processStarter;
        this.healthProbe = healthProbe;
        this.portProbe = portProbe;
        this.logger = logger;
        this.shutdownHookInstaller = shutdownHookInstaller;
        this.registerShutdownHook = registerShutdownHook;
        this.pollDelayMs = pollDelayMs;
        this.stopGraceMs = stopGraceMs;
        this.startupExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "LocalLlm-Startup");
                thread.setDaemon(true);
                return thread;
            }
        });
        if (this.registerShutdownHook) {
            registerShutdownHook();
        }
    }

    public void start() {
        final long localGeneration;
        synchronized (lock) {
            if (state == LocalLlmState.STARTING || state == LocalLlmState.READY || state == LocalLlmState.STOPPING) {
                return;
            }
            generation++;
            localGeneration = generation;
            state = LocalLlmState.STARTING;
            endpoint = null;
            process = null;
            outputTail.clear();
        }
        logger.info("[LocalLlm] starting");
        try {
            startupExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    startInternal(localGeneration);
                }
            });
        } catch (RejectedExecutionException e) {
            fail(localGeneration, "failure", "startup-rejected", "startup executor rejected task", e, null);
        }
    }

    public void stop() {
        Process toStop;
        synchronized (lock) {
            if (state == LocalLlmState.STOPPED) {
                return;
            }
            generation++;
            state = LocalLlmState.STOPPING;
            toStop = process;
            process = null;
            endpoint = null;
        }
        logger.info("[LocalLlm] stopping");
        stopProcess(toStop);
        synchronized (lock) {
            state = LocalLlmState.STOPPED;
            outputTail.clear();
        }
        logger.info("[LocalLlm] stopped");
    }

    public boolean isAvailable() {
        return state == LocalLlmState.READY;
    }

    public boolean isReady() {
        return state == LocalLlmState.READY;
    }

    public Optional<URI> getEndpoint() {
        URI current = endpoint;
        return current == null ? Optional.<URI>empty() : Optional.of(current);
    }

    public LocalLlmState getState() {
        return state;
    }

    private void startInternal(long localGeneration) {
        LocalLlmConfig cfg;
        try {
            cfg = configProvider.resolve();
        } catch (RuntimeException e) {
            fail(localGeneration, "failure", "config-resolve", "failed to resolve config", e, null);
            return;
        }
        if (cfg == null) {
            fail(localGeneration, "failure", "config-null", "resolved config is null", null, null);
            return;
        }
        logger.info("[LocalLlm] config enabled=" + cfg.enabled
                + " server=" + cfg.serverPath
                + " model=" + cfg.modelPath);
        if (!cfg.enabled) {
            synchronized (lock) {
                if (generation == localGeneration) {
                    state = LocalLlmState.STOPPED;
                    endpoint = null;
                    process = null;
                }
            }
            logger.info("[LocalLlm] disabled");
            return;
        }
        if (!cfg.isValid()) {
            fail(localGeneration, "unavailable", "invalid-config", firstDiagnostic(cfg), null, null);
            return;
        }
        if (!portProbe.isPortAvailable(cfg.host, cfg.port)) {
            fail(localGeneration, "unavailable", "port-busy", "port is busy on loopback: " + cfg.port, null, null);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(cfg.serverPath.toString());
        command.add("--model");
        command.add(cfg.modelPath.toString());
        command.add("--host");
        command.add(cfg.host);
        command.add("--port");
        command.add(String.valueOf(cfg.port));

        Process launched;
        try {
            launched = processStarter.start(command);
        } catch (Exception e) {
            fail(localGeneration, "failure", "launch-error", "process launch failed", e, null);
            return;
        }
        URI baseEndpoint = URI.create("http://" + cfg.host + ":" + cfg.port);
        synchronized (lock) {
            if (generation != localGeneration || state != LocalLlmState.STARTING) {
                stopProcess(launched);
                return;
            }
            process = launched;
            endpoint = baseEndpoint;
            startDrainers(launched, localGeneration);
        }

        waitForReady(localGeneration, cfg, launched, baseEndpoint);
    }

    private void waitForReady(long localGeneration, LocalLlmConfig cfg, Process launched, URI baseEndpoint) {
        long startedAt = System.currentTimeMillis();
        URI healthEndpoint = baseEndpoint.resolve("/health");
        while (true) {
            if (!isGenerationActive(localGeneration)) {
                return;
            }
            if (!launched.isAlive()) {
                fail(localGeneration, "failure", "child-exited",
                        "child exited before readiness (code " + safeExitCode(launched) + ")", null, launched);
                return;
            }
            if (System.currentTimeMillis() - startedAt >= cfg.startupTimeoutMs) {
                fail(localGeneration, "failure", "startup-timeout",
                        "startup timeout after " + cfg.startupTimeoutMs + " ms", null, launched);
                return;
            }

            try {
                int status = healthProbe.getHealthStatus(healthEndpoint);
                if (status == 200) {
                    synchronized (lock) {
                        if (generation != localGeneration || state != LocalLlmState.STARTING) {
                            return;
                        }
                        state = LocalLlmState.READY;
                    }
                    logger.info("[LocalLlm] ready endpoint=" + baseEndpoint);
                    return;
                }
                if (status != 503) {
                    fail(localGeneration, "failure", "health-status", "unexpected /health status " + status, null, launched);
                    return;
                }
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(pollDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(localGeneration, "failure", "startup-interrupted", "startup polling interrupted", e, launched);
                return;
            }
        }
    }

    private void fail(long localGeneration,
                      String category,
                      String code,
                      String detail,
                      Throwable error,
                      Process failingProcess) {
        if (!isGenerationActive(localGeneration)) {
            return;
        }
        stopProcess(failingProcess);
        StringBuilder msg = new StringBuilder("[LocalLlm] ")
                .append(category)
                .append(": ")
                .append(code);
        if (detail != null && !detail.isEmpty()) {
            msg.append(" (").append(detail).append(")");
        }
        String tail = lastTailLine();
        if (tail != null && !tail.isEmpty()) {
            msg.append(" | tail: ").append(tail);
        }
        if (error != null) {
            logger.error(msg.toString(), error);
        } else {
            logger.info(msg.toString());
        }
        synchronized (lock) {
            if (generation != localGeneration) {
                return;
            }
            process = null;
            endpoint = null;
            state = LocalLlmState.FAILED;
        }
    }

    private boolean isGenerationActive(long localGeneration) {
        synchronized (lock) {
            return generation == localGeneration && state == LocalLlmState.STARTING;
        }
    }

    private void stopProcess(Process proc) {
        if (proc == null) {
            return;
        }
        try {
            proc.destroy();
            if (!proc.waitFor(stopGraceMs, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                proc.waitFor(stopGraceMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
        } catch (RuntimeException ignored) {
        }
    }

    private String firstDiagnostic(LocalLlmConfig cfg) {
        if (cfg.diagnostics.isEmpty()) {
            return "unknown config validation error";
        }
        return cfg.diagnostics.get(0);
    }

    private void startDrainers(Process proc, long localGeneration) {
        startDrainer(proc.getInputStream(), "stdout", localGeneration);
        startDrainer(proc.getErrorStream(), "stderr", localGeneration);
    }

    private void startDrainer(final InputStream input, final String streamTag, final long localGeneration) {
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                drainStream(input, streamTag, localGeneration);
            }
        }, "LocalLlm-Drain-" + streamTag + "-" + drainerSeq.incrementAndGet());
        drainer.setDaemon(true);
        drainer.start();
    }

    private void drainStream(InputStream input, String streamTag, long localGeneration) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (lock) {
                    if (generation != localGeneration) {
                        return;
                    }
                    outputTail.addLast(streamTag + ": " + line);
                    while (outputTail.size() > DIAGNOSTIC_TAIL_LINES) {
                        outputTail.removeFirst();
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String lastTailLine() {
        synchronized (lock) {
            return outputTail.peekLast();
        }
    }

    private void registerShutdownHook() {
        try {
            shutdownHookInstaller.install("LocalLlm-Shutdown-" + hookSeq.incrementAndGet(), new Runnable() {
                @Override
                public void run() {
                    stop();
                }
            });
        } catch (IllegalStateException ignored) {
        } catch (SecurityException e) {
            logger.error("[LocalLlm] failed to register shutdown hook", e);
        }
    }

    private static LifecycleLogger defaultLifecycleLogger() {
        return new LifecycleLogger() {
            @Override
            public void info(String message) {
                FileLogger.log(message);
            }

            @Override
            public void error(String message, Throwable error) {
                FileLogger.logError(message, error);
            }
        };
    }

    private static ShutdownHookInstaller defaultShutdownHookInstaller() {
        return new ShutdownHookInstaller() {
            @Override
            public void install(String hookName, Runnable hook) {
                Runtime.getRuntime().addShutdownHook(new Thread(hook, hookName));
            }
        };
    }

    private static LifecycleLogger noOpLifecycleLogger() {
        return new LifecycleLogger() {
            @Override
            public void info(String message) {
            }

            @Override
            public void error(String message, Throwable error) {
            }
        };
    }

    private static ShutdownHookInstaller noOpShutdownHookInstaller() {
        return new ShutdownHookInstaller() {
            @Override
            public void install(String hookName, Runnable hook) {
            }
        };
    }

    private static int probeHealth(URI endpoint) throws IOException {
        HttpURLConnection con = (HttpURLConnection) endpoint.toURL().openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(300);
        con.setReadTimeout(300);
        con.setUseCaches(false);
        con.setDoInput(true);
        try {
            return con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }

    private static boolean isPortAvailable(String host, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName(host), port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int safeExitCode(Process proc) {
        try {
            return proc.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return -1;
        }
    }
}
