package nurgling.widgets.nsettings;

import haven.*;
import nurgling.NConfig;
import nurgling.NStyle;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Настройки макроса «Маркеры животных»: список regex-паттернов для отслеживания (как в Quick Actions).
 * Сохраняется в NConfig.animal_marker_patterns и используется при включении макроса на карте.
 */
public class AnimalMarkersSettings extends Panel {
    public ArrayList<PatternItem> patterns = new ArrayList<>();
    private PatternList list;
    private TextEntry newPattern;
    private final int width = UI.scale(210);

    private static final List<String> DEFAULT_PATTERNS;
    static {
        ArrayList<String> def = new ArrayList<>();
        def.add("gfx/kritter/.*");
        def.add("gfx/kritter/boar/.*");
        def.add("gfx/kritter/deer/.*");
        def.add("gfx/kritter/moose/.*");
        def.add("gfx/kritter/bear/.*");
        def.add("gfx/kritter/wolf/.*");
        def.add("gfx/kritter/lynx/.*");
        def.add("gfx/kritter/fox/.*");
        def.add("gfx/kritter/rabbit/.*");
        def.add("gfx/kritter/rat/.*");
        def.add("gfx/kritter/pheasant/.*");
        def.add("gfx/kritter/roe/.*");
        def.add("gfx/kritter/elk/.*");
        def.add("gfx/kritter/mammoth/.*");
        def.add("gfx/kritter/wolverine/.*");
        def.add("gfx/kritter/badger/.*");
        def.add("gfx/kritter/adder/.*");
        def.add("gfx/kritter/wildgoat/.*");
        def.add("gfx/kritter/walrus/.*");
        def.add("gfx/kritter/orca/.*");
        def.add("gfx/kritter/troll/.*");
        def.add("gfx/kritter/bat/.*");
        def.add("gfx/kritter/eagle/.*");
        def.add("gfx/kritter/eagleowl/.*");
        def.add("gfx/kritter/goldeneagle/.*");
        def.add("gfx/kritter/goat/.*");
        def.add("gfx/kritter/cavelouse/.*");
        def.add("gfx/kritter/boreworm/.*");
        DEFAULT_PATTERNS = Collections.unmodifiableList(def);
    }

    public AnimalMarkersSettings() {
        super(L10n.get("animal_markers.settings.title"));
        final int margin = UI.scale(10);

        prev = add(new Label(L10n.get("animal_markers.patterns_list")), new Coord(margin, margin + UI.scale(20)));
        prev = add(list = new PatternList(new Coord(width, UI.scale(220))), prev.pos("bl").adds(0, 5));

        prev = add(newPattern = new TextEntry(UI.scale(175), ""), prev.pos("bl").adds(0, 10));
        add(new IButton(
            Resource.loadsimg("nurgling/hud/buttons/add/u"),
            Resource.loadsimg("nurgling/hud/buttons/add/d"),
            Resource.loadsimg("nurgling/hud/buttons/add/h")) {
            @Override
            public void click() {
                if (!newPattern.text().isEmpty()) {
                    PatternItem pi = new PatternItem(newPattern.text());
                    pi.isEnabled.a = true;
                    patterns.add(pi);
                    newPattern.settext("");
                    if (list != null) list.update();
                }
            }
        }, new Coord(newPattern.pos("ur").x + UI.scale(5), newPattern.c.y + (newPattern.sz.y - UI.scale(18)) / 2));

        load();
        pack();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void load() {
        patterns.clear();
        Object raw = NConfig.get(NConfig.Key.animal_marker_patterns);
        if (raw instanceof ArrayList) {
            for (Object obj : (ArrayList<?>) raw) {
                if (obj instanceof HashMap) {
                    HashMap<String, Object> m = (HashMap<String, Object>) obj;
                    String name = (String) m.get("name");
                    Boolean enabled = (Boolean) m.get("enabled");
                    if (name != null) {
                        PatternItem pi = new PatternItem(name);
                        pi.isEnabled.a = enabled != null ? enabled : true;
                        patterns.add(pi);
                    }
                }
            }
        }
        if (patterns.isEmpty()) {
            for (String p : DEFAULT_PATTERNS) {
                PatternItem pi = new PatternItem(p);
                pi.isEnabled.a = true;
                patterns.add(pi);
            }
        }
        if (list != null) list.update();
    }

    @Override
    public void save() {
        ArrayList<HashMap<String, Object>> plist = new ArrayList<>();
        for (PatternItem pi : patterns) {
            HashMap<String, Object> m = new HashMap<>();
            m.put("type", "NPattern");
            m.put("name", pi.text());
            m.put("enabled", pi.isEnabled.a);
            plist.add(m);
        }
        NConfig.set(NConfig.Key.animal_marker_patterns, plist);
        NConfig.needUpdate();
    }

    /**
     * Возвращает список включённых regex-паттернов для макроса (используется NGameUI.startAnimalMarkerMacro).
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<String> getEnabledPatterns() {
        Object raw = NConfig.get(NConfig.Key.animal_marker_patterns);
        ArrayList<String> out = new ArrayList<>();
        if (raw instanceof ArrayList) {
            for (Object obj : (ArrayList<?>) raw) {
                if (obj instanceof HashMap) {
                    HashMap<String, Object> m = (HashMap<String, Object>) obj;
                    if (Boolean.TRUE.equals(m.get("enabled"))) {
                        String name = (String) m.get("name");
                        if (name != null && !name.isEmpty()) out.add(name);
                    }
                }
            }
        }
        if (out.isEmpty()) out.add("gfx/kritter/.*");
        return out;
    }

    private class PatternList extends haven.SListBox<PatternItem, Widget> {
        PatternList(Coord sz) {
            super(sz, UI.scale(22));
        }

        @Override
        protected List<PatternItem> items() {
            return patterns;
        }

        @Override
        public void resize(Coord sz) {
            super.resize(new Coord(width - UI.scale(6), sz.y));
        }

        @Override
        protected Widget makeitem(PatternItem item, int idx, Coord sz) {
            return new haven.SListWidget.ItemWidget<PatternItem>(this, sz, item) {
                {
                    add(item);
                    item.resize(sz);
                }
                @Override
                public void resize(Coord sz) {
                    super.resize(sz);
                    item.resize(sz);
                }
            };
        }

        private final Color bg = new Color(30, 40, 40, 160);
        @Override
        public void draw(GOut g) {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            g.chcolor();
            super.draw(g);
        }
    }

    public class PatternItem extends Widget {
        Label text;
        IButton remove;
        public CheckBox isEnabled;

        @Override
        public void resize(Coord sz) {
            if (isEnabled != null) isEnabled.move(new Coord(isEnabled.c.x, (sz.y - isEnabled.sz.y) / 2));
            if (text != null) text.move(new Coord(text.c.x, (sz.y - text.sz.y) / 2));
            if (remove != null) remove.move(new Coord(sz.x - NStyle.removei[0].sz().x - UI.scale(5), (sz.y - remove.sz.y) / 2));
            super.resize(sz);
        }

        PatternItem(String pattern) {
            prev = isEnabled = add(new CheckBox("") {
                @Override
                public void set(boolean val) { a = val; }
            });
            this.text = add(new Label(pattern), prev.pos("ur").add(UI.scale(2), 0));
            remove = add(new IButton(NStyle.removei[0].back, NStyle.removei[1].back, NStyle.removei[2].back) {
                @Override
                public void click() {
                    patterns.remove(PatternItem.this);
                    if (list != null) list.update();
                }
            }, new Coord(width - NStyle.removei[0].sz().x, 0).sub(UI.scale(5), UI.scale(1)));
            try {
                remove.settip(Resource.remote().loadwait("nurgling/hud/buttons/removeItem/u").flayer(Resource.tooltip).text());
            } catch (Exception ignored) {}
            pack();
        }

        public String text() {
            return text != null ? text.text() : "";
        }
    }
}
