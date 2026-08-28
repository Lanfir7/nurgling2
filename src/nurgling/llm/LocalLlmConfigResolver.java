package nurgling.llm;

import haven.Config;
import haven.Utils;
import nurgling.NConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LocalLlmConfigResolver {
    public static final String DEFAULT_SERVER_PATH = "ai/llama-server.exe";
    public static final String DEFAULT_MODEL_PATH = "ai/model.gguf";
    public static final String REQUIRED_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;
    public static final long DEFAULT_STARTUP_TIMEOUT_MS = 90000L;

    interface RuntimeBaseDirProvider {
        Path get();
    }

    interface ConfigAccessor {
        Object get(NConfig.Key key);
    }

    private final RuntimeBaseDirProvider runtimeBaseDirProvider;
    private final ConfigAccessor configAccessor;

    public LocalLlmConfigResolver() {
        this(new RuntimeBaseDirProvider() {
                 @Override
                 public Path get() {
                     return detectRuntimeBaseDir();
                 }
             },
             new ConfigAccessor() {
                 @Override
                 public Object get(NConfig.Key key) {
                     return NConfig.getGlobal(key);
                 }
             });
    }

    LocalLlmConfigResolver(RuntimeBaseDirProvider runtimeBaseDirProvider) {
        this(runtimeBaseDirProvider, new ConfigAccessor() {
            @Override
            public Object get(NConfig.Key key) {
                return NConfig.getGlobal(key);
            }
        });
    }

    LocalLlmConfigResolver(RuntimeBaseDirProvider runtimeBaseDirProvider, ConfigAccessor configAccessor) {
        this.runtimeBaseDirProvider = runtimeBaseDirProvider;
        this.configAccessor = configAccessor;
    }

    public LocalLlmConfig fromGlobalConfig() {
        return resolve(
                configAccessor.get(NConfig.Key.llmEnabled),
                configAccessor.get(NConfig.Key.llmServerPath),
                configAccessor.get(NConfig.Key.llmModelPath),
                configAccessor.get(NConfig.Key.llmHost),
                configAccessor.get(NConfig.Key.llmPort),
                configAccessor.get(NConfig.Key.llmStartupTimeoutMs)
        );
    }

    LocalLlmConfig resolve(Object enabledValue, Object serverPathValue, Object modelPathValue,
                           Object hostValue, Object portValue, Object timeoutValue) {
        boolean enabled = boolOr(enabledValue, true);
        String host = strOr(hostValue, REQUIRED_HOST);
        int port = intOr(portValue, DEFAULT_PORT);
        long timeoutMs = longOr(timeoutValue, DEFAULT_STARTUP_TIMEOUT_MS);
        Path baseDir = runtimeBaseDirProvider.get();
        Path serverPath = resolvePath(baseDir, strOr(serverPathValue, DEFAULT_SERVER_PATH));
        Path modelPath = resolvePath(baseDir, strOr(modelPathValue, DEFAULT_MODEL_PATH));

        List<String> diagnostics = new ArrayList<>();
        if (!REQUIRED_HOST.equals(host)) {
            diagnostics.add("Local LLM host must be literal " + REQUIRED_HOST + ".");
        }
        if (port < 1 || port > 65535) {
            diagnostics.add("Local LLM port must be in range 1..65535.");
        }
        if (timeoutMs <= 0) {
            diagnostics.add("Local LLM startup timeout must be positive.");
        }
        if (enabled) {
            if (!Files.isRegularFile(serverPath)) {
                diagnostics.add("Local LLM server path must be an existing regular file: " + serverPath);
            }
            if (!Files.isRegularFile(modelPath)) {
                diagnostics.add("Local LLM model path must be an existing regular file: " + modelPath);
            }
        }
        return new LocalLlmConfig(enabled, serverPath, modelPath, host, port, timeoutMs, diagnostics);
    }

    static Path resolvePath(Path baseDir, String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return baseDir.resolve(path).normalize();
    }

    static Path detectRuntimeBaseDir() {
        try {
            Path src = Utils.srcpath(Config.class);
            if (src != null) {
                Path absolute = src.toAbsolutePath().normalize();
                if (Files.isRegularFile(absolute)) {
                    Path parent = absolute.getParent();
                    if (parent != null) {
                        return parent;
                    }
                }
                if (Files.isDirectory(absolute)) {
                    return absolute;
                }
                Path parent = absolute.getParent();
                if (parent != null) {
                    return parent;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private static String strOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? fallback : str;
    }

    private static boolean boolOr(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value != null) {
            String raw = value.toString().trim();
            if ("true".equalsIgnoreCase(raw)) {
                return true;
            }
            if ("false".equalsIgnoreCase(raw)) {
                return false;
            }
        }
        return fallback;
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            String raw = value.toString().trim();
            if (!raw.isEmpty()) {
                try {
                    return Integer.parseInt(raw);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    private static long longOr(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            String raw = value.toString().trim();
            if (!raw.isEmpty()) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }
}
