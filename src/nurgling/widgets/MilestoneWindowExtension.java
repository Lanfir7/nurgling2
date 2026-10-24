package nurgling.widgets;

import haven.*;
import nurgling.NStyle;
import nurgling.hotkeys.InputNavigation;
import nurgling.i18n.L10n;
import nurgling.tools.RoadTags;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Private tag column for the road rows of a known, right-clicked milestone. */
public final class MilestoneWindowExtension {
    private static final String POST_PREFIX = "gfx/terobjs/road/milestone-";
    private MilestoneWindowExtension() {}

    public static void attach(Window window) {
        if (window == null) return;
        for (Widget child = window.child; child != null; child = child.next)
            if (child instanceof Binder) return;
        window.add(new Binder(), Coord.z);
    }

    static boolean isRoadButton(Widget widget) {
        if (!(widget instanceof Button) || ((Button) widget).text == null) return false;
        String text = ((Button) widget).text.text;
        return "Follow".equals(text) || "Travel".equals(text);
    }

    /** Only an aligned road name and action button make a window eligible to bind a post. */
    static List<Label> roadRows(Window window) {
        List<Button> buttons = new ArrayList<>();
        for (Widget child = window.child; child != null; child = child.next)
            if (isRoadButton(child)) buttons.add((Button) child);
        List<Label> rows = new ArrayList<>();
        for (Widget child = window.child; child != null; child = child.next) {
            if (!(child instanceof Label) || ((Label) child).texts == null) continue;
            int middle = child.c.y + child.sz.y / 2;
            for (Button button : buttons) {
                if (middle >= button.c.y && middle < button.c.y + button.sz.y) {
                    rows.add((Label) child);
                    break;
                }
            }
        }
        rows.sort((a, b) -> Integer.compare(a.c.y, b.c.y));
        return rows;
    }

    static final class Binder extends Widget {
        static final long WAIT_MS = 5_000;
        private static final int GAP = UI.scale(10);
        private static final int CELL_WIDTH = UI.scale(150);
        private final long created = System.currentTimeMillis();
        private final Map<Label, TagCell> cells = new IdentityHashMap<>();
        private Gob post;
        private String postKey;
        private int originalMargin = -1;

        Binder() { super(Coord.z); }

        static boolean accepts(Gob gob) {
            return gob != null && gob.ngob != null && gob.ngob.name != null
                    && gob.ngob.name.startsWith(POST_PREFIX);
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            if (!(parent instanceof Window)) return;
            Window window = (Window) parent;
            List<Label> rows = roadRows(window);
            if (rows.isEmpty()) {
                for (TagCell cell : cells.values()) cell.destroy();
                cells.clear();
                if (postKey == null && expired()) destroy();
                return;
            }
            if (postKey == null) {
                if (post == null && ui != null && ui.core != null)
                    post = ui.core.takeClickedGob(Binder::accepts);
                if (post != null) postKey = RoadTags.postKey(post);
                if (postKey == null) {
                    if (expired()) destroy();
                    return;
                }
            }
            layout(window, rows);
        }

        private boolean expired() {
            return System.currentTimeMillis() - created >= WAIT_MS;
        }

        private void layout(Window window, List<Label> rows) {
            int right = 0;
            for (Widget child = window.child; child != null; child = child.next)
                if (isRoadButton(child)) right = Math.max(right, child.c.x + child.sz.x);
            if (originalMargin < 0)
                originalMargin = Math.max(0, window.csz().x - right);
            int x = right + GAP;
            Map<String, Integer> seen = new HashMap<>();
            Set<Label> live = new HashSet<>(rows);
            for (Map.Entry<Label, TagCell> entry : new ArrayList<>(cells.entrySet())) {
                if (!live.contains(entry.getKey())) {
                    entry.getValue().destroy();
                    cells.remove(entry.getKey());
                }
            }
            for (Label row : rows) {
                String key = RoadTags.roadKey(row.texts, seen.merge(row.texts, 1, Integer::sum) - 1);
                TagCell cell = cells.get(row);
                if (cell == null || !cell.roadKey.equals(key)) {
                    if (cell != null) cell.destroy(); // discard an edit when a server row changes identity
                    cell = window.add(new TagCell(postKey, key, CELL_WIDTH));
                    cells.put(row, cell);
                }
                cell.move(Coord.of(x, row.c.y + (row.sz.y - cell.sz.y) / 2));
            }
            int needed = x + CELL_WIDTH + originalMargin;
            if (window.csz().x < needed)
                window.resize(Coord.of(needed, window.csz().y));
        }
    }

    static final class TagCell extends Widget {
        private static final Color EMPTY = new Color(111, 124, 120);
        final String roadKey;
        private final String postKey;
        private String tag;
        private Text rendered;
        private Entry entry;
        private boolean closing;
        private boolean entryFocused;

        TagCell(String postKey, String roadKey, int width) {
            super(Coord.of(width, Text.std.height()));
            this.postKey = postKey;
            this.roadKey = roadKey;
            this.tag = RoadTags.get(postKey, roadKey);
            render();
        }

        private void render() {
            boolean hasTag = tag != null && !tag.isEmpty();
            rendered = Text.std.render(hasTag ? tag : L10n.get("roadtag.add"), hasTag ? NStyle.border : EMPTY);
            settip(hasTag ? tag : L10n.get("roadtag.tip"));
        }

        @Override
        public void draw(GOut out) {
            if (entry == null) out.reclip(Coord.z, sz).image(rendered.tex(), Coord.z);
            super.draw(out);
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if (entry == null && event.b == 1) {
                entry = add(new Entry(sz.x, tag == null ? "" : tag), Coord.z);
                entryFocused = false;
                setfocus(entry);
                return true;
            }
            return super.mousedown(event);
        }

        private void close(String save) {
            if (save != null) {
                RoadTags.set(postKey, roadKey, save);
                tag = RoadTags.get(postKey, roadKey);
                render();
            }
            closing = true;
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            if (entry == null) return;
            if (entry.hasfocus) entryFocused = true;
            if (closing || (entryFocused && !entry.hasfocus)) {
                entry.destroy();
                entry = null;
                closing = false;
            }
        }

        private final class Entry extends TextEntry {
            Entry(int width, String value) { super(width, value); }
            @Override public void activate(String value) { close(value); }
            @Override public boolean keydown(KeyDownEvent event) {
                if (InputNavigation.cancel(event.code)) {
                    close(null);
                    return true;
                }
                return super.keydown(event);
            }
        }
    }
}
