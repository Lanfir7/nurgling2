package nurgling.widgets;

import haven.Widget;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(12, count(nmap, "MapSearchFront.showInFront"),
                "NMapWnd open* show/re-show paths");
        assertEquals(8, count(tools, "MapSearchFront.showInFront"),
                "MapToolsWindow toggle/open* show paths");
        assertTrue(nmap.contains("openForagingSearch"));
        assertTrue(nmap.contains("openQuarryartzSearch"));
        assertTrue(nmap.contains("openGemstoneSearch"));
        assertTrue(nmap.contains("openTreeSearch"));
        assertTrue(nmap.contains("openFishSearch"));
        assertTrue(nmap.contains("openProspectingSearch"));
        assertTrue(nmap.contains("openOresSearch"));
        assertTrue(tools.contains("openTerrainSearch"));
        assertTrue(tools.contains("openTerrainResources"));
        assertTrue(tools.contains("openTreeSearch"));
        assertTrue(tools.contains("openFishSearch"));
        assertTrue(tools.contains("public static void toggle"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static int count(String src, String needle) {
        int n = 0;
        for(int i = 0; (i = src.indexOf(needle, i)) >= 0; i += needle.length())
            n++;
        return n;
    }
}
