package nurgling.llm;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLlmConfigResolverTest {
    @Test
    void resolvesDefaultRelativePathsAgainstRuntimeBaseDirectory() throws Exception {
        Path baseDir = Files.createTempDirectory("llm-runtime");
        Files.createDirectories(baseDir.resolve("ai"));
        Files.write(baseDir.resolve("ai").resolve("llama-server.exe"), new byte[]{1});
        Files.write(baseDir.resolve("ai").resolve("model.gguf"), new byte[]{1});

        LocalLlmConfig cfg = new LocalLlmConfigResolver(() -> baseDir)
                .resolve(null, null, null, null, null, null);

        assertTrue(cfg.enabled);
        assertEquals(baseDir.resolve("ai").resolve("llama-server.exe"), cfg.serverPath);
        assertEquals(baseDir.resolve("ai").resolve("model.gguf"), cfg.modelPath);
        assertEquals("127.0.0.1", cfg.host);
        assertEquals(8080, cfg.port);
        assertEquals(90000L, cfg.startupTimeoutMs);
        assertTrue(cfg.isValid());
    }

    @Test
    void keepsAbsolutePathsIncludingSpaces() throws Exception {
        Path baseDir = Files.createTempDirectory("llm-runtime");
        Path server = baseDir.resolve("bin with spaces").resolve("llama-server.exe").toAbsolutePath();
        Path model = baseDir.resolve("models with spaces").resolve("model.gguf").toAbsolutePath();
        Files.createDirectories(server.getParent());
        Files.createDirectories(model.getParent());
        Files.write(server, new byte[]{1});
        Files.write(model, new byte[]{1});

        LocalLlmConfig cfg = new LocalLlmConfigResolver(() -> baseDir).resolve(
                true,
                server.toString(),
                model.toString(),
                "127.0.0.1",
                8080,
                60000L
        );

        assertEquals(server, cfg.serverPath);
        assertEquals(model, cfg.modelPath);
        assertTrue(cfg.isValid());
    }

    @Test
    void rejectsNonLoopbackHostLiteral() throws Exception {
        Path baseDir = Files.createTempDirectory("llm-runtime");
        Files.createDirectories(baseDir.resolve("ai"));
        Files.write(baseDir.resolve("ai").resolve("llama-server.exe"), new byte[]{1});
        Files.write(baseDir.resolve("ai").resolve("model.gguf"), new byte[]{1});

        LocalLlmConfig cfg = new LocalLlmConfigResolver(() -> baseDir).resolve(
                true, null, null, "0.0.0.0", null, null
        );

        assertFalse(cfg.isValid());
        assertTrue(cfg.diagnostics.stream().anyMatch(msg -> msg.contains("127.0.0.1")));
    }

    @Test
    void rejectsOutOfRangePortAndTimeout() throws Exception {
        Path baseDir = Files.createTempDirectory("llm-runtime");
        Files.createDirectories(baseDir.resolve("ai"));
        Files.write(baseDir.resolve("ai").resolve("llama-server.exe"), new byte[]{1});
        Files.write(baseDir.resolve("ai").resolve("model.gguf"), new byte[]{1});

        LocalLlmConfig cfg = new LocalLlmConfigResolver(() -> baseDir).resolve(
                true, null, null, "127.0.0.1", 70000, 0
        );

        assertFalse(cfg.isValid());
        assertTrue(cfg.diagnostics.stream().anyMatch(msg -> msg.contains("port")));
        assertTrue(cfg.diagnostics.stream().anyMatch(msg -> msg.contains("timeout")));
    }

    @Test
    void requiresRegularFilesWhenEnabled() throws Exception {
        Path baseDir = Files.createTempDirectory("llm-runtime");
        Files.createDirectories(baseDir.resolve("ai"));
        Files.write(baseDir.resolve("ai").resolve("llama-server.exe"), new byte[]{1});

        LocalLlmConfig cfg = new LocalLlmConfigResolver(() -> baseDir).resolve(
                true, null, null, null, null, null
        );

        assertFalse(cfg.isValid());
        assertTrue(cfg.diagnostics.stream().anyMatch(msg -> msg.contains("model")));
    }

    @Test
    void readsProcessGlobalKeysFromNConfig() throws Exception {
        Path baseDir = Files.createTempDirectory("llm-runtime");
        Path server = baseDir.resolve("custom").resolve("llama-server.exe");
        Path model = baseDir.resolve("custom").resolve("model.gguf");
        Files.createDirectories(server.getParent());
        Files.write(server, new byte[]{1});
        Files.write(model, new byte[]{1});

        final Map<nurgling.NConfig.Key, Object> values = new HashMap<>();
        values.put(nurgling.NConfig.Key.llmEnabled, true);
        values.put(nurgling.NConfig.Key.llmServerPath, "custom/llama-server.exe");
        values.put(nurgling.NConfig.Key.llmModelPath, "custom/model.gguf");
        values.put(nurgling.NConfig.Key.llmHost, "127.0.0.1");
        values.put(nurgling.NConfig.Key.llmPort, 18080);
        values.put(nurgling.NConfig.Key.llmStartupTimeoutMs, 61000L);

        LocalLlmConfig cfg = new LocalLlmConfigResolver(
                new LocalLlmConfigResolver.RuntimeBaseDirProvider() {
                    @Override
                    public Path get() {
                        return baseDir;
                    }
                },
                new LocalLlmConfigResolver.ConfigAccessor() {
                    @Override
                    public Object get(nurgling.NConfig.Key key) {
                        return values.get(key);
                    }
                }
        ).fromGlobalConfig();
        assertTrue(cfg.isValid());
        assertEquals(server, cfg.serverPath);
        assertEquals(model, cfg.modelPath);
        assertEquals(18080, cfg.port);
        assertEquals(61000L, cfg.startupTimeoutMs);
    }
}
