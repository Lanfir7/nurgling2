package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.Resource;
import haven.Widget;
import nurgling.NConfig;
import nurgling.widgets.SettingsPageFrame;
import nurgling.widgets.StepSettingsPanel;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioPanelLayoutTest {
    static {
        NConfig.current = new NConfig();
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                    Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if(stream == null)
                    throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch(java.net.MalformedURLException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void buttonsAndSettingsStayInsideTheSettingsPage() {
        ScenarioPanel panel = new ScenarioPanel();
        SettingsPageFrame frame = new SettingsPageFrame(panel, "Scenarios");

        frame.fitTo(Coord.of(560, 480), 1);

        assertTrue(frame.sz.y <= 480, "page should stay in the viewport");
        assertContained(panel);
        StepSettingsPanel settings = find(panel, StepSettingsPanel.class);
        Label hint = find(settings, Label.class);
        assertTrue(hint.c.x + hint.sz.x <= settings.sz.x, "settings hint is clipped");
        Button add = find(panel, Button.class);
        Widget host = add.parent;
        assertTrue(add.c.y + add.sz.y <= host.sz.y, "bottom button is clipped");
        assertTrue(add.c.x + add.sz.x <= host.sz.x, "bottom button runs past the page");
    }

    private static void assertContained(Widget root) {
        for(Widget child = root.child; child != null; child = child.next) {
            assertTrue(child.c.x >= 0 && child.c.y >= 0, child.getClass().getSimpleName());
            assertTrue(child.c.x + child.sz.x <= root.sz.x, child.getClass().getSimpleName() + " past the right edge");
            assertTrue(child.c.y + child.sz.y <= root.sz.y, child.getClass().getSimpleName() + " past the bottom");
            assertContained(child);
        }
    }

    private static <T extends Widget> T find(Widget root, Class<T> type) {
        for(Widget child = root.child; child != null; child = child.next) {
            if(type.isInstance(child))
                return type.cast(child);
            T nested = find(child, type);
            if(nested != null)
                return nested;
        }
        return null;
    }
}
