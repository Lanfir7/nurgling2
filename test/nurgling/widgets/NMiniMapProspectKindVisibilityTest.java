package nurgling.widgets;

import haven.Resource;
import nurgling.NConfig;
import nurgling.conf.ProspectKind;
import nurgling.conf.ProspectMarkSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiniMapProspectKindVisibilityTest {
    static {
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                    Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if (stream == null)
                    throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private final NConfig previous = NConfig.current;

    @AfterEach
    void restoreCurrent() {
        NConfig.current = previous;
    }

    @Test
    void oreGemStoneDefaultVisible() {
        NConfig.current = new NConfig();
        assertTrue(NMiniMap.showProspectKind(ProspectKind.ORE));
        assertTrue(NMiniMap.showProspectKind(ProspectKind.GEM));
        assertTrue(NMiniMap.showProspectKind(ProspectKind.STONE));
    }

    @Test
    void legacyFalseHidesEvenWhenSettingsEnabled() {
        NConfig.current = new NConfig();
        settings().setEnabled(ProspectKind.ORE, true);
        settings().setEnabled(ProspectKind.GEM, true);
        settings().setEnabled(ProspectKind.STONE, true);
        NConfig.set(NConfig.Key.showOreSpotIcons, false);
        NConfig.set(NConfig.Key.showGemstoneIcons, false);
        NConfig.set(NConfig.Key.showStoneIcons, false);

        assertFalse(NMiniMap.showProspectKind(ProspectKind.ORE));
        assertFalse(NMiniMap.showProspectKind(ProspectKind.GEM));
        assertFalse(NMiniMap.showProspectKind(ProspectKind.STONE));
        assertTrue(settings().enabled(ProspectKind.ORE));
        assertTrue(settings().enabled(ProspectKind.GEM));
        assertTrue(settings().enabled(ProspectKind.STONE));
    }

    @Test
    void settingsDisabledHidesEvenWhenLegacyTrue() {
        NConfig.current = new NConfig();
        NConfig.set(NConfig.Key.showOreSpotIcons, true);
        NConfig.set(NConfig.Key.showGemstoneIcons, true);
        NConfig.set(NConfig.Key.showStoneIcons, true);
        settings().setEnabled(ProspectKind.ORE, false);
        settings().setEnabled(ProspectKind.GEM, false);
        settings().setEnabled(ProspectKind.STONE, false);

        assertFalse(NMiniMap.showProspectKind(ProspectKind.ORE));
        assertFalse(NMiniMap.showProspectKind(ProspectKind.GEM));
        assertFalse(NMiniMap.showProspectKind(ProspectKind.STONE));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showOreSpotIcons));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showGemstoneIcons));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showStoneIcons));
    }

    @Test
    void setterWritesSettingsAndMatchingLegacyKey() {
        NConfig.current = new NConfig();
        NMiniMap.showProspectKind(ProspectKind.ORE, false);
        NMiniMap.showProspectKind(ProspectKind.GEM, false);
        NMiniMap.showProspectKind(ProspectKind.STONE, false);

        assertFalse(settings().enabled(ProspectKind.ORE));
        assertFalse(settings().enabled(ProspectKind.GEM));
        assertFalse(settings().enabled(ProspectKind.STONE));
        assertEquals(Boolean.FALSE, NConfig.get(NConfig.Key.showOreSpotIcons));
        assertEquals(Boolean.FALSE, NConfig.get(NConfig.Key.showGemstoneIcons));
        assertEquals(Boolean.FALSE, NConfig.get(NConfig.Key.showStoneIcons));

        NMiniMap.showProspectKind(ProspectKind.ORE, true);
        NMiniMap.showProspectKind(ProspectKind.GEM, true);
        NMiniMap.showProspectKind(ProspectKind.STONE, true);

        assertTrue(settings().enabled(ProspectKind.ORE));
        assertTrue(settings().enabled(ProspectKind.GEM));
        assertTrue(settings().enabled(ProspectKind.STONE));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showOreSpotIcons));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showGemstoneIcons));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showStoneIcons));
    }

    @Test
    void nonMineralKindTogglesOnlySettings() {
        NConfig.current = new NConfig();
        NMiniMap.showProspectKind(ProspectKind.CLAY, false);

        assertFalse(settings().enabled(ProspectKind.CLAY));
        assertTrue(settings().enabled(ProspectKind.ORE));
        assertTrue(settings().enabled(ProspectKind.GEM));
        assertTrue(settings().enabled(ProspectKind.STONE));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showOreSpotIcons));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showGemstoneIcons));
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.showStoneIcons));
        assertFalse(NMiniMap.showProspectKind(ProspectKind.CLAY));
        assertTrue(NMiniMap.showProspectKind(ProspectKind.ORE));
    }

    private static ProspectMarkSettings settings() {
        return NMiniMap.prospectSettings();
    }
}
