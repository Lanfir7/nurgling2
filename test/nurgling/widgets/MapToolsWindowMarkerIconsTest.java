package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapToolsWindowMarkerIconsTest {
    @Test
    void markerIconControlOpensDedicatedScrollableWindow() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/MapToolsWindow.java"), StandardCharsets.UTF_8);

        assertTrue(src.contains("private class MarkerIconsWindow extends Window"));
        assertTrue(src.contains("openMarkerIcons();"));
        assertTrue(src.contains("new Scrollport(new Coord(OVERLAY_W - UI.scale(8), UI.scale(380)))"));
        assertTrue(src.contains("list.showbar(true);"));
        assertTrue(src.contains("list.bar.val = Math.min(scroll, list.bar.max);"));
        assertTrue(src.contains("if(markerIconsWindow == window)"));
        assertTrue(src.contains("List<MapWnd.MapIconType> types = map.markerVisibilityTypes()"));
        assertFalse(src.contains("markerTab = tabs.add()"));

        String mapWnd = Files.readString(Path.of("src/haven/MapWnd.java"), StandardCharsets.UTF_8);
        int types = mapWnd.indexOf("public List<MapIconType> markerVisibilityTypes()");
        String body = mapWnd.substring(types, mapWnd.indexOf("private MarkerType markerVisibilityType", types));
        assertTrue(body.contains("MapMarkerVisibility.isSystemMarker(marker)"));
    }

    @Test
    void markerTypesContainOnlySourcesWithoutExistingToggle() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/NMiniMap.java"), StandardCharsets.UTF_8);
        int method = src.indexOf("public java.util.List<MapWnd.MapIconType> mapIconTypes()");
        assertTrue(method >= 0);
        String body = src.substring(method, src.indexOf("private static String iconLabelOr", method));

        assertTrue(body.contains("getAllTimers()"));
        assertTrue(body.contains("MapMarkerVisibility.liveIconIdentity(icon)"));
        assertTrue(body.contains("resourceIdentity(\"discovery\""));
        assertTrue(body.contains("resourceIdentity(\"timer\""));
        assertFalse(body.contains("resourceIdentity(\"tree\""));
        assertFalse(body.contains("resourceIdentity(\"fish\""));
        assertFalse(body.contains("resourceIdentity(\"prospecting\""));
        assertFalse(body.contains("resourceIdentity(\"local\""));
    }
}
