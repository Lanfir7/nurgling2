package nurgling.widgets;

import haven.Button;
import haven.CheckBox;
import haven.Coord;
import haven.HSlider;
import haven.Scrollport;
import haven.UI;
import haven.Widget;
import nurgling.ClientResourceFixture;
import nurgling.NConfig;
import nurgling.i18n.L10n;
import nurgling.widgets.options.GraphicsSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsSettingsScrollTest {
    @Test
    void lastEffectIsReachableByPageScrollAtNormalAndShortHeights() throws Exception {
        try(ClientResourceFixture resources = new ClientResourceFixture()) {
            NConfig previous = NConfig.current;
            NConfig.current = new NConfig();
            try {
                for(int height : new int[] {540, 300}) {
                    GraphicsSettings panel = new GraphicsSettings();
                    Scrollport page = new Scrollport(UI.scale(545, height));
                    SettingsPageFrame frame = new SettingsPageFrame(panel, null);
                    page.cont.add(frame, Coord.z);
                    frame.fitTo(page.cont.sz, 1);
                    page.cont.update();

                    CheckBox lastEffect = panel.children(CheckBox.class).stream()
                            .filter(box -> L10n.get("gfx.shafts").equals(box.label()))
                            .findFirst().orElseThrow();

                    assertTrue(positionWithin(lastEffect, panel).y > page.sz.y,
                            "The final option must exercise scrolling at this viewport height");
                    assertTrue(page.bar.max > 0, "The page must scroll to reveal lower options");
                    page.bar.ch(page.bar.max);
                    assertTrue(fullyVisibleWithin(lastEffect, page),
                            "The final option must be visible when the page scrollbar reaches its end");
                    for(Widget control : panel.children(Widget.class)) {
                        if(!(control instanceof Button || control instanceof CheckBox || control instanceof HSlider))
                            continue;
                        Coord pos = positionWithin(control, panel);
                        assertTrue(pos.x >= 0 && pos.x + control.sz.x <= panel.sz.x,
                                "An option extends beyond the page width: " + control);
                    }
                }
            } finally {
                NConfig.current = previous;
            }
        }
    }

    private static Coord positionWithin(Widget child, Widget ancestor) {
        Coord position = Coord.z;
        for(Widget current = child; current != ancestor; current = current.parent)
            position = position.add(current.c);
        return position;
    }

    private static boolean fullyVisibleWithin(Widget child, Widget ancestor) {
        Coord position = Coord.z;
        for(Widget current = child; current != ancestor; current = current.parent) {
            Widget parent = current.parent;
            position = parent.xlate(current.c.add(position), true);
            if(position.x < 0 || position.y < 0 ||
                    position.x + child.sz.x > parent.sz.x ||
                    position.y + child.sz.y > parent.sz.y)
                return false;
        }
        return true;
    }
}
