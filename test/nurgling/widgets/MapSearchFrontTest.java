package nurgling.widgets;

import haven.Coord;
import haven.Widget;
import nurgling.actions.bots.MasterMiner;
import nurgling.conf.ProspectKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSearchFrontTest {

    @Test
    void deferredRaiseWinsOverParentStyleRaise() {
        Widget parent = new Widget();
        Widget map = parent.add(new Widget());
        Widget search = parent.add(new Widget());

        MapSearchFront.showInFront(search);
        assertEquals(search, parent.lchild, "immediate raise should put search last");

        map.raise();
        assertEquals(map, parent.lchild, "map mousedown raise covers search until the deferred tick");

        search.tick(0.016);
        assertEquals(search, parent.lchild, "deferred raise must put search last among same-z siblings");
    }

    @Test
    void laterMapRaiseStillWorks() {
        Widget parent = new Widget();
        Widget map = parent.add(new Widget());
        Widget search = parent.add(new Widget());

        MapSearchFront.showInFront(search);
        map.raise();
        search.tick(0.016);
        assertEquals(search, parent.lchild);

        map.raise();
        assertEquals(map, parent.lchild, "clicking the map later must still raise it");
    }

    @Test
    void hiddenWindowIsNotRaisedOnDeferredTick() {
        Widget parent = new Widget();
        Widget map = parent.add(new Widget());
        Widget search = parent.add(new Widget());

        MapSearchFront.showInFront(search);
        search.hide();
        map.raise();
        search.tick(0.016);

        assertEquals(map, parent.lchild);
    }

    @Test
    void mapToolbarOpenersCallShowInFront() throws Exception {
        String nmap = read("src/nurgling/widgets/NMapWnd.java");
        String tools = read("src/nurgling/widgets/MapToolsWindow.java");
        String gui = Files.readString(Paths.get("src/nurgling/NGameUI.java"));
        assertEquals(8, count(nmap, "MapSearchFront.showInFront"),
                "NMapWnd open* show/re-show paths");
        assertEquals(11, count(tools, "MapSearchFront.showInFront"),
                "MapToolsWindow toggle/open* show paths");
        assertTrue(nmap.contains("openForagingSearch"));
        assertTrue(nmap.contains("openTreeSearch"));
        assertTrue(nmap.contains("openFishSearch"));
        assertTrue(nmap.contains("openQuarryartzSearch"));
        assertFalse(nmap.contains("openGemstoneSearch"));
        assertFalse(nmap.contains("openProspectingSearch"));
        assertFalse(nmap.contains("openOresSearch"));
        assertTrue(nmap.contains("openMineralSearch(ProspectKind.ORE)"));
        assertTrue(nmap.contains("openMineralSearch(ProspectKind.GEM)"));
        assertFalse(nmap.contains("openMineralSearch(ProspectKind.STONE)"));
        assertTrue(tools.contains("openTerrainSearch"));
        assertTrue(tools.contains("openTerrainResources"));
        assertTrue(tools.contains("openTreeSearch"));
        assertTrue(tools.contains("openFishSearch"));
        assertTrue(tools.contains("openMineralSearch"));
        assertTrue(tools.contains("public static void openMineralSearch()"));
        assertTrue(tools.contains("public static void openMineralSearch(ProspectKind"));
        assertTrue(tools.contains("public static void toggle"));
        assertTrue(gui.contains("mineralSearchWindow"));
        assertTrue(gui.contains("mineralSearchWindow.destroy()"));
        assertTrue(gui.contains("oreSearchWindow"));
        assertTrue(gui.contains("gemstoneSearchWindow"));
        assertTrue(nmap.contains("animalsBtn"));
        assertTrue(nmap.contains("foragingBtn"));
        assertTrue(nmap.contains("oreBtn"));
        assertTrue(nmap.contains("gemBtn"));
        assertFalse(nmap.contains("stoneBtn"));
        assertTrue(nmap.contains("quarryartzBtn"));
        assertFalse(nmap.contains("oreSpotsBtn"));
        assertFalse(nmap.contains("MinesweeperOverlay"));
        assertTrue(Files.isRegularFile(Path.of("src/nurgling/widgets/OreSearchWindow.java")));
        assertTrue(Files.isRegularFile(Path.of("src/nurgling/widgets/GemstoneSearchWindow.java")));
    }

    @Test
    void clayIsNotAnOreSpot() throws Exception {
        LabeledMinimapMark clay = new LabeledMinimapMark("q30", "Clay", 30.0, 1L, new Coord(1, 1), null);
        assertEquals(ProspectKind.CLAY, clay.kind);
        assertFalse(MasterMiner.isOre("Clay"));

        String src = Files.readString(Path.of("src/nurgling/widgets/NMiniMap.java"), StandardCharsets.UTF_8);
        String ore = method(src, "static boolean isOreSpotMark");
        assertTrue(ore.contains("mark.kind == ProspectKind.ORE"));
        assertTrue(ore.contains("MasterMiner.isOre"));
        assertFalse(ore.contains("Clay"));
        assertFalse(ore.contains("clay"));
    }

    private static String method(String src, String sig) {
        int at = src.indexOf(sig);
        assertTrue(at >= 0, "missing " + sig);
        int brace = src.indexOf('{', at);
        int depth = 0;
        for (int i = brace; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0)
                    return src.substring(at, i + 1);
            }
        }
        throw new AssertionError("unclosed " + sig);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    private static int count(String src, String needle) {
        int n = 0;
        for(int i = 0; (i = src.indexOf(needle, i)) >= 0; i += needle.length())
            n++;
        return n;
    }
}
