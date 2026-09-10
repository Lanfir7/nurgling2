package nurgling.widgets.nsettings;

import haven.Coord;
import haven.Widget;
import nurgling.NConfig;
import nurgling.i18n.L10n;
import nurgling.widgets.ChunkNavVisualizerWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeSetupChunkNavVisualizerTest {
    static {
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    java.nio.file.Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                    java.nio.file.Paths.get("bin", "hafen-res.jar").toUri().toURL(),
                    java.nio.file.Paths.get("bin", "nurgling-res.jar").toUri().toURL()});
            haven.Resource.local().add(new haven.Resource.FileSource(
                    java.nio.file.Paths.get("resources", "compiled", "res")));
            haven.Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if (stream == null)
                    throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private NConfig previousConfig;

    @BeforeEach
    void installConfig() {
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach
    void restoreConfig() {
        NConfig.current = previousConfig;
    }

    @Test
    void chunkNavButtonBottomFitsWithinHomeSetupHeight() {
        HomeSetup setup = new HomeSetup(() -> new Widget());
        int buttonBottom = setup.chunkNav.c.y + setup.chunkNav.sz.y;
        assertTrue(buttonBottom <= setup.sz.y,
                "chunkNav bottom " + buttonBottom + " exceeds HomeSetup height " + setup.sz.y);
    }

    @Test
    void clickingChunkNavButtonAddsCenteredVisualizerUnderInjectedRoot() {
        Widget root = new Widget(Coord.of(2000, 1500));
        root.setfocusctl(true);
        HomeSetup setup = new HomeSetup(() -> root);

        setup.chunkNav.click();

        Widget opened = null;
        int childCount = 0;
        for (Widget child : root.children()) {
            childCount++;
            opened = child;
        }
        assertEquals(1, childCount);
        assertNotNull(opened);
        assertEquals(ChunkNavVisualizerWindow.class, opened.getClass());
        assertEquals(root.sz.sub(opened.sz).div(2).max(Coord.z), opened.c);
    }

    @Test
    void chunkNavMapLabelIsLocalizedInEnglishAndRussian() {
        String previous = L10n.getLanguage();
        try {
            L10n.setLanguage("en");
            String en = L10n.get("world.home.chunknav_map");
            assertFalse(en.trim().isEmpty());
            assertNotEquals("world.home.chunknav_map", en);

            L10n.setLanguage("ru");
            String ru = L10n.get("world.home.chunknav_map");
            assertFalse(ru.trim().isEmpty());
            assertNotEquals("world.home.chunknav_map", ru);
            assertNotEquals(en, ru);
        } finally {
            L10n.setLanguage(previous);
        }
    }
}
