package nurgling;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Keeps the updater from replacing client files while any GUI or headless
 * client is running. The operating system releases the shared lock on exit.
 */
public final class UpdaterLock {
    private static FileChannel channel;
    private static FileLock lock;

    private UpdaterLock() {
    }

    public static void hold() {
        Path dir = installDir().resolve("updater");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try {
            channel = FileChannel.open(dir.resolve("client.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            lock = channel.lock(0, Long.MAX_VALUE, true);
        } catch (IOException | OverlappingFileLockException e) {
            System.out.println("[UpdaterLock] could not take updater/client.lock: " + e);
        }
    }

    private static Path installDir() {
        try {
            Path jar = Paths.get(UpdaterLock.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(jar) ? jar.getParent() : Paths.get("");
        } catch (URISyntaxException | SecurityException e) {
            return Paths.get("");
        }
    }
}
