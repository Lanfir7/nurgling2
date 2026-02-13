package nurgling.widgets.nsettings;

import haven.*;
import nurgling.NConfig;
import nurgling.NStyle;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Настройки отключения анимации для выбранных гобов (Tar Kiln, Smelter, Candle и т.д.).
 * Глобальный чекбокс + список гобов с индивидуальными чекбоксами.
 */
public class DisableGobAnimSettings extends Panel {
    public ArrayList<PatternItem> patterns = new ArrayList<>();
    private PatternList list;
    private TextEntry newPattern;
    private CheckBox enabledCheckbox;
    private final int width = UI.scale(210);

    // Cached set of disabled gob resource names for fast lookup from render thread
    private static volatile boolean globalEnabled = false;
    private static final Set<String> disabledSet = new CopyOnWriteArraySet<>();

    public DisableGobAnimSettings() {
        super("Disable Animations");
        final int margin = UI.scale(10);

        prev = enabledCheckbox = add(new CheckBox("Disable Gob Animations") {
            @Override
            public void set(boolean val) {
                a = val;
                NConfig.set(NConfig.Key.disableGobAnimEnabled, val);
                globalEnabled = val;
                NConfig.needUpdate();
            }
        }, new Coord(margin, margin + UI.scale(20)));

        prev = add(new Label("Animated gobs:"), prev.pos("bl").adds(0, UI.scale(10)));
        prev = add(list = new PatternList(new Coord(width, UI.scale(250))), prev.pos("bl").adds(0, 5));

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
        Object enabledRaw = NConfig.get(NConfig.Key.disableGobAnimEnabled);
        if (enabledCheckbox != null) {
            enabledCheckbox.a = enabledRaw instanceof Boolean ? (Boolean) enabledRaw : false;
        }
        globalEnabled = enabledCheckbox != null && enabledCheckbox.a;

        patterns.clear();
        Object raw = NConfig.get(NConfig.Key.disableGobAnimPatterns);
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
        if (list != null) list.update();
        rebuildCache();
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
        NConfig.set(NConfig.Key.disableGobAnimPatterns, plist);
        NConfig.needUpdate();
        rebuildCache();
    }

    /** Rebuild the cached set of disabled resource names from current config. */
    @SuppressWarnings("unchecked")
    private static void rebuildCache() {
        Set<String> newSet = new HashSet<>();
        Object raw = NConfig.get(NConfig.Key.disableGobAnimPatterns);
        if (raw instanceof ArrayList) {
            for (Object obj : (ArrayList<?>) raw) {
                if (obj instanceof HashMap) {
                    HashMap<String, Object> m = (HashMap<String, Object>) obj;
                    if (Boolean.TRUE.equals(m.get("enabled"))) {
                        String name = (String) m.get("name");
                        if (name != null && !name.isEmpty()) {
                            newSet.add(name);
                        }
                    }
                }
            }
        }
        disabledSet.clear();
        disabledSet.addAll(newSet);
    }

    /**
     * Static init: load cached state from NConfig at startup.
     * Called once when the class is first referenced.
     */
    static {
        Object enabledRaw = NConfig.get(NConfig.Key.disableGobAnimEnabled);
        globalEnabled = enabledRaw instanceof Boolean && (Boolean) enabledRaw;
        rebuildCache();
    }

    /**
     * Fast check: is animation disabled for the given resource name?
     * Called from ResDrawable.ctick() on every frame — must be fast.
     */
    public static boolean isAnimDisabled(String resName) {
        if (!globalEnabled || resName == null) return false;
        return disabledSet.contains(resName);
    }

    // ---- UI list ----

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
