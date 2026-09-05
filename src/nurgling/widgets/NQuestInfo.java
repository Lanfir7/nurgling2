package nurgling.widgets;

import haven.*;
import haven.res.ui.locptr.Pointer;
import haven.res.ui.obj.buddy.Buddy;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NStyle;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.conf.FontSettings;
import nurgling.conf.NQuestTrackerProp;
import nurgling.i18n.L10n;
import nurgling.tools.QuestGiverDistance;
import nurgling.tools.QuestTrackFilter;
import nurgling.widgets.nsettings.Fonts;
import nurgling.widgets.quest.QCond;
import nurgling.widgets.quest.QuestCredoCounter;
import nurgling.widgets.quest.QuestKind;
import nurgling.widgets.quest.QuestMenu;
import nurgling.widgets.quest.QuestModel;
import nurgling.widgets.quest.QuestObjectiveAction;
import nurgling.widgets.quest.QuestObjectiveActionButton;
import nurgling.widgets.quest.QuestObjectiveActionResolver;
import nurgling.widgets.quest.QuestObjectiveRowLayout;
import nurgling.widgets.quest.QuestRowTheme;
import nurgling.widgets.quest.QuestHelperFilter;
import nurgling.widgets.quest.QuestTreeIconController;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The HUD quest tracker.
 *
 * A header of filters over a scrollable list of real row widgets, driven by
 * {@link QuestModel}. Groups are collapsed by default, so a character with twenty active
 * quests gets a dozen rows rather than sixty.
 *
 * The world-space side of this widget - {@link #huntingT}, {@link #forageT}, the marker
 * table and {@link #isQuestedItem} - is read from gob-tick threads by
 * {@link nurgling.NGob}, {@link nurgling.NGItem} and {@link haven.MiniMap}, so it is kept
 * separate from the view and published as immutable sets.
 */
public class NQuestInfo extends Widget
{
    /* ------------------------------------------------------------------ layout */

    private static final Coord PAD = UI.scale(new Coord(4, 3));
    private static final int INDENT = UI.scale(14);
    private static final int CHEV_W = UI.scale(10);
    private static final Coord CHIP_SZ = UI.scale(new Coord(17, 15));
    private static final Coord DEF_SZ = UI.scale(new Coord(252, 216));

    /* ------------------------------------------------------------------ overlay API */

    /**
     * Bumped whenever the tracked set changes. {@link nurgling.NGob} and {@link NGItem}
     * poll this to know when to re-evaluate their cached quest highlighting.
     */
    public final AtomicInteger lastUpdate = new AtomicInteger(0);

    /** Gob-name fragments of unfinished {@code Kill} objectives. Replaced wholesale, never mutated. */
    public volatile Set<String> huntingT = Collections.emptySet();
    /** Gob-name fragments of unfinished {@code Pick} objectives. */
    public volatile Set<String> forageT = Collections.emptySet();
    /** Lowercased item names of unfinished {@code Bring} objectives. */
    private volatile Set<String> bringItems = Collections.emptySet();

    /* ------------------------------------------------------------------ state */

    private final QuestModel model = new QuestModel();
    private final QuestObjectiveActionResolver actionResolver = new QuestObjectiveActionResolver();
    private final QuestTreeIconController treeIcons = new QuestTreeIconController();
    private NQuestTrackerProp prop = null;
    private NQuestTrackerProp fallback = null;
    private boolean needRebuild = true;

    private Scrollport body;
    private ICheckBox modebtn, searchbtn, gearbtn;
    private KindChip[] chips;
    private TextEntry searchbox;
    private String search = "";
    private int headerH = 0;

    private FontSettings fontsrc = null;
    private Text.Foundry groupFnd, condFnd;
    private int rowH = UI.scale(14);

    /** Giver names whose marker props we set last rebuild, so vanished ones can be cleared. */
    private final Set<String> markedGivers = new HashSet<>();
    /** The prop sets we published last rebuild, to tell a real change from a rebuild. */
    private final Map<String, HashSet<String>> markedProps = new HashMap<>();

    private double distAcc = 0;
    private int distKey = Integer.MIN_VALUE;
    private final HashMap<String, Coord2d> giverCoords = new HashMap<String, Coord2d>();
    private final HashSet<Integer> harvestTried = new HashSet<Integer>();
    private double harvestAcc = 0;

    public NQuestInfo()
    {
        super(DEF_SZ);
        fonts();
        modebtn = add(new NMiniMapWnd.NMenuCheckBox(
            "nurgling/hud/buttons/questmode", null, L10n.get("char.quest.switch_mode")));
        modebtn.changed(a -> {
            prop().mode = a ? NQuestTrackerProp.Mode.TASKS : NQuestTrackerProp.Mode.GIVERS;
            prop().save();
            needRebuild = true;
        });
        chips = new KindChip[] {
            add(new KindChip(QuestKind.NPC, "N", NStyle.questGiver, "Quests from quest givers")),
            add(new KindChip(QuestKind.CREDO, "C", NStyle.questCredo, "Credo quests")),
            add(new KindChip(QuestKind.WORLD, "W", NStyle.questWorld, "World quests")),
        };
        searchbtn = add(new NMiniMapWnd.NMenuCheckBox(
            "nurgling/hud/buttons/lsearch", null, "Search quests"));
        searchbtn.changed(a -> {
            search = "";
            if(searchbox != null)
                searchbox.settext("");
            relayout();
            needRebuild = true;
        });
        gearbtn = add(new NMiniMapWnd.NMenuCheckBox(
            "nurgling/hud/buttons/settings", null, "Tracker options"));
        gearbtn.changed(a -> {
            gearbtn.a = false;
            openGearMenu();
        });
        searchbox = add(new TextEntry(DEF_SZ.x - PAD.x * 2, "") {
            @Override
            protected void changed()
            {
                super.changed();
                NQuestInfo.this.search = text().trim().toLowerCase();
                NQuestInfo.this.needRebuild = true;
            }
        });
        searchbox.hide();
        body = add(new Scrollport(DEF_SZ));
        relayout();
    }

    /* ------------------------------------------------------------------ settings */

    private NQuestTrackerProp prop()
    {
        if(prop == null) {
            prop = (ui instanceof NUI) ? NQuestTrackerProp.get((NUI)ui) : null;
            if(prop == null) {
                // Character not resolved yet: run on defaults (which never persist, see
                // NQuestTrackerProp.save) and pick up the real settings once login finishes.
                if(fallback == null)
                    fallback = new NQuestTrackerProp("", "");
                return fallback;
            }
            modebtn.a = (prop.mode == NQuestTrackerProp.Mode.TASKS);
            for(KindChip c : chips)
                c.a = prop.kinds.contains(c.kind);
            // Settings arrived after the first rebuild ran on defaults - redo it with them.
            needRebuild = true;
        }
        return prop;
    }

    /** Rebuild the three text roles from the user's chosen Quests font. */
    private void fonts()
    {
        Object cur = NConfig.get(NConfig.Key.fonts);
        if(!(cur instanceof FontSettings) || cur == fontsrc)
            return;
        fontsrc = (FontSettings)cur;
        groupFnd = QuestHeadingFont.from(fontsrc.getFoundary(Fonts.FontType.QUESTS));
        java.awt.Font f = groupFnd.font;
        condFnd = new Text.Foundry(f.deriveFont(Math.max(8f, f.getSize2D() - UI.scale(1f))),
                                   NStyle.questCond).aa(true);
        rowH = groupFnd.height() + UI.scale(3);
        needRebuild = true;
    }

    /* ------------------------------------------------------------------ layout */

    @Override
    public void resize(Coord sz)
    {
        super.resize(sz);
        relayout();
        needRebuild = true;
    }

    private void relayout()
    {
        int x = PAD.x, top = PAD.y;
        modebtn.c = new Coord(x, top);
        x += modebtn.sz.x + PAD.x;
        for(KindChip c : chips) {
            c.c = new Coord(x, top + (modebtn.sz.y - c.sz.y) / 2);
            x += c.sz.x + UI.scale(2);
        }
        int rx = sz.x - PAD.x - gearbtn.sz.x;
        gearbtn.c = new Coord(rx, top);
        rx -= searchbtn.sz.x + PAD.x;
        searchbtn.c = new Coord(rx, top);

        int y = top + modebtn.sz.y + PAD.y;
        if(searchbtn.a) {
            searchbox.show();
            searchbox.resize(Math.max(UI.scale(40), sz.x - PAD.x * 2));
            searchbox.c = new Coord(PAD.x, y);
            y += searchbox.sz.y + PAD.y;
        } else {
            searchbox.hide();
        }
        headerH = y;
        body.c = new Coord(0, headerH);
        body.resize(new Coord(sz.x, Math.max(rowH, sz.y - headerH)));
    }

    /* ------------------------------------------------------------------ tick */

    @Override
    public void tick(double dt)
    {
        super.tick(dt);
        fonts();
        NGameUI gui = getparent(NGameUI.class);
        if(gui == null)
            gui = NUtils.getGameUI();
        if(model.tick(dt, (gui != null) ? gui.chrwdg : null))
            needRebuild = true;
        treeIcons.reconcile(QuestHelperFilter.visibleQuests(model.quests(), prop()),
                (gui != null) ? gui.iconconf : null);
        harvestPointers(gui);
        if(condsSweepIdle() && gui != null && gui.chrwdg != null) {
            harvestAcc += dt;
            if(harvestAcc > 0.45) {
                harvestAcc = 0;
                if(!gui.chrwdg.visible) {
                    Integer hid = nextHarvestQuest();
                    if(hid != null && gui.chrwdg.quest != null) {
                        harvestTried.add(hid);
                        gui.chrwdg.quest.wdgmsg("qsel", hid);
                    }
                }
            }
        }
        distAcc += dt;
        if(distAcc > 0.5) {
            distAcc = 0;
            int key = distanceKey();
            if(key != distKey) {
                distKey = key;
                needRebuild = true;
            }
        }
        if(needRebuild) {
            needRebuild = false;
            rebuild();
        }
    }

    @Override
    public void destroy()
    {
        NGameUI gui = getparent(NGameUI.class);
        if(gui == null)
            gui = NUtils.getGameUI();
        treeIcons.release((gui != null) ? gui.iconconf : null);
        super.destroy();
    }

    /* ------------------------------------------------------------------ view model */

    private static class Row
    {
        final String text;
        final boolean ready;
        final int questId;
        final boolean secondary;
        final QuestKind kind;
        final QCond cond;

        Row(String text, boolean ready, int questId, boolean secondary, QuestKind kind, QCond cond)
        {
            this.text = text;
            this.ready = ready;
            this.questId = questId;
            this.secondary = secondary;
            this.kind = kind;
            this.cond = cond;
        }
    }

    private static class Group
    {
        String key;
        String title;
        QuestKind kind = QuestKind.NPC;
        String giver;
        String questKey;
        int questId = -1;
        boolean ready;
        boolean idle;
        boolean pinned;
        int done, total;
        final List<Row> rows = new ArrayList<>();

        Color titleColor()
        {
            if(ready)
                return NStyle.questReady;
            if(idle)
                return NStyle.questGiverIdle;
            switch(kind) {
                case CREDO: return NStyle.questCredo;
                case WORLD: return NStyle.questWorld;
                default:    return NStyle.questGiver;
            }
        }
    }

    private void rebuild()
    {
        NQuestTrackerProp p = prop();
        List<Group> groups = (p.mode == NQuestTrackerProp.Mode.TASKS) ? taskGroups(p) : giverGroups(p);
        boolean overlays = applyMarkerProps();
        filterAndSort(groups, p);
        layoutRows(groups, p);
        QuestHelperFilter.OverlaySets s = QuestHelperFilter.overlayTargets(
                QuestHelperFilter.visibleQuests(model.quests(), p));
        if(!s.hunt.equals(huntingT) || !s.forage.equals(forageT) || !s.bring.equals(bringItems)) {
            huntingT = s.hunt;
            forageT = s.forage;
            bringItems = s.bring;
            overlays = true;
        }
        // Only wake the gob overlays when what they read actually changed - collapsing a group
        // is a view change, and should not make every gob in the world re-evaluate itself.
        if(overlays)
            lastUpdate.incrementAndGet();
    }

    /** Should this quest be considered at all, before per-group filtering? */
    private boolean visible(QuestModel.TQuest q, NQuestTrackerProp p)
    {
        return QuestHelperFilter.visible(q, p);
    }

    private List<Group> giverGroups(NQuestTrackerProp p)
    {
        Map<String, Group> byGiver = new LinkedHashMap<>();
        List<Group> out = new ArrayList<>();
        for(QuestModel.TQuest q : model.quests()) {
            if(!visible(q, p))
                continue;
            if(q.kind == QuestKind.CREDO || q.kind == QuestKind.WORLD || q.giver == null) {
                Group g = new Group();
                g.key = q.key();
                g.questKey = q.key();
                g.kind = q.kind;
                g.questId = q.id;
                g.title = QuestWnd.localizedTitle(q.title());
                g.ready = q.readyToTurnIn();
                for(QCond c : q.conds) {
                    if(c.verb == QCond.Verb.TELL)
                        continue;
                    g.rows.add(condRow(c, false, q.kind));
                }
                g.total = g.rows.size();
                g.done = 0;
                for(Row r : g.rows) {
                    if(r.ready)
                        g.done++;
                }
                out.add(g);
                continue;
            }
            Group g = group(byGiver, q.giver);
            g.questKey = q.key();
            if(g.questId < 0)
                g.questId = q.id;
            if(q.readyToTurnIn())
                g.ready = true;
            for(QCond c : q.conds) {
                if(c.verb == QCond.Verb.TELL)
                    continue;
                g.rows.add(condRow(c, false, q.kind));
            }
        }
        // Objectives that point at a giver but belong to somebody else's quest - "bring X to
        // Jenny" shows under Jenny too, so her group tells you what she is waiting for.
        for(QuestModel.TQuest q : model.quests()) {
            if(!visible(q, p))
                continue;
            for(QCond c : q.conds) {
                if(c.verb == QCond.Verb.TELL || c.ready || c.giver == null)
                    continue;
                String target = model.canonGiver(c.giver);
                if(target.equals(q.giver))
                    continue;
                Group g = group(byGiver, target);
                if(g.questId < 0)
                    g.questId = q.id;
                g.rows.add(condRow(c, true, q.kind));
            }
        }
        for(Group g : byGiver.values()) {
            g.title = labelName(g.giver);
            g.idle = true;
            g.total = g.rows.size();
            for(Row r : g.rows) {
                if(r.ready)
                    g.done++;
                if(!r.secondary)
                    g.idle = false;
            }
            out.add(g);
        }
        return out;
    }

    private Group group(Map<String, Group> byGiver, String name)
    {
        Group g = byGiver.get(name);
        if(g == null) {
            g = new Group();
            g.key = "giver:" + name;
            g.giver = name;
            g.title = name;
            g.kind = QuestKind.NPC;
            byGiver.put(name, g);
        }
        return g;
    }

    private static final Object[] TASK_CATS = {
        "Bring", "char.quest.section.bring", new QCond.Verb[] {QCond.Verb.BRING},
        "Foraging", "char.quest.section.foraging", new QCond.Verb[] {QCond.Verb.PICK},
        "Hunting", "char.quest.section.hunting", new QCond.Verb[] {QCond.Verb.KILL},
        "Conversation", "char.quest.section.conversation", new QCond.Verb[] {QCond.Verb.GREET, QCond.Verb.RAGE, QCond.Verb.WAVE, QCond.Verb.LAUGH},
        "Reward", "char.quest.section.reward", new QCond.Verb[] {QCond.Verb.TELL},
        "Attributes", "char.quest.section.attributes", new QCond.Verb[] {QCond.Verb.GAIN},
        "Craft", "char.quest.section.craft", new QCond.Verb[] {QCond.Verb.CREATE},
        "Other", "char.quest.section.other", new QCond.Verb[] {QCond.Verb.CAVE, QCond.Verb.LIGHT, QCond.Verb.OTHER},
    };

    private List<Group> taskGroups(NQuestTrackerProp p)
    {
        List<Group> out = new ArrayList<>();
        for(int i = 0; i < TASK_CATS.length; i += 3) {
            String name = (String)TASK_CATS[i];
            String l10nKey = (String)TASK_CATS[i + 1];
            Set<QCond.Verb> verbs = new HashSet<>(Arrays.asList((QCond.Verb[])TASK_CATS[i + 2]));
            Group g = new Group();
            g.key = "task:" + name;
            g.title = L10n.get(l10nKey);
            List<QCond> list = new ArrayList<>();
            Map<Integer, QuestKind> kinds = new HashMap<>();
            for(QuestModel.TQuest q : model.quests()) {
                if(!visible(q, p))
                    continue;
                for(QCond c : q.conds) {
                    if(c.ready || !verbs.contains(c.verb))
                        continue;
                    if(c.verb == QCond.Verb.TELL && !q.readyToTurnIn())
                        continue;
                    if(g.questId < 0)
                        g.questId = q.id;
                    list.add(c);
                    kinds.put(c.questId, q.kind);
                }
            }
            if(list.isEmpty())
                continue;
            Collections.sort(list, new Comparator<QCond>() {
                public int compare(QCond a, QCond b)
                {
                    return QuestGiverDistance.compareMeters(condMeters(a), condMeters(b));
                }
            });
            QuestModel.CredoProgress credo = model.pursuedCredoProgress();
            for(QCond c : list) {
                QuestKind kind = kinds.get(c.questId);
                String text = QuestCredoCounter.appendToAction(displayCond(c), kind, c.questId, credo);
                g.rows.add(new Row(text, c.ready, c.questId, false, kind, c));
            }
            g.total = g.rows.size();
            out.add(g);
        }
        return out;
    }

    private void filterAndSort(List<Group> groups, final NQuestTrackerProp p)
    {
        for(Iterator<Group> i = groups.iterator(); i.hasNext(); ) {
            Group g = i.next();
            if(g.giver != null && p.hiddenGivers.contains(g.giver)) {
                i.remove();
                continue;
            }
            if(g.giver != null && g.rows.isEmpty() && !g.ready) {
                i.remove();
                continue;
            }
            g.pinned = p.pinned.contains(g.key);
            if(!search.isEmpty() && !matches(g)) {
                i.remove();
            }
        }
        Collections.sort(groups, new Comparator<Group>() {
            public int compare(Group a, Group b)
            {
                if(a.pinned != b.pinned)
                    return a.pinned ? -1 : 1;
                if(a.ready != b.ready)
                    return a.ready ? -1 : 1;
                int ka = kindOrder(a.kind), kb = kindOrder(b.kind);
                if(ka != kb)
                    return ka - kb;
                if(a.idle != b.idle)
                    return a.idle ? 1 : -1;
                return String.CASE_INSENSITIVE_ORDER.compare(nz(a.title), nz(b.title));
            }
        });
    }

    private static String nz(String s)
    {
        return (s == null) ? "" : s;
    }

    private static int kindOrder(QuestKind k)
    {
        switch(k) {
            case CREDO: return 0;
            case NPC:   return 1;
            default:    return 2;
        }
    }

    private boolean matches(Group g)
    {
        if(nz(g.title).toLowerCase().contains(search))
            return true;
        for(Row r : g.rows) {
            if(r.text.toLowerCase().contains(search))
                return true;
        }
        return false;
    }

    /** Collapsed unless the player expanded it; the credo being pursued starts expanded. */
    private boolean collapsed(Group g, NQuestTrackerProp p)
    {
        if(!search.isEmpty())
            return false;
        if(p.collapsed.contains(g.key))
            return true;
        if(p.expanded.contains(g.key))
            return false;
        return !(g.kind == QuestKind.CREDO && g.questId == model.pursuedCredoId());
    }

    private void layoutRows(List<Group> groups, NQuestTrackerProp p)
    {
        for(Widget w = body.cont.child; w != null; ) {
            Widget next = w.next;
            w.destroy();
            w = next;
        }
        int w = body.cont.sz.x - PAD.x * 2;
        int y = 0, shown = 0, hidden = 0;
        for(Group g : groups) {
            boolean expand = !collapsed(g, p);
            if(!g.pinned && p.maxrows > 0 && shown >= p.maxrows) {
                hidden += 1 + (expand ? g.rows.size() : 0);
                continue;
            }
            add(new GroupRow(g, w, !expand), shown, y);
            y += rowH;
            shown++;
            if(!expand)
                continue;
            for(Row r : g.rows) {
                if(!g.pinned && p.maxrows > 0 && shown >= p.maxrows) {
                    hidden++;
                    continue;
                }
                add(new CondRow(r, w, g.kind), shown, y);
                y += rowH;
                shown++;
            }
        }
        boolean capped = hidden > 0;
        if(capped)
            add(new MoreRow(hidden, w), shown, y);
        else if(shown == 0)
            add(new EmptyRow(w), shown, y);
        body.cont.update();
    }

    private void add(ARow row, int idx, int y)
    {
        row.idx = idx;
        body.cont.add(row, new Coord(PAD.x, y));
    }

    /* ------------------------------------------------------------------ marker props */

    /**
     * Recompute the icon set drawn over each quest giver's map marker.
     * Mirrors the tags {@link nurgling.overlays.NQuestGiver} draws.
     */
    private boolean applyMarkerProps()
    {
        Map<String, HashSet<String>> props = new HashMap<>();
        NQuestTrackerProp p = prop();
        for(QuestModel.TQuest q : model.quests()) {
            if(!visible(q, p))
                continue;
            if(q.giver != null && q.readyToTurnIn())
                tag(props, q.giver, "tell");
            for(QCond c : q.conds) {
                if(c.ready || c.giver == null)
                    continue;
                String t = c.markerTag();
                if(t != null)
                    tag(props, model.canonGiver(c.giver), t);
            }
        }
        boolean changed = false;
        for(String gone : markedGivers) {
            if(!props.containsKey(gone)) {
                setMarkersProp(gone, null);
                changed = true;
            }
        }
        for(Map.Entry<String, HashSet<String>> e : props.entrySet()) {
            if(!e.getValue().equals(markedProps.get(e.getKey())))
                changed = true;
            setMarkersProp(e.getKey(), e.getValue());
        }
        markedGivers.clear();
        markedGivers.addAll(props.keySet());
        markedProps.clear();
        markedProps.putAll(props);
        return changed;
    }

    private static void tag(Map<String, HashSet<String>> props, String giver, String tag)
    {
        HashSet<String> s = props.get(giver);
        if(s == null)
            props.put(giver, s = new HashSet<>());
        s.add(tag);
    }

    /* ------------------------------------------------------------------ menus */

    private void openGearMenu()
    {
        final NQuestTrackerProp p = prop();
        List<QuestMenu.Item> items = new ArrayList<>();
        items.add(new QuestMenu.Item("Max rows: " + ((p.maxrows > 0) ? String.valueOf(p.maxrows) : "all"),
            () -> {
                p.maxrows = nextCap(p.maxrows);
                p.save();
                needRebuild = true;
            }));
        items.add(new QuestMenu.Item("Expand all", () -> {
            p.collapsed.clear();
            expandAllGroups(p);
            p.save();
            needRebuild = true;
        }));
        items.add(new QuestMenu.Item("Collapse all", () -> {
            p.expanded.clear();
            collapseAllGroups(p);
            p.save();
            needRebuild = true;
        }));
        items.add(new QuestMenu.Item(L10n.get("char.quest.refresh"), () -> refreshDistances()));
        if(!p.hiddenQuests.isEmpty() || !p.hiddenGivers.isEmpty()) {
            items.add(new QuestMenu.Item(
                "Unhide all (" + (p.hiddenQuests.size() + p.hiddenGivers.size()) + ")", () -> {
                    p.hiddenQuests.clear();
                    p.hiddenGivers.clear();
                    p.save();
                    needRebuild = true;
                }));
        }
        if(!p.pinned.isEmpty()) {
            items.add(new QuestMenu.Item("Clear pins (" + p.pinned.size() + ")", () -> {
                p.pinned.clear();
                p.save();
                needRebuild = true;
            }));
        }
        popup(items);
    }

    private void expandAllGroups(NQuestTrackerProp p)
    {
        for(Widget w = body.cont.child; w != null; w = w.next) {
            if(w instanceof GroupRow)
                p.expanded.add(((GroupRow)w).group.key);
        }
    }

    private void collapseAllGroups(NQuestTrackerProp p)
    {
        for(Widget w = body.cont.child; w != null; w = w.next) {
            if(w instanceof GroupRow)
                p.collapsed.add(((GroupRow)w).group.key);
        }
    }

    private void popup(List<QuestMenu.Item> items)
    {
        if(items.isEmpty())
            return;
        ui.root.add(new QuestMenu(items), ui.mc);
    }

    private static int nextCap(int cur)
    {
        if(cur <= 0)
            return 8;
        if(cur < 12)
            return 12;
        if(cur < 20)
            return 20;
        if(cur < 30)
            return 30;
        return 0;
    }

    private void openQuest(int questId)
    {
        NGameUI gui = getparent(NGameUI.class);
        if(gui == null || gui.chrwdg == null || questId < 0)
            return;
        gui.chrwdg.show();
        gui.chrwdg.raise();
        gui.chrwdg.questtab.showtab();
        if(gui.chrwdg.quest != null)
            gui.chrwdg.quest.wdgmsg("qsel", questId);
    }

    private void rowMenu(final Group g)
    {
        final NQuestTrackerProp p = prop();
        List<QuestMenu.Item> items = new ArrayList<>();
        final boolean pinned = p.pinned.contains(g.key);
        items.add(new QuestMenu.Item(pinned ? "Unpin" : "Pin to top", () -> {
            if(pinned)
                p.pinned.remove(g.key);
            else
                p.pinned.add(g.key);
            p.save();
            needRebuild = true;
        }));
        // Only offered for a group that IS one quest - a giver group can hold several, and
        // "hide this quest" would silently pick one of them.
        if(g.giver == null && g.questKey != null) {
            items.add(new QuestMenu.Item("Hide this quest", () -> {
                p.hiddenQuests.add(g.questKey);
                p.save();
                needRebuild = true;
            }));
        }
        if(g.giver != null) {
            items.add(new QuestMenu.Item("Hide everything from " + g.giver, () -> {
                p.hiddenGivers.add(g.giver);
                p.save();
                needRebuild = true;
            }));
        }
        if(g.questId >= 0)
            items.add(new QuestMenu.Item("Open in Quest Log", () -> openQuest(g.questId)));
        popup(items);
    }

    /* ------------------------------------------------------------------ rows */

    private static String elide(Text.Foundry f, String s, int maxw)
    {
        if(maxw <= 0)
            return "…";
        if(f.strsize(s).x <= maxw)
            return s;
        int lo = 0, hi = s.length();
        while(lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if(f.strsize(s.substring(0, mid) + "…").x <= maxw)
                lo = mid;
            else
                hi = mid - 1;
        }
        return (lo <= 0) ? "…" : (s.substring(0, lo).trim() + "…");
    }

    private abstract class ARow extends Widget
    {
        final QuestRowTheme theme;
        boolean hover = false;
        int idx = 0;

        ARow(int w, QuestKind kind)
        {
            this(w, QuestRowTheme.forKind(kind));
        }

        ARow(int w, QuestRowTheme theme)
        {
            super(new Coord(w, rowH));
            this.theme = theme;
        }

        @Override
        public void mousemove(MouseMoveEvent ev)
        {
            hover = ev.c.isect(Coord.z, sz);
            super.mousemove(ev);
        }

        void band(GOut g)
        {
            g.chcolor(((idx % 2) == 0) ? NStyle.rowEven : NStyle.rowOdd);
            g.frect(Coord.z, sz);
            if(theme.emphasized) {
                g.chcolor(theme.background);
                g.frect(Coord.z, sz);
                g.chcolor(theme.accent);
                g.frect(Coord.z, new Coord(Math.max(2, UI.scale(3)), sz.y));
            }
            if(hover) {
                g.chcolor(NStyle.questHover);
                g.frect(Coord.z, sz);
            }
            g.chcolor();
        }

        int ty(Tex t)
        {
            return (sz.y - t.sz().y) / 2;
        }
    }

    private class GroupRow extends ARow
    {
        final Group group;
        final boolean collapsed;
        private final Tex chev, title, counter, badge;
        private final int titleX, badgeW;

        GroupRow(Group g, int w, boolean collapsed)
        {
            super(w, g.kind);
            this.group = g;
            this.collapsed = collapsed;
            this.chev = groupFnd.render(collapsed ? "▸" : "▾", NStyle.questDim).tex();
            String pin = g.pinned ? "◆ " : "";
            String cnt = QuestCredoCounter.forGroup(g.kind, g.questId, g.done, g.total, model.pursuedCredoProgress());
            this.counter = cnt.isEmpty() ? null : condFnd.render(cnt, NStyle.questDim).tex();
            int cw = (counter != null) ? counter.sz().x + UI.scale(6) : 0;
            this.badge = theme.emphasized
                ? condFnd.render(L10n.get(theme.badgeKey), NStyle.infoBg).tex()
                : null;
            this.badgeW = (badge != null) ? badge.sz().x + UI.scale(8) : 0;
            this.titleX = CHEV_W + badgeW + ((badge != null) ? UI.scale(5) : 0);
            this.title = groupFnd.render(
                elide(groupFnd, pin + nz(g.title), w - titleX - cw), g.titleColor()).tex();
        }

        @Override
        public void draw(GOut g)
        {
            band(g);
            g.image(chev, new Coord(0, ty(chev)));
            if(badge != null) {
                int bh = badge.sz().y + UI.scale(2);
                int by = (sz.y - bh) / 2;
                g.chcolor(theme.accent);
                g.frect(new Coord(CHEV_W, by), new Coord(badgeW, bh));
                g.chcolor();
                g.image(badge, new Coord(CHEV_W + UI.scale(4), ty(badge)));
            }
            g.image(title, new Coord(titleX, ty(title)));
            if(counter != null)
                g.image(counter, new Coord(sz.x - counter.sz().x, ty(counter)));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev)
        {
            if(ev.b == 3) {
                rowMenu(group);
                return true;
            }
            if(ev.b == 1) {
                NQuestTrackerProp p = prop();
                if(collapsed) {
                    p.collapsed.remove(group.key);
                    p.expanded.add(group.key);
                } else {
                    p.expanded.remove(group.key);
                    p.collapsed.add(group.key);
                }
                p.save();
                needRebuild = true;
                return true;
            }
            if(ev.b == 2) {
                openQuest(group.questId);
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public Object tooltip(Coord c, Widget prev)
        {
            return nz(group.title) + " - left-click to " + (collapsed ? "expand" : "collapse")
                 + ", right-click for options";
        }
    }

    private class CondRow extends ARow
    {
        final Row row;
        private final Tex glyph, text;
        private final String full;
        private final QuestObjectiveActionButton actionButton;

        CondRow(Row r, int w, QuestKind groupKind)
        {
            super(w, QuestRowTheme.forObjective(groupKind, r.kind));
            this.row = r;
            this.full = r.text;
            Color col = theme.conditionColor(r.ready, r.secondary);
            this.glyph = condFnd.render(r.ready ? "✓" : "•", col).tex();
            int off = INDENT + glyph.sz().x + UI.scale(4);
            QuestObjectiveAction potential = actionResolver.resolve(r.cond);
            if(potential != null) {
                actionButton = add(new QuestObjectiveActionButton(r.cond));
                actionButton.c = new Coord(w - actionButton.sz.x - UI.scale(2), (rowH - actionButton.sz.y) / 2);
            } else {
                actionButton = null;
            }
            int textWidth = QuestObjectiveRowLayout.textWidth(w, off, actionButton != null);
            this.text = condFnd.render(elide(condFnd, r.text, textWidth), col).tex();
        }

        @Override
        public void draw(GOut g)
        {
            band(g);
            g.image(glyph, new Coord(INDENT, ty(glyph)));
            g.image(text, new Coord(INDENT + glyph.sz().x + UI.scale(4), ty(text)));
            super.draw(g);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev)
        {
            if(ev.propagate(this))
                return true;
            if(ev.b == 1) {
                openQuest(row.questId);
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public Object tooltip(Coord c, Widget prev)
        {
            return full;
        }
    }

    private class MoreRow extends ARow
    {
        private final Tex text;

        MoreRow(int n, int w)
        {
            super(w, (QuestKind)null);
            this.text = condFnd.render("+ " + n + " more…", NStyle.questDim).tex();
        }

        @Override
        public void draw(GOut g)
        {
            band(g);
            g.image(text, new Coord(INDENT, ty(text)));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev)
        {
            if(ev.b == 1) {
                NQuestTrackerProp p = prop();
                p.maxrows = 0;
                p.save();
                needRebuild = true;
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public Object tooltip(Coord c, Widget prev)
        {
            return "Click to show every row (max rows: unlimited)";
        }
    }

    private class EmptyRow extends ARow
    {
        private final Tex text;

        EmptyRow(int w)
        {
            super(w, (QuestKind)null);
            this.text = condFnd.render("No quests to show", NStyle.questDim).tex();
        }

        @Override
        public void draw(GOut g)
        {
            g.image(text, new Coord(INDENT, ty(text)));
        }
    }

    /** Toggle for one {@link QuestKind}. Compact on purpose - the panel can be narrow. */
    private class KindChip extends ACheckBox
    {
        final QuestKind kind;
        private final Color col;
        private final String tip;
        private final Tex on, off;
        private boolean hover = false;

        KindChip(QuestKind kind, String letter, Color col, String tip)
        {
            super(CHIP_SZ);
            this.kind = kind;
            this.col = col;
            this.tip = tip;
            this.a = true;
            Text.Foundry f = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 10).aa(true);
            this.on = f.render(letter, NStyle.infoBg).tex();
            this.off = f.render(letter, col).tex();
        }

        @Override
        public void draw(GOut g)
        {
            g.chcolor(a ? col : NStyle.titleBg);
            g.frect(Coord.z, sz);
            g.chcolor(a ? col : NStyle.questDim);
            g.rect(Coord.z, sz);
            g.chcolor();
            Tex t = a ? on : off;
            g.image(t, sz.sub(t.sz()).div(2));
            if(hover) {
                g.chcolor(NStyle.questHover);
                g.frect(Coord.z, sz);
                g.chcolor();
            }
        }

        @Override
        public void mousemove(MouseMoveEvent ev)
        {
            hover = ev.c.isect(Coord.z, sz);
            super.mousemove(ev);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev)
        {
            if(ev.b == 1) {
                a = !a;
                NQuestTrackerProp p = prop();
                if(a)
                    p.kinds.add(kind);
                else
                    p.kinds.remove(kind);
                p.save();
                needRebuild = true;
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public Object tooltip(Coord c, Widget prev)
        {
            return tip;
        }
    }

    /* ------------------------------------------------------------------ drawing */

    @Override
    public void draw(GOut g)
    {
        NDraggableWidget.drawBg(g, sz, ui);
        g.chcolor(NStyle.titleBg);
        g.frect(Coord.z, new Coord(sz.x, headerH));
        g.chcolor(NStyle.separator);
        g.frect(new Coord(0, headerH - UI.scale(1)), new Coord(sz.x, UI.scale(1)));
        g.chcolor();
        super.draw(g);
        int bw = Math.max(2, UI.scale(2));
        g.chcolor(NStyle.border);
        g.frect(Coord.z, new Coord(sz.x, bw));
        g.frect(new Coord(0, sz.y - bw), new Coord(sz.x, bw));
        g.frect(Coord.z, new Coord(bw, sz.y));
        g.frect(new Coord(sz.x - bw, 0), new Coord(bw, sz.y));
        g.chcolor();
    }

    /* ------------------------------------------------------------------ server hooks */

    /** From {@code Quest.Box.uimsg("conds")} via {@link nurgling.NUtils#setQuestConds}. */
    public void updateConds(int id, Object[] args)
    {
        model.setConds(id, args);
    }

    /** From {@code QuestWnd.uimsg} via {@link nurgling.NUtils#removeQuest}. */
    public void removeQuest(int id)
    {
        model.removeQuest(id);
    }

    /** From {@code QuestWnd.uimsg} via {@link nurgling.NUtils#addQuest}. */
    public void addQuest(int id)
    {
        model.addQuest(id);
    }

    /** From {@link QuestTrackFilter#notifyHelper}: mute set changed. */
    public void requestUpdate()
    {
        needRebuild = true;
    }

    private Row condRow(QCond c, boolean secondary, QuestKind kind)
    {
        return new Row(displayCond(c), c.ready, c.questId, secondary, kind, c);
    }

    private String displayCond(QCond c)
    {
        String base;
        if(c.verb == QCond.Verb.TELL) {
            String name = (c.giver != null) ? model.canonGiver(c.giver) : c.text;
            base = QuestWnd.returnToLabel(name);
        } else {
            base = QuestWnd.localizeCond(c.text);
        }
        return QuestGiverDistance.withMeters(base, condMeters(c));
    }

    private String labelName(String name)
    {
        return QuestGiverDistance.withMeters(name, distanceTo(name));
    }

    private Double condMeters(QCond cond)
    {
        if(cond.giver == null)
            return null;
        return distanceTo(model.canonGiver(cond.giver));
    }

    private Double distanceTo(String name)
    {
        try {
            NGameUI gui = gui();
            Pointer ptr = findPointer(gui, name);
            if(ptr != null) {
                try {
                    double d = ptr.getDistance();
                    if(d > 0)
                        return d;
                } catch(RuntimeException ignored) {
                }
            }
            Coord2d at = findGiverPos(name);
            if(at == null)
                return null;
            Gob player = (gui != null && gui.map != null) ? gui.map.player() : null;
            if(player == null)
                return null;
            return QuestGiverDistance.meters(player.rc.dist(at));
        } catch(Loading l) {
            return null;
        }
    }

    private NGameUI gui()
    {
        NGameUI gui = getparent(NGameUI.class);
        return (gui != null) ? gui : NUtils.getGameUI();
    }

    private Pointer findPointer(NGameUI gui, String name)
    {
        if(gui == null || name == null || name.isEmpty())
            return null;
        Pointer hit = findPointerUnder(gui, name);
        if(hit != null)
            return hit;
        if(gui.ui != null && gui.ui.root != null)
            return findPointerUnder(gui.ui.root, name);
        return null;
    }

    private Pointer findPointerUnder(Widget root, String name)
    {
        for(Pointer p : root.children(Pointer.class)) {
            if(QuestGiverDistance.namesMatch(name, p.tip()))
                return p;
        }
        return null;
    }

    private Coord2d findGiverPos(String name)
    {
        if(name == null || name.isEmpty())
            return null;
        Coord2d cached = cachedGiverPos(name);
        if(cached != null)
            return cached;
        NGameUI gui = gui();
        Pointer ptr = findPointer(gui, name);
        if(ptr != null) {
            try {
                Coord2d tc = ptr.tc();
                if(tc != null) {
                    giverCoords.put(name, tc);
                    return tc;
                }
            } catch(RuntimeException ignored) {
            }
        }
        Coord2d gobAt = findGobPos(gui, name);
        if(gobAt != null) {
            giverCoords.put(name, gobAt);
            return gobAt;
        }
        synchronized(markers) {
            for(MarkerInfo mi : markers) {
                if(name.equals(mi.name) && mi.coord != null)
                    return mi.coord;
            }
        }
        return findMapMarker(gui, name);
    }

    private Coord2d cachedGiverPos(String name)
    {
        Coord2d exact = giverCoords.get(name);
        if(exact != null)
            return exact;
        for(Map.Entry<String, Coord2d> e : giverCoords.entrySet()) {
            if(QuestGiverDistance.namesMatch(name, e.getKey()))
                return e.getValue();
        }
        return null;
    }

    private Coord2d findGobPos(NGameUI gui, String name)
    {
        if(gui == null || gui.map == null || gui.map.glob == null)
            return null;
        synchronized(gui.map.glob.oc) {
            for(Gob gob : gui.map.glob.oc) {
                if(gobNamed(gob, name))
                    return gob.rc;
            }
        }
        return null;
    }

    private static boolean gobNamed(Gob gob, String name)
    {
        if(gob.ngob != null && name.equals(gob.ngob.name))
            return true;
        String kin = NGameUI.gobIdToKinName.get(gob.id);
        if(name.equals(kin))
            return true;
        Buddy buddy = gob.getattr(Buddy.class);
        if(buddy != null) {
            if(name.equals(buddy.rnm))
                return true;
            if(buddy.b != null && name.equals(buddy.b.name))
                return true;
        }
        return false;
    }

    private Coord2d findMapMarker(NGameUI gui, String name)
    {
        if(gui == null || gui.mapfile == null || gui.mapfile.file == null || gui.mapfile.view == null)
            return null;
        MiniMap.Location sessloc = gui.mapfile.view.sessloc;
        if(sessloc == null)
            return gui.mapfile.findMarkerPosition(name);
        MapFile file = gui.mapfile.file;
        if(!file.lock.readLock().tryLock())
            return gui.mapfile.findMarkerPosition(name);
        try {
            Coord2d exact = null, fuzzy = null;
            for(MapFile.Marker mark : file.markers) {
                if(mark.nm == null || mark.seg != sessloc.seg.id)
                    continue;
                Coord2d pos = mark.tc.sub(sessloc.tc).mul(MCache.tilesz).add(MCache.tilesz.div(2));
                if(name.equals(mark.nm))
                    exact = pos;
                else if(fuzzy == null && QuestGiverDistance.namesMatch(name, mark.nm))
                    fuzzy = pos;
            }
            if(exact != null)
                return exact;
            if(fuzzy != null)
                return fuzzy;
        } finally {
            file.lock.readLock().unlock();
        }
        return gui.mapfile.findMarkerPosition(name);
    }

    private void harvestPointers(NGameUI gui)
    {
        if(gui == null)
            return;
        harvestPointersUnder(gui);
        if(gui.ui != null && gui.ui.root != null)
            harvestPointersUnder(gui.ui.root);
    }

    private void harvestPointersUnder(Widget root)
    {
        for(Pointer p : root.children(Pointer.class)) {
            String tip = p.tip();
            if(tip == null || tip.isEmpty())
                continue;
            try {
                Coord2d tc = p.tc();
                if(tc != null)
                    giverCoords.put(tip, tc);
            } catch(RuntimeException ignored) {
            }
        }
    }

    private boolean condsSweepIdle()
    {
        for(QuestModel.TQuest q : model.quests()) {
            if(!q.condsLoaded || q.condsStale)
                return false;
        }
        return true;
    }

    private Integer nextHarvestQuest()
    {
        for(QuestModel.TQuest q : model.quests()) {
            if(harvestTried.contains(q.id))
                continue;
            for(QCond c : q.conds) {
                if(c.ready || c.giver == null)
                    continue;
                if(findGiverPos(model.canonGiver(c.giver)) == null)
                    return q.id;
            }
        }
        return null;
    }

    private int distanceKey()
    {
        int h = 1;
        NGameUI gui = gui();
        Gob player = (gui != null && gui.map != null) ? gui.map.player() : null;
        if(player != null)
            h = 31 * h + QuestGiverDistance.tileKey(player.rc.x, player.rc.y);
        for(QuestModel.TQuest q : model.quests()) {
            for(QCond c : q.conds) {
                if(c.ready || c.giver == null)
                    continue;
                String nm = model.canonGiver(c.giver);
                Double m = distanceTo(nm);
                h = 31 * h + nm.hashCode();
                h = 31 * h + (m == null ? 0 : (int)Math.round(m));
            }
        }
        return h;
    }

    private void refreshDistances()
    {
        giverCoords.clear();
        harvestTried.clear();
        distKey = Integer.MIN_VALUE;
        needRebuild = true;
    }

    /* ------------------------------------------------------------------ overlay queries */

    public boolean isHuntingTarget(String target)
    {
        return matchesAny(huntingT, target);
    }

    public boolean isForageTarget(String target)
    {
        return matchesAny(forageT, target);
    }

    private static boolean matchesAny(Set<String> set, String target)
    {
        if(target == null)
            return false;
        for(String s : set) {
            if(target.contains(s))
                return true;
        }
        return false;
    }

    public boolean isQuestedItem(NGItem item)
    {
        String nm = (item == null) ? null : item.name();
        if(nm == null)
            return false;
        String lc = nm.toLowerCase();
        for(String want : bringItems) {
            if(lc.contains(want))
                return true;
        }
        return false;
    }

    /* ------------------------------------------------------------------ markers */

    public class MarkerInfo
    {
        public String name;
        public Coord2d coord;
        public long seg;
        public HashSet<String> prop;

        public MarkerInfo(String name, Coord2d coord, long seg)
        {
            this.name = name;
            this.coord = coord;
            this.seg = seg;
        }
    }

    private final HashSet<MarkerInfo> markers = new HashSet<>();

    public void addMarkerCoord(Coord2d tmp, String nm, long seg)
    {
        model.noteGiverName(nm);
        synchronized(markers) {
            for(MarkerInfo mi : markers) {
                if(mi.name.equals(nm)) {
                    mi.coord = tmp;
                    mi.seg = seg;
                    return;
                }
            }
            markers.add(new MarkerInfo(nm, tmp, seg));
        }
        lastUpdate.incrementAndGet();
    }

    public MarkerInfo getMarkerInfo(NGameUI gui, Gob gob)
    {
        if(gui == null || gui.mapfile == null || gob == null)
            return null;
        synchronized(markers) {
            for(MarkerInfo mi : markers) {
                if(mi.coord != null && gui.mapfile.playerSegmentId() == mi.seg
                   && gob.rc.dist(mi.coord) < 1)
                    return mi;
            }
        }
        return null;
    }

    void setMarkersProp(String name, HashSet<String> props)
    {
        if(name == null)
            return;
        synchronized(markers) {
            for(MarkerInfo mi : markers) {
                if(mi.name != null && mi.name.equals(name)) {
                    mi.prop = props;
                    return;
                }
            }
            MarkerInfo mi = new MarkerInfo(name, null, -1);
            mi.prop = props;
            markers.add(mi);
        }
    }

    @Override
    public void dispose()
    {
        synchronized(markers) {
            markers.clear();
        }
        super.dispose();
    }
}
