package nurgling;

import haven.WorkshopLauncher;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Steam Workshop entry: pull the latest client from GitHub, then launch it.
 * Keeps files in {@code ~/NurglingEvolution} so Steam does not overwrite them.
 */
public final class NSteamBoot {
    public static final String FEED = NUpdateFeed.SOURCE_RELEASE_DIR;
    public static final String APP_ID = "3051280";

    private NSteamBoot() {}

    public static Path cacheDir() {
        return Paths.get(System.getProperty("user.home"), "NurglingEvolution");
    }

    public static List<String> command(Path java, Path launcher) {
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-jar");
        cmd.add(launcher.toString());
        cmd.add("update");
        cmd.add(FEED);
        cmd.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED");
        cmd.add("-Dsun.java2d.uiScale.enabled=false");
        cmd.add("-Dhaven.authmech=steam");
        cmd.add("-jar");
        cmd.add("./hafen.jar");
        return cmd;
    }

    public static void main(String[] args) throws Exception {
        Path cache = cacheDir();
        Files.createDirectories(cache);
        Path launcher = cache.resolve("nurgling_launcher.jar");
        Path bundled = bundledLauncher();
        if (bundled != null && Files.isRegularFile(bundled))
            Files.copy(bundled, launcher, StandardCopyOption.REPLACE_EXISTING);
        if (!Files.isRegularFile(launcher))
            throw new IOException("nurgling_launcher.jar missing in workshop item and cache");
        Files.writeString(cache.resolve("steam_appid.txt"), APP_ID);

        Path java;
        try {
            java = WorkshopLauncher.findjvm();
        } catch (IOException e) {
            java = Path.of("java");
        }
        ProcessBuilder pb = new ProcessBuilder(command(java, launcher));
        pb.directory(cache.toFile());
        pb.inheritIO();
        pb.environment().put("SteamAppID", APP_ID);
        System.exit(pb.start().waitFor());
    }

    static Path bundledLauncher() {
        try {
            URI loc = NSteamBoot.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Paths.get(loc);
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            return dir.resolve("nurgling_launcher.jar");
        } catch (Exception e) {
            return null;
        }
    }
}
