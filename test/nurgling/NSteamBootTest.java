package nurgling;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
