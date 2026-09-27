package codex.frameexport;

import java.io.Writer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** One-shot read-only attach agent; it does not change the client or its capture state. */
public final class FrameMetricsExport {
    private FrameMetricsExport() {}

    public static void agentmain(String args, Instrumentation ignored) {
        String[] paths = args == null ? new String[0] : args.split("\\|", -1);
        if (paths.length != 2)
            return;
        Path csv = Path.of(paths[0]);
        Path status = Path.of(paths[1]);
        String result;
        try {
            result = export(csv);
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? ((InvocationTargetException) error).getTargetException() : error;
            result = "ERROR|" + cause.getClass().getSimpleName() + "|"
                    + clean(cause.getMessage());
        }
        try {
            Files.writeString(status, result + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception ignoredStatusFailure) {
            // The attach helper reports a missing status file explicitly.
        }
    }

    private static String export(Path csv) throws Exception {
        if (!csv.isAbsolute())
            return "ERROR|InvalidPath|CSV path must be absolute";
        Class<?> uiClass = Class.forName("haven.UI", false,
                ClassLoader.getSystemClassLoader());
        Object ui = uiClass.getMethod("getInstance").invoke(null);
        if (ui == null)
            return "NO_UI|No active visual UI";
        Object loop = uiClass.getMethod("getLoop").invoke(ui);
        if (loop == null)
            return "NO_LOOP|Active UI has no loop";
        Object metrics = loop.getClass().getField("metrics").get(loop);
        if (metrics == null)
            return "NO_METRICS|Active loop has no frame metrics";
        Object snapshot = metrics.getClass().getMethod("snapshot").invoke(metrics);
        int size = (Integer) snapshot.getClass().getMethod("size").invoke(snapshot);
        if (size == 0) {
            boolean enabled = (Boolean) metrics.getClass().getMethod("enabled").invoke(metrics);
            return enabled ? "NO_FRAMES|Capture is enabled but has no frames yet"
                    : "DISABLED|Frame capture is disabled and has no retained frames";
        }
        String summary = (String) snapshot.getClass().getMethod("summary").invoke(snapshot);
        boolean created = false;
        try {
            try (Writer writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                snapshot.getClass().getMethod("writeCsv", Writer.class).invoke(snapshot, writer);
            }
        } catch (Exception e) {
            if (created)
                Files.deleteIfExists(csv);
            throw e;
        }
        return "OK|" + size + "|" + clean(summary);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ')
                .replace('|', '/');
    }
}
