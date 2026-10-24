package nurgling.widgets;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.TextEntry;
import haven.Widget;
import haven.Window;
import nurgling.NConfig;
import nurgling.tools.RoadTags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MilestoneWindowExtensionTest {
    @TempDir Path temp;
    private final NConfig prior = NConfig.current;

    @AfterEach void restore() { NConfig.current = prior; }

    private void usePrivateConfig() throws Exception {
        NConfig config = new NConfig();
        Field field = NConfig.class.getDeclaredField("path");
        field.setAccessible(true);
        field.set(config, temp.resolve("config.json").toString());
        NConfig.current = config;
    }

    private static <T extends Widget> List<T> children(Widget parent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Widget child = parent.child; child != null; child = child.next)
            if (type.isInstance(child)) found.add(type.cast(child));
        return found;
    }

    private static MilestoneWindowExtension.Binder bindForTest(Window window, String post) throws Exception {
        MilestoneWindowExtension.attach(window);
        MilestoneWindowExtension.attach(window);
        List<MilestoneWindowExtension.Binder> binders = children(window, MilestoneWindowExtension.Binder.class);
        assertEquals(1, binders.size(), "a window gets one binder");
        Field key = MilestoneWindowExtension.Binder.class.getDeclaredField("postKey");
        key.setAccessible(true);
        key.set(binders.get(0), post);
        return binders.get(0);
    }

    @Test
    void onlyAlignedRoadRowsReceiveTagsAndLayoutDoesNotKeepGrowing() throws Exception {
        usePrivateConfig();
        Window unrelated = new Window(Coord.of(300, 130), "Other");
        unrelated.add(new Label("A"), Coord.of(10, 10));
        unrelated.add(new Button(70, "Save"), Coord.of(120, 8));
        assertTrue(MilestoneWindowExtension.roadRows(unrelated).isEmpty());

        Window road = new Window(Coord.of(300, 160), "Milestone");
        Label first = road.add(new Label("A"), Coord.of(10, 12));
        road.add(new Button(70, "Follow"), Coord.of(120, 10));
        Label second = road.add(new Label("A"), Coord.of(10, 52));
        road.add(new Button(70, "Travel"), Coord.of(120, 50));
        String post = RoadTags.postKey(91, -7, 12);
        MilestoneWindowExtension.Binder binder = bindForTest(road, post);
        binder.tick(0);
        List<MilestoneWindowExtension.TagCell> cells = children(road, MilestoneWindowExtension.TagCell.class);
        assertEquals(2, cells.size());
        assertTrue(cells.stream().anyMatch(cell -> cell.roadKey.equals(RoadTags.roadKey("A", 0))));
        assertTrue(cells.stream().anyMatch(cell -> cell.roadKey.equals(RoadTags.roadKey("A", 1))));
        int width = road.csz().x;
        binder.tick(0);
        assertEquals(width, road.csz().x, "tag column must be added only once");

        first.c = Coord.of(10, 52);
        second.c = Coord.of(10, 12);
        binder.tick(0);
        for (MilestoneWindowExtension.TagCell cell : cells)
            assertNull(cell.parent, "reordered duplicate names must discard old editors");
        cells = children(road, MilestoneWindowExtension.TagCell.class);

        MilestoneWindowExtension.TagCell old = cells.stream()
                .filter(cell -> cell.roadKey.equals(RoadTags.roadKey("A", 1))).findFirst().orElseThrow();
        old.mousedown(new Widget.MouseDownEvent(Coord.z, 1));
        first.settext("B");
        binder.tick(0);
        assertNull(old.parent, "a changed row discards its editor without saving");
        assertNull(RoadTags.get(post, RoadTags.roadKey("A", 0)));
        assertEquals(2, children(road, MilestoneWindowExtension.TagCell.class).size());
        first.destroy();
        binder.tick(0);
        assertEquals(1, children(road, MilestoneWindowExtension.TagCell.class).size());
    }

    @Test
    void enterSavesAndFocusLossCancelsAnEdit() throws Exception {
        usePrivateConfig();
        Window road = new Window(Coord.of(300, 120), "Milestone");
        road.add(new Label("East"), Coord.of(10, 12));
        road.add(new Button(70, "Follow"), Coord.of(120, 10));
        String post = RoadTags.postKey(91, -7, 12);
        String key = RoadTags.roadKey("East", 0);
        MilestoneWindowExtension.Binder binder = bindForTest(road, post);
        binder.tick(0);
        MilestoneWindowExtension.TagCell cell = children(road, MilestoneWindowExtension.TagCell.class).get(0);
        cell.mousedown(new Widget.MouseDownEvent(Coord.z, 1));
        TextEntry entry = children(cell, TextEntry.class).get(0);
        entry.activate("  Home  ");
        cell.tick(0);
        assertEquals("Home", RoadTags.get(post, key));

        cell.mousedown(new Widget.MouseDownEvent(Coord.z, 1));
        entry = children(cell, TextEntry.class).get(0);
        entry.settext("escape should cancel");
        KeyEvent escape = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        assertTrue(entry.keydown(new Widget.KeyDownEvent(escape)));
        cell.tick(0);
        assertEquals("Home", RoadTags.get(post, key));

        cell.mousedown(new Widget.MouseDownEvent(Coord.z, 1));
        entry = children(cell, TextEntry.class).get(0);
        entry.settext("wrong road");
        entry.hasfocus = true;
        cell.tick(0);
        entry.hasfocus = false;
        cell.tick(0);
        assertEquals("Home", RoadTags.get(post, key));
    }
}
