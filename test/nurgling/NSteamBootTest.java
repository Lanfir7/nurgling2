package nurgling;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NSteamBootTest {
    @Test
    void steamBootPullsFromSourceRepoRelease() {
        assertTrue(NSteamBoot.FEED.startsWith("https://raw.githubusercontent.com/Lanfir7/nurgling2/"));
        assertTrue(NSteamBoot.FEED.contains("/release"));
    }

    @Test
    void commandRunsNurglingLauncherUpdateThenHafen() {
        Path java = Path.of("java");
        Path launcher = Path.of("C:", "cache", "nurgling_launcher.jar");
        List<String> cmd = NSteamBoot.command(java, launcher);
        assertEquals("java", cmd.get(0));
        assertEquals("-jar", cmd.get(1));
        assertEquals(launcher.toString(), cmd.get(2));
        assertEquals("update", cmd.get(3));
        assertEquals(NSteamBoot.FEED, cmd.get(4));
        assertEquals("-jar", cmd.get(cmd.size() - 2));
        assertEquals("./hafen.jar", cmd.get(cmd.size() - 1));
        assertTrue(cmd.contains("--add-exports=java.desktop/sun.awt=ALL-UNNAMED"));
        assertTrue(cmd.contains("-Dhaven.authmech=steam"));
        assertTrue(cmd.contains("-XX:+UseZGC"));
        assertTrue(cmd.contains("-XX:+IgnoreUnrecognizedVMOptions"));
        assertTrue(cmd.contains("-XX:+ZGenerational"));
        assertTrue(cmd.indexOf("-XX:+UseZGC") < cmd.lastIndexOf("-jar"));
        assertTrue(cmd.indexOf("-XX:+IgnoreUnrecognizedVMOptions")
                < cmd.indexOf("-XX:+ZGenerational"));
    }

    @Test
    void clientRequiresJava21OrNewer() {
        assertFalse(NSteamBoot.supportsJava("1.8"));
        assertFalse(NSteamBoot.supportsJava("17"));
        assertFalse(NSteamBoot.supportsJava("invalid"));
        assertTrue(NSteamBoot.supportsJava("21"));
        assertTrue(NSteamBoot.supportsJava("24"));
    }

    @Test
    void statusShowsDownloadingFileFromLauncherLog() {
        assertEquals("Downloading hafen.jar",
                NSteamBoot.statusForLine("[LOG]Downloading hafen.jar -> tmp/hafen.jar"));
    }

    @Test
    void statusShowsCheckAndStart() {
        assertEquals("Checking for updates...", NSteamBoot.statusForLine("[LOG]Comparing version files"));
        assertEquals("Already up to date", NSteamBoot.statusForLine("[LOG]No update required"));
        assertEquals("Starting...", NSteamBoot.statusForLine("[LOG]Starting client"));
    }

    @Test
    void hideSplashWhenClientStarts() {
        assertTrue(NSteamBoot.isClientStarting("[LOG]Starting client"));
        assertFalse(NSteamBoot.isClientStarting("[LOG]Downloading hafen.jar -> x"));
    }
}
