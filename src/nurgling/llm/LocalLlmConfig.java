package nurgling.llm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalLlmConfig {
    public final boolean enabled;
    public final Path serverPath;
    public final Path modelPath;
    public final String host;
    public final int port;
    public final long startupTimeoutMs;
    public final List<String> diagnostics;

    LocalLlmConfig(boolean enabled, Path serverPath, Path modelPath, String host, int port, long startupTimeoutMs,
                   List<String> diagnostics) {
        this.enabled = enabled;
        this.serverPath = serverPath;
        this.modelPath = modelPath;
        this.host = host;
        this.port = port;
        this.startupTimeoutMs = startupTimeoutMs;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public boolean isValid() {
        return diagnostics.isEmpty();
    }
}
