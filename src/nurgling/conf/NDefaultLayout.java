package nurgling.conf;

import haven.Coord;
import haven.UI;
import nurgling.NConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anchor-based default placement for the draggable HUD widgets.
 *
 * The previous defaults were absolute pixel coordinates authored on a small
 * (~800x600) window and stored unscaled, which produced two visible problems on
 * a first launch: on a large monitor the whole HUD was crammed into the top-left
 * quadrant, and every widget without an entry in the list piled up on top of
 * each other at (0,0). Offsets here are design pixels resolved against the live
 * screen size and UI scale instead, so the default layout holds at any
 * resolution. As soon as the player moves a widget it gets an absolute position
 * in the config and stops consulting this table.
 *
 * Three presets are offered rather than one, because the audience genuinely
 * differs: someone arriving from the vanilla client wants the vanilla HUD, while
 * someone here for the automation wants the bot panels up front. Presets are
 * expressed as a diff over {@link Preset#CLASSIC} so the shared placement only
 * has to be described once.
 */
public class NDefaultLayout {
    public enum Anchor {
        TL, TC, TR,
        ML, MC, MR,
        BL, BC, BR
    }

    /**
     * Nominal widget footprint, used only to draw the schematic layout previews
     * in the picker. Real placement always uses the widget's live size.
     */
    public enum Box {
        METER(100, 20),
        SMALL(120, 108),
        BAR(240, 90),
        COMPASS(520, 100),
        BELT(500, 40),
        SLIM(200, 80),
        PANEL(200, 150),
        BIG(210, 210),
        TALL(230, 380),
        CHAT(280, 160);

        public final Coord sz;

        Box(int x, int y) {
            this.sz = new Coord(x, y);
        }
    }

    public enum Preset {
        MINIMAL, CLASSIC, BOTS;

        public String label() {
            return (nurgling.i18n.L10n.get("layout.preset." + name().toLowerCase()));
        }

        public String desc() {
            return (nurgling.i18n.L10n.get("layout.preset." + name().toLowerCase() + ".desc"));
        }

        public static Preset current() {
            Object v = NConfig.get(NConfig.Key.layoutPreset);
            if (v instanceof String) {
                try {
                    return (valueOf((String) v));
                } catch (IllegalArgumentException e) {
                    /* Unknown name in a hand-edited config: fall through. */
                }
            }
            return (CLASSIC);
        }
    }

    public static class Slot {
        public final Anchor anchor;
        /**
         * Inset from the anchored edge for edge anchors, or a delta from the
         * centre line for the centred ones, in unscaled design pixels.
         */
        public final Coord off;
        public final boolean vis;
        public final Box box;

        Slot(Anchor anchor, int x, int y, Box box) {
            this(anchor, x, y, box, true);
        }

        Slot(Anchor anchor, int x, int y, Box box, boolean vis) {
            this.anchor = anchor;
            this.off = new Coord(x, y);
            this.box = box;
            this.vis = vis;
        }

        Slot hidden() {
            return (new Slot(anchor, off.x, off.y, box, false));
        }

        Slot at(Anchor anchor, int x, int y) {
            return (new Slot(anchor, x, y, box, vis));
        }
    }

    /** Reference screen the design-pixel offsets were authored against. */
    public static final Coord design = new Coord(1280, 720);

    private static final int BELT_STEP = 58;
    private static final Pattern BELT = Pattern.compile("belt(\\d+)");

    /** Shared placement, and the CLASSIC preset in its own right. */
    private static final Map<String, Slot> base = new LinkedHashMap<>();
    private static final Map<Preset, Map<String, Slot>> diff = new HashMap<>();

    static {
        /* Vitals, top-left: portrait with the meter stack beside it. */
        base.put("portrait", new Slot(Anchor.TL, 4, 4, Box.SMALL));
        base.put("metergfx/hud/meter/hp", new Slot(Anchor.TL, 132, 6, Box.METER));
        base.put("metergfx/hud/meter/stam", new Slot(Anchor.TL, 132, 30, Box.METER));
        base.put("metergfx/hud/meter/nrj", new Slot(Anchor.TL, 132, 54, Box.METER));
        base.put("drinkmeter", new Slot(Anchor.TL, 132, 78, Box.METER));
        base.put("speedmeter", new Slot(Anchor.TL, 132, 102, Box.METER));
        base.put("party", new Slot(Anchor.TL, 4, 128, Box.PANEL));

        /* Navigation, time and buffs, top-centre. */
        base.put("compass", new Slot(Anchor.TC, 0, 4, Box.COMPASS));
        base.put("Calendar", new Slot(Anchor.TC, 0, 110, Box.BAR));
        base.put("bufflist", new Slot(Anchor.TC, 0, 204, Box.BAR));

        /* Navigation and notifications, top-right. */
        base.put("mainmenu", new Slot(Anchor.TR, 4, 4, Box.BAR));
        base.put("minimap", new Slot(Anchor.TR, 4, 120, Box.BIG));
        base.put("quests", new Slot(Anchor.TR, 4, 336, Box.PANEL));
        /* Alarms sit top-centre rather than in the right column: that column is
         * fully booked by the menu, minimap, quests and the action grid. */
        base.put("alarm", new Slot(Anchor.TC, 0, 298, Box.METER));

        /* Client tools, left edge in the band between the party list and chat. */
        base.put("botsmenu", new Slot(Anchor.ML, 4, 0, Box.PANEL));
        base.put("EquipProxy", new Slot(Anchor.ML, 4, 134, Box.SMALL));
        base.put("recentactions", new Slot(Anchor.ML, 4, 150, Box.SLIM, false));

        /* Combat widgets: opponents on the right, buffs and actions near the
         * centre where the eye already is during a fight. */
        base.put("Fightview", new Slot(Anchor.MR, 4, -100, Box.TALL));
        base.put("FightBuffsInfo", new Slot(Anchor.MC, 0, -60, Box.BAR));
        base.put("FightActions", new Slot(Anchor.MC, 0, 40, Box.BAR));

        base.put("ChatUI", new Slot(Anchor.BL, 4, 4, Box.CHAT));
        base.put("menugrid", new Slot(Anchor.BR, 4, 4, Box.BIG));
        base.put("BeltProxy", new Slot(Anchor.BC, 0, 4 + (BELT_STEP * 4), Box.BELT, false));
        base.put("StudyReport", new Slot(Anchor.MC, 0, 0, Box.PANEL));

        /* MINIMAL: the vanilla-looking HUD. Everything the custom client adds on
         * top is hidden, so a newcomer sees roughly what the guides show. Hotbars
         * are never hidden here because their keybinds only fire while visible. */
        Map<String, Slot> minimal = new HashMap<>();
        for (String name : new String[]{"botsmenu", "EquipProxy", "recentactions",
                                        "BeltProxy", "alarm", "speedmeter", "drinkmeter"})
            minimal.put(name, base.get(name).hidden());
        diff.put(Preset.MINIMAL, minimal);

        /* BOTS: automation panels get the prime left-hand real estate and the
         * alarm widget is up, since that is what this client is used for. */
        Map<String, Slot> bots = new HashMap<>();
        /* Equipment moves up beside the meters to free the left band for the
         * automation panels, which is the whole point of this preset. */
        bots.put("EquipProxy", base.get("EquipProxy").at(Anchor.TL, 240, 4));
        bots.put("recentactions", new Slot(Anchor.ML, 4, 150, Box.SLIM));
        bots.put("BeltProxy", new Slot(Anchor.BC, 0, 4 + (BELT_STEP * 4), Box.BELT));
        diff.put(Preset.BOTS, bots);
    }

    /**
     * Player-facing widget names. In DRAG mode the labels used to be the internal
     * identifiers, so the HUD editor told you about "metergfx/hud/meter/nrj"
     * instead of "Energy" -- unreadable for exactly the audience that needs it.
     */
    private static final Map<String, String> titles = new HashMap<>();

    static {
        titles.put("portrait", "widget.portrait");
        titles.put("metergfx/hud/meter/hp", "widget.hp");
        titles.put("metergfx/hud/meter/stam", "widget.stam");
        titles.put("metergfx/hud/meter/nrj", "widget.energy");
        titles.put("drinkmeter", "widget.drink");
        titles.put("speedmeter", "widget.speed");
        titles.put("party", "widget.party");
        titles.put("compass", "widget.compass");
        titles.put("Calendar", "widget.calendar");
        titles.put("bufflist", "widget.buffs");
        titles.put("mainmenu", "widget.mainmenu");
        titles.put("minimap", "widget.minimap");
        titles.put("quests", "widget.quests");
        titles.put("alarm", "widget.alarm");
        titles.put("botsmenu", "widget.bots");
        titles.put("EquipProxy", "widget.equip");
        titles.put("recentactions", "widget.recent");
        titles.put("Fightview", "widget.fightview");
        titles.put("FightBuffsInfo", "widget.fightbuffs");
        titles.put("FightActions", "widget.fightactions");
        titles.put("ChatUI", "widget.chat");
        titles.put("menugrid", "widget.menugrid");
        titles.put("BeltProxy", "widget.beltproxy");
        titles.put("StudyReport", "widget.study");
    }

    /** Readable label for a widget, falling back to its internal name. */
    public static String title(String name) {
        String key = titles.get(name);
        if (key != null)
            return (nurgling.i18n.L10n.hasKey(key) ? nurgling.i18n.L10n.get(key) : name);
        Matcher m = BELT.matcher(name);
        if (m.matches() && nurgling.i18n.L10n.hasKey("widget.belt"))
            return (nurgling.i18n.L10n.get("widget.belt") + " " + (Integer.parseInt(m.group(1)) + 1));
        return (name);
    }

    /** Cascade slots handed out to widgets with no entry in the table. */
    private static final Map<String, Integer> cascade = new ConcurrentHashMap<>();

    public static Slot slot(String name) {
        return (slot(Preset.current(), name));
    }

    public static Slot slot(Preset preset, String name) {
        Map<String, Slot> over = diff.get(preset);
        if (over != null) {
            Slot s = over.get(name);
            if (s != null)
                return (s);
        }
        Slot s = base.get(name);
        if (s != null)
            return (s);
        /* Hotbars are configurable in number, so they are placed by index
         * rather than listed one by one. */
        Matcher m = BELT.matcher(name);
        if (m.matches()) {
            int i = Integer.parseInt(m.group(1));
            return (new Slot(Anchor.BC, 0, 4 + (BELT_STEP * i), Box.BELT));
        }
        return (null);
    }

    public static boolean defaultVis(String name) {
        Slot s = slot(name);
        return ((s == null) || s.vis);
    }

    /**
     * Resolve the default top-left corner for a widget. Falls back to a
     * diagonal cascade for unknown widgets so that new or third-party panels
     * stay individually reachable instead of stacking in the corner.
     */
    public static Coord resolve(String name, Coord wsz, Coord screen) {
        Slot s = slot(name);
        if (s == null)
            return (clamp(cascade(name), wsz, screen));
        return (clamp(place(s, UI.scale(s.off), wsz, screen), wsz, screen));
    }

    private static Coord place(Slot s, Coord off, Coord wsz, Coord screen) {
        int x, y;
        switch (s.anchor) {
            case TL: case ML: case BL:
                x = off.x;
                break;
            case TC: case MC: case BC:
                x = ((screen.x - wsz.x) / 2) + off.x;
                break;
            default:
                x = screen.x - wsz.x - off.x;
                break;
        }
        switch (s.anchor) {
            case TL: case TC: case TR:
                y = off.y;
                break;
            case ML: case MC: case MR:
                y = ((screen.y - wsz.y) / 2) + off.y;
                break;
            default:
                y = screen.y - wsz.y - off.y;
                break;
        }
        return (new Coord(x, y));
    }

    /** One schematic rectangle of a layout preview. */
    public static class Preview {
        public final Coord c, sz;

        Preview(Coord c, Coord sz) {
            this.c = c;
            this.sz = sz;
        }
    }

    /**
     * Lay the preset out on the {@link #design} reference screen using nominal
     * widget footprints, then scale the result into {@code area}. Combat-only
     * and on-demand widgets are left out: they are not part of what the player
     * is choosing between, and drawing them would just add noise.
     */
    public static List<Preview> preview(Preset preset, Coord area, int belts) {
        List<String> names = new ArrayList<>(base.keySet());
        for (int i = 0; i < belts; i++)
            names.add("belt" + i);
        names.removeAll(java.util.Arrays.asList("Fightview", "FightBuffsInfo",
                                                "FightActions", "StudyReport"));
        List<Preview> out = new ArrayList<>();
        for (String name : names) {
            Slot s = slot(preset, name);
            if ((s == null) || !s.vis)
                continue;
            Coord nom = s.box.sz;
            Coord c = place(s, s.off, nom, design);
            out.add(new Preview(scaleto(c, area), scaleto(nom, area)));
        }
        return (Collections.unmodifiableList(out));
    }

    private static Coord scaleto(Coord c, Coord area) {
        return (new Coord((c.x * area.x) / design.x, (c.y * area.y) / design.y));
    }

    private static Coord cascade(String name) {
        int i = cascade.computeIfAbsent(name, k -> cascade.size());
        int step = UI.scale(28);
        return (new Coord(UI.scale(24) + (step * i), UI.scale(24) + (step * i)));
    }

    private static Coord clamp(Coord c, Coord wsz, Coord screen) {
        int x = Math.min(c.x, screen.x - wsz.x);
        int y = Math.min(c.y, screen.y - wsz.y);
        return (new Coord(Math.max(0, x), Math.max(0, y)));
    }
}
