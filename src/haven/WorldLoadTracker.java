package haven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Records continuous periods where MapView can only draw its loading screen. */
class WorldLoadTracker {
    private final Consumer<String> logger;
    private final Set<String> reasons = new LinkedHashSet<>();
    private long startedAt = -1;

    WorldLoadTracker(Consumer<String> logger) {
        this.logger = Objects.requireNonNull(logger);
    }

    void blocked(Loading loading, long now) {
        blocked(message(loading), origin(loading), now);
    }

    void blocked(String reason, String origin, long now) {
        String detail = normalize(reason) + " @ " + normalize(origin);
        if (startedAt < 0) {
            startedAt = now;
            reasons.clear();
            logger.accept("[WorldLoad] black screen started: " + detail);
        }
        reasons.add(detail);
    }

    void ready(long now) {
        if (startedAt < 0)
            return;
        long durationMs = Math.max(0, (now - startedAt) / 1_000_000L);
        logger.accept("[WorldLoad] world visible after " + durationMs + " ms; reasons: " +
                String.join(" -> ", reasons));
        startedAt = -1;
        reasons.clear();
    }

    static Consumer<String> fileLogger(String file) {
        Path path = Paths.get(file);
        return message -> {
            System.out.println(message);
            String line = Instant.now() + " " + message + System.lineSeparator();
            try {
                Files.write(path, line.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("[WorldLoad] cannot write " + path + ": " + e.getMessage());
            }
        };
    }

    private static String message(Loading loading) {
        String message = loading.getMessage();
        return (message == null || message.trim().isEmpty()) ? "Loading" : message;
    }

    private static String origin(Loading loading) {
        Throwable source = loading;
        while (source instanceof Loading && ((Loading) source).rec != null)
            source = ((Loading) source).rec;
        for (StackTraceElement frame : source.getStackTrace()) {
            String className = frame.getClassName();
            if (className.equals(WorldLoadTracker.class.getName()) ||
                    (className.equals(MapView.class.getName()) && frame.getMethodName().equals("draw")) ||
                    className.startsWith("java.") || className.startsWith("jdk."))
                continue;
            int dot = className.lastIndexOf('.');
            String simpleName = (dot < 0) ? className : className.substring(dot + 1);
            return simpleName + "." + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty())
            return "unknown";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
