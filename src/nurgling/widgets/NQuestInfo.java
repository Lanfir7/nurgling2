package nurgling.widgets;

import haven.*;
import haven.Window;
import haven.res.ui.locptr.Pointer;
import haven.res.ui.obj.buddy.Buddy;
import nurgling.NConfig;
import static haven.PType.*;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NStyle;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.tools.QuestGiverDistance;
import nurgling.tools.QuestRewardFilter;
import nurgling.tools.QuestTrackFilter;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static haven.ItemInfo.catimgsh;
import static nurgling.widgets.NDraggableWidget.drawBg;

public class NQuestInfo extends Widget
{

    Text.Furnace fnd2 = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 14, Color.white).aa(true), 2, 1, Color.BLACK);
    Text.Furnace fnd1 = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 14, new Color(222, 205, 171)).aa(true), 2, 1, Color.BLACK);
    Text.Furnace gfnd2_under = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 14, new Color(222, 205, 171)).aa(true), 2, 1, Color.BLACK);
    public static final RichText.Foundry numfnd1 = new RichText.Foundry(new ChatUI.ChatParser(TextAttribute.FONT, Text.dfont.deriveFont(UI.scale(18f)), TextAttribute.FOREGROUND, Color.YELLOW));
    Text.Furnace active_title = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 18, new Color(217, 127, 59)).aa(true), 2, 1, new Color(94, 56, 56));
    Text.Furnace unactive_title = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 18, new Color(147, 131, 131)).aa(true), 2, 1, new Color(94, 56, 56));
    Text.Furnace credo_title = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 18, new Color(126, 198, 194)).aa(true), 2, 1, new Color(94, 56, 56));

    public NQuestInfo() {
        super();
        lastUpdate.set(0);
        huntingT.clear();
        forageT.clear();
        Widget prev = add(modebtn = new NMiniMapWnd.NMenuCheckBox("nurgling/hud/buttons/questmode", null, L10n.get("char.quest.switch_mode")), UI.scale(margin.x)/2, UI.scale(margin.y)/2).changed(a -> {mode = (mode == Mode.QUESTGIVERS?Mode.TASKS:Mode.QUESTGIVERS);needUpdate.set(true);});
        prev = add(hidebtn = new NMiniMapWnd.NMenuCheckBox("nurgling/hud/buttons/eye", null, L10n.get("char.quest.hide_credo")), prev.pos("ur")).changed(a -> {NConfig.set(NConfig.Key.hidecredo,a);needUpdate.set(true);});
        hidebtn.a = (boolean) NConfig.get(NConfig.Key.hidecredo);
        refreshbtn = add(new NMiniMapWnd.NMenuCheckBox("nurgling/hud/buttons/inv/search", null, L10n.get("char.quest.refresh")), prev.pos("ur"));
        refreshbtn.click(() -> refreshDistances());
    }
    NMiniMapWnd.NMenuCheckBox modebtn = null;
    NMiniMapWnd.NMenuCheckBox hidebtn = null;
    NMiniMapWnd.NMenuCheckBox refreshbtn = null;
    enum Mode
    {
        QUESTGIVERS,
        TASKS
    }


    Mode mode = Mode.QUESTGIVERS;
    private Collection<QuestImage> imgs = new ArrayList<>();
    HashMap<String,QuestGiver> qgconds = new HashMap<String,QuestGiver>();
    HashMap<Condition.State,Targets> taskconds = new HashMap<Condition.State,Targets>();
    private Tex glowon = null;
    public static final AtomicInteger lastUpdate = new AtomicInteger(0);
    private final Set<String> items = new HashSet<>();
    class Targets
    {
        ArrayList<Condition> conditions = new ArrayList<Condition>();
    }

    class QuestGiver
    {
        ArrayList<Condition> myConditions = new ArrayList<Condition>();
        ArrayList<Condition> otherConditions = new ArrayList<Condition>();

        int completed = 0;
        int uncompleted = 0;
    }

    public static boolean isHuntingTarget(String target)
    {
        if(target!=null)
            for(String ht:huntingT)
            {
                if(target.contains(ht))
                {
                    return true;
                }
            }
        return false;
    }

    public static boolean isForageTarget(String target)
    {
        if(target!=null)
            for(String ht:forageT)
            {
                if(target.contains(ht))
                {
                    return true;
                }
            }
        return false;
    }

    @Override
    public void dispose() {
        markers.clear();
        super.dispose();
    }

    void update() {
        imgs.clear();
        for (String qname : qgconds.keySet()) {
            setMarkersProp(qname, null);
        }
        qgconds.clear();
        taskconds.clear();
        huntingT.clear();
        items.clear();
        forageT.clear();
        for(Condition.State st: Condition.State.values()) {
            taskconds.put(st, new Targets());
        }
        QuestGiver credo = new QuestGiver();
        for (NQuest quest : quests.values()) {
            if (QuestTrackFilter.isMutedTitle(helperTitle(quest)))
                continue;
            boolean isReady = true;
            boolean tellPending = false;
            Condition tellCond = null;
            for (Condition cond : quest.conditions) {
                Condition.QuestsGiver qg = null;
                if (cond.state == Condition.State.TELL) {
                    quest.questGiver = ((Condition.QuestsGiver) cond.attrs.get(Condition.QuestsGiver.class)).name;
                    if (!cond.ready) {
                        tellPending = true;
                        tellCond = cond;
                    }
                }
                else if ((qg = ((Condition.QuestsGiver) cond.attrs.get(Condition.QuestsGiver.class)))!=null)
                {
                    if (!qgconds.containsKey(qg.name)) {
                        qgconds.put(qg.name, new QuestGiver());
                    }
                }
                if(cond.state == Condition.State.BRING && !cond.ready)
                {
                    items.add(((Condition.BringItem) cond.attrs.get(Condition.BringItem.class)).name);
                }
                if(!cond.ready) {
                    if (cond.state == Condition.State.KILL) {
                        huntingT.add(((Condition.HuntTarget) cond.attrs.get(Condition.HuntTarget.class)).name);
                    } else if (cond.state == Condition.State.PICK) {
                        forageT.add(((Condition.PickTarget) cond.attrs.get(Condition.PickTarget.class)).name);
                    }
                    if (cond.state != Condition.State.TELL)
                        isReady = false;
                }
                if(cond.state!=null && !cond.ready && cond.state != Condition.State.TELL)
                    taskconds.get(cond.state).conditions.add(cond);
            }
            if (QuestRewardFilter.isTurnIn(tellPending, !isReady) && tellCond != null)
                taskconds.get(Condition.State.TELL).conditions.add(tellCond);
            if (quest.questGiver != null) {
                QuestGiver qg;
                if (!qgconds.containsKey(quest.questGiver)) {
                    qgconds.put(quest.questGiver, new QuestGiver());
                }
                qg = qgconds.get(quest.questGiver);

                if (isReady) {
                    qg.completed++;
                } else {
                    qg.uncompleted++;
                }
            }
        }

        for (NQuest quest : quests.values()) {
            if (QuestTrackFilter.isMutedTitle(helperTitle(quest)))
                continue;
            for (Condition cond : quest.conditions) {
                if (quest.questGiver != null) {
                    if (qgconds.containsKey(quest.questGiver)) {
                        qgconds.get(quest.questGiver).myConditions.add(cond);
                    }
                    Condition.QuestsGiver qg = (Condition.QuestsGiver)cond.attrs.get(Condition.QuestsGiver.class);
                    if(qg!=null && cond.state!= Condition.State.TELL)
                    {
                        qgconds.get(qg.name).otherConditions.add(cond);
                    }
                }
                else
                {
                    if(!(Boolean)NConfig.get(NConfig.Key.hidecredo))
                        credo.myConditions.add(cond);
                }
            }
        }
        for (String qname : qgconds.keySet()) {
            QuestGiver qg = qgconds.get(qname);
            HashSet<String> prop = new HashSet<>();
            if(qg.completed!=0)
            {
                prop.add("tell");
            }
            for(Condition cond : qg.otherConditions) {
                if(!cond.ready) {
                    if (cond.state == Condition.State.BRING) {
                        prop.add("bring");
                    } else if (cond.state == Condition.State.GREET || cond.state == Condition.State.VISIT) {
                        prop.add("greet");
                    } else if (cond.state == Condition.State.RAGE) {
                        prop.add("rage");
                    } else if (cond.state == Condition.State.WAVE) {
                        prop.add("wave");
                    } else if (cond.state == Condition.State.LAUGH) {
                        prop.add("laugh");
                    }
                }
            }
            setMarkersProp(qname, prop);
        }
        if (mode == Mode.QUESTGIVERS) {
            if(!credo.myConditions.isEmpty()) {
                imgs.add(new QuestImage(credo_title.render(L10n.get("char.quest.credo")).img, -1));
                for (Condition cond : credo.myConditions)
                {
                    if(!cond.ready)
                        imgs.add(new QuestImage(fnd1.render(QuestWnd.localizeCond(cond.target)).img, cond.questId));
                }
            }
            for (String qname : qgconds.keySet()) {
                QuestGiver qg = qgconds.get(qname);
                if (!qg.myConditions.isEmpty()) {
                    imgs.add(new QuestImage(catimgsh(5, active_title.render(labelName(qname)).img, numfnd1.render(String.format("($col[128,255,128]{%d}|$col[255,128,128]{%d})", qg.completed, qg.uncompleted)).img), qg.myConditions.get(0).questId));
                } else {
                    if (!qg.otherConditions.isEmpty()) {
                        for (Condition cond : qg.otherConditions)
                        {
                            if(!cond.ready)
                            {
                                imgs.add(new QuestImage(unactive_title.render(labelName(qname)).img, cond.questId));
                                break;
                            }
                        }
                    }
                }
                for (Condition cond : qg.myConditions) {
                    if (cond.state != Condition.State.TELL && !cond.ready)
                        imgs.add(new QuestImage(fnd1.render(labelCond(cond)).img, cond.questId));
                }
                for (Condition cond : qg.otherConditions) {
                    if(!cond.ready)
                        imgs.add(new QuestImage(fnd2.render(labelCond(cond)).img, cond.questId));
                }
            }
        } else if (mode == Mode.TASKS) {
            addTargets(L10n.get("char.quest.section.bring"), Condition.State.BRING);
            addTargets(L10n.get("char.quest.section.foraging"), Condition.State.PICK);
            addTargets(L10n.get("char.quest.section.hunting"), Condition.State.KILL);
            addTargets(L10n.get("char.quest.section.conversation"), Condition.State.GREET, Condition.State.VISIT, Condition.State.RAGE, Condition.State.WAVE, Condition.State.LAUGH);
            addTargets(L10n.get("char.quest.section.reward"), Condition.State.TELL);
            addTargets(L10n.get("char.quest.section.attributes"), Condition.State.GAIN);
            addTargets(L10n.get("char.quest.section.craft"), Condition.State.CREATE);
            addTargets(L10n.get("char.quest.section.other"), Condition.State.CAVE, Condition.State.LIGHT);
        }
        if (!imgs.isEmpty()) {
            glowon = new TexI(ncatimgs(1, imgs.toArray(new QuestImage[0])));
            Coord rsz = new Coord(glowon.sz().x, glowon.sz().y).add(UI.scale(this.margin).mul(2)).add(new Coord(0, modebtn.sz.y));
            rsz.y = Math.min(NUtils.getGameUI().sz.y - NDraggableWidget.delta.y,rsz.y);
            resize(rsz);
        } else {
            glowon = null;
            Coord nsz = UI.scale(this.margin).mul(2).add(new Coord(0, modebtn.sz.y));
            int btnw = modebtn.sz.x + hidebtn.sz.x + refreshbtn.sz.x;
            Coord rsz = new Coord(Math.max(nsz.x, btnw + margin.x * 2), Math.max(nsz.y, modebtn.sz.y + margin.y * 2));
            rsz.y = Math.min(NUtils.getGameUI().sz.y - NDraggableWidget.delta.y,rsz.y);
            resize(rsz);
        }
        if (parent != null)
            parent.resize(sz.add(NDraggableWidget.delta));
        needUpdate.set(false);
        lastUpdate.set(lastUpdate.get()+1);
    }

    static class QuestImage {
        public Pair<Coord, Coord> area = new Pair<>(new Coord(), new Coord());
        public BufferedImage img;
        public int id;

        public QuestImage(BufferedImage img, int id) {
            this.img = img;
            this.id = id;
        }
    }

    void addTargets(String name, Condition.State... states) {
        if(states.length>0) {
            ArrayList<Condition> list = new ArrayList<Condition>();
            for (Condition.State state : states) {
                Targets cand = taskconds.get(state);
                if (cand != null)
                    list.addAll(cand.conditions);
            }
            if(list.isEmpty())
                return;
            Collections.sort(list, new Comparator<Condition>() {
                public int compare(Condition a, Condition b) {
                    return QuestGiverDistance.compareMeters(condMeters(a), condMeters(b));
                }
            });
            imgs.add(new QuestImage(active_title.render(name).img, -1));
            for (Condition condition : list) {
                imgs.add(new QuestImage(gfnd2_under.render(labelCond(condition)).img, condition.questId));
            }
        }
    }

    private String labelCond(Condition cond) {
        if (cond.state == Condition.State.TELL) {
            Condition.QuestsGiver qg = cond.getattr(Condition.QuestsGiver.class);
            String name = (qg != null && qg.name != null) ? qg.name : cond.target;
            return QuestGiverDistance.withMeters(QuestWnd.returnToLabel(name), condMeters(cond));
        }
        return QuestGiverDistance.withMeters(QuestWnd.localizeCond(cond.target), condMeters(cond));
    }

    private String labelName(String name) {
        return QuestGiverDistance.withMeters(name, distanceTo(name));
    }

    private Double condMeters(Condition cond) {
        Condition.QuestsGiver qg = cond.getattr(Condition.QuestsGiver.class);
        if (qg == null)
            return null;
        return distanceTo(qg.name);
    }

    private Double distanceTo(String name) {
        try {
            NGameUI gui = NUtils.getGameUI();
            Pointer ptr = findPointer(gui, name);
            if (ptr != null) {
                try {
                    double d = ptr.getDistance();
                    if (d > 0)
                        return d;
                } catch (RuntimeException ignored) {
                }
            }
            Coord2d at = findGiverPos(name);
            if (at == null)
                return null;
            Gob player = (gui != null && gui.map != null) ? gui.map.player() : null;
            if (player == null)
                return null;
            return QuestGiverDistance.meters(player.rc.dist(at));
        } catch (Loading l) {
            return null;
        }
    }

    private Pointer findPointer(NGameUI gui, String name) {
        if (gui == null || name == null || name.isEmpty())
            return null;
        Pointer hit = findPointerUnder(gui, name);
        if (hit != null)
            return hit;
        if (gui.ui != null && gui.ui.root != null)
            return findPointerUnder(gui.ui.root, name);
        return null;
    }

    private Pointer findPointerUnder(Widget root, String name) {
        for (Pointer p : root.children(Pointer.class)) {
            if (QuestGiverDistance.namesMatch(name, p.tip()))
                return p;
        }
        return null;
    }

    private Coord2d findGiverPos(String name) {
        if (name == null || name.isEmpty())
            return null;
        Coord2d cached = cachedGiverPos(name);
        if (cached != null)
            return cached;
        NGameUI gui = NUtils.getGameUI();
        Pointer ptr = findPointer(gui, name);
        if (ptr != null) {
            try {
                Coord2d tc = ptr.tc();
                if (tc != null) {
                    giverCoords.put(name, tc);
                    return tc;
                }
            } catch (RuntimeException ignored) {
            }
        }
        Coord2d gobAt = findGobPos(gui, name);
        if (gobAt != null) {
            giverCoords.put(name, gobAt);
            return gobAt;
        }
        synchronized (markers) {
            for (MarkerInfo mi : markers) {
                if (name.equals(mi.name) && mi.coord != null)
                    return mi.coord;
            }
        }
        return findMapMarker(gui, name);
    }

    private Coord2d cachedGiverPos(String name) {
        Coord2d exact = giverCoords.get(name);
        if (exact != null)
            return exact;
        for (Map.Entry<String, Coord2d> e : giverCoords.entrySet()) {
            if (QuestGiverDistance.namesMatch(name, e.getKey()))
                return e.getValue();
        }
        return null;
    }

    private Coord2d findGobPos(NGameUI gui, String name) {
        if (gui == null || gui.map == null || gui.map.glob == null)
            return null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                if (gobNamed(gob, name))
                    return gob.rc;
            }
        }
        return null;
    }

    private static boolean gobNamed(Gob gob, String name) {
        if (gob.ngob != null && name.equals(gob.ngob.name))
            return true;
        String kin = NGameUI.gobIdToKinName.get(gob.id);
        if (name.equals(kin))
            return true;
        Buddy buddy = gob.getattr(Buddy.class);
        if (buddy != null) {
            if (name.equals(buddy.rnm))
                return true;
            if (buddy.b != null && name.equals(buddy.b.name))
                return true;
        }
        return false;
    }

    private Coord2d findMapMarker(NGameUI gui, String name) {
        if (gui == null || gui.mapfile == null || gui.mapfile.file == null || gui.mapfile.view == null)
            return null;
        MiniMap.Location sessloc = gui.mapfile.view.sessloc;
        if (sessloc == null)
            return gui.mapfile.findMarkerPosition(name);
        MapFile file = gui.mapfile.file;
        if (!file.lock.readLock().tryLock())
            return gui.mapfile.findMarkerPosition(name);
        try {
            Coord2d exact = null, fuzzy = null;
            for (MapFile.Marker mark : file.markers) {
                if (mark.nm == null || mark.seg != sessloc.seg.id)
                    continue;
                Coord2d pos = mark.tc.sub(sessloc.tc).mul(MCache.tilesz).add(MCache.tilesz.div(2));
                if (name.equals(mark.nm))
                    exact = pos;
                else if (fuzzy == null && QuestGiverDistance.namesMatch(name, mark.nm))
                    fuzzy = pos;
            }
            if (exact != null)
                return exact;
            if (fuzzy != null)
                return fuzzy;
        } finally {
            file.lock.readLock().unlock();
        }
        return gui.mapfile.findMarkerPosition(name);
    }

    private void harvestPointers(NGameUI gui) {
        if (gui == null)
            return;
        harvestPointersUnder(gui);
        if (gui.ui != null && gui.ui.root != null)
            harvestPointersUnder(gui.ui.root);
    }

    private void harvestPointersUnder(Widget root) {
        for (Pointer p : root.children(Pointer.class)) {
            String tip = p.tip();
            if (tip == null || tip.isEmpty())
                continue;
            try {
                Coord2d tc = p.tc();
                if (tc != null)
                    giverCoords.put(tip, tc);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private Integer nextHarvestQuest() {
        for (NQuest q : quests.values()) {
            if (harvestTried.contains(q.id))
                continue;
            for (Condition cond : q.conditions) {
                if (cond.ready)
                    continue;
                Condition.QuestsGiver qg = cond.getattr(Condition.QuestsGiver.class);
                if (qg == null || qg.name == null)
                    continue;
                if (findGiverPos(qg.name) == null)
                    return q.id;
            }
        }
        return null;
    }

    private BufferedImage ncatimgs(int margin, QuestImage... imgs) {
        int w = 0, h = -margin;
        for (QuestImage img : imgs) {
            if (img == null)
                continue;
            if (img.img.getWidth() > w)
                w = img.img.getWidth();
            h += img.img.getHeight() + margin;
        }
        BufferedImage ret = TexI.mkbuf(new Coord(w, h));
        Graphics g = ret.getGraphics();
        int y = 0;
        for (QuestImage img : imgs) {
            if (img == null)
                continue;
            img.area.a.x = 0;
            img.area.a.y = y;
            g.drawImage(img.img, 0, y, null);
            y += img.img.getHeight() + margin;
            img.area.b.x = img.img.getWidth();
            img.area.b.y = y - margin;
        }
        g.dispose();
        return (ret);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        Coord pos = new Coord(ev.c.x, ev.c.y).sub(UI.scale(this.margin)).sub(new Coord(0,modebtn.sz.y));
        if (imgs != null) {
            for (QuestImage img : imgs) {
                if (img.id >= 0) {
                    if (img.area.a.x <= pos.x && pos.x <= img.area.b.x && img.area.a.y <= pos.y && pos.y <= img.area.b.y) {
                        NUtils.getGameUI().chrwdg.show();
                        NUtils.getGameUI().chrwdg.questtab.showtab();
                        NUtils.getGameUI().chrwdg.wdgmsg("qsel", img.id);
                        return true;
                    }
                }
            }
        }
        return super.mousedown(ev);
    }

    AtomicBoolean needUpdate = new AtomicBoolean(false);
    private double distAcc = 0;
    private int distKey = Integer.MIN_VALUE;
    private final HashMap<String, Coord2d> giverCoords = new HashMap<String, Coord2d>();
    private final HashSet<Integer> harvestTried = new HashSet<Integer>();
    private double harvestAcc = 0;

    public static final HashSet<String> huntingT = new HashSet<>();
    public static final HashSet<String> forageT = new HashSet<>();

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(!forRemove.isEmpty())
        {
            for(Integer i : forRemove)
            {
                quests.remove(i);
            }
            forRemove.clear();
            needUpdate.set(true);
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.chrwdg != null) {
            for(NQuest q : quests.values())
            {
                if(!q.request && q.conditions.isEmpty()) {
                    q.request = true;
                    gui.chrwdg.wdgmsg("qsel", q.id);
                }
            }
            harvestPointers(gui);
            harvestAcc += dt;
            if (harvestAcc > 0.45) {
                harvestAcc = 0;
                if (!gui.chrwdg.visible) {
                    Integer hid = nextHarvestQuest();
                    if (hid != null) {
                        harvestTried.add(hid);
                        gui.chrwdg.wdgmsg("qsel", hid);
                    }
                }
            }
        }
        distAcc += dt;
        if (distAcc > 0.5) {
            distAcc = 0;
            int key = distanceKey();
            if (key != distKey) {
                distKey = key;
                needUpdate.set(true);
            }
        }
        if(needUpdate.get())
            update();
    }

    private int distanceKey() {
        int h = 1;
        NGameUI gui = NUtils.getGameUI();
        Gob player = (gui != null && gui.map != null) ? gui.map.player() : null;
        if (player != null)
            h = 31 * h + QuestGiverDistance.tileKey(player.rc.x, player.rc.y);
        for (NQuest quest : quests.values()) {
            for (Condition cond : quest.conditions) {
                if (cond.ready)
                    continue;
                Condition.QuestsGiver qg = cond.getattr(Condition.QuestsGiver.class);
                if (qg == null || qg.name == null)
                    continue;
                Double m = distanceTo(qg.name);
                h = 31 * h + qg.name.hashCode();
                h = 31 * h + (m == null ? 0 : (int) Math.round(m));
            }
        }
        return h;
    }

    private void refreshDistances() {
        if (refreshbtn != null)
            refreshbtn.set(false);
        giverCoords.clear();
        harvestTried.clear();
        distKey = Integer.MIN_VALUE;
        needUpdate.set(true);
    }
    Coord margin = new Coord(10,10);
    public static final IBox pbox = Window.wbox;
    @Override
    public void draw(GOut g) {
        Coord margin = UI.scale(this.margin);
        if (glowon != null) {
            NDraggableWidget.drawBg(g.reclip(new Coord(0,modebtn.sz.y), glowon.sz().add(margin.mul(2))), glowon.sz().add(margin.mul(2)), ui);
            pbox.draw(g, new Coord(0,modebtn.sz.y), glowon.sz().add(margin.mul(2)));

            g.image(glowon, margin.add(new Coord(0,modebtn.sz.y)));
        }
        super.draw(g);
    }

    public void updateConds(int id, Object[] args) {
        NQuest quest = quests.get(id);

        if(quest != null) {
            quest.request = false;
            quest.conditions.clear();
            int a = 0;
            while (a < args.length) {
                String desc = STR.of(args[a++]);
                int st = INT.of(args[a++]);
                String status = STR.of(args[a++]);
                Object[] wdata = null;
                if((a < args.length) && OBJS.is(args[a]))
                    wdata = OBJS.of(args[a++]);
                Condition cond = new Condition(st != 0, desc, id, status);
                quest.conditions.add(cond);
            }
        }
        else
        {
            NUtils.getGameUI().error("NOT FOUND");
        }
        needUpdate.set(true);
    }

    public void requestUpdate() {
        needUpdate.set(true);
    }

    private String helperTitle(NQuest quest) {
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.chrwdg != null && gui.chrwdg.quest != null) {
            QuestWnd.Quest q = gui.chrwdg.quest.cqst.get(quest.id);
            if (q != null)
                return QuestTrackFilter.safeTitle(q);
        }
        return null;
    }


    public void removeQuest(int id) {
        synchronized (forRemove) {
            forRemove.add(id);
        }
    }

    final ArrayList<Integer> forRemove = new ArrayList<>();
    final HashMap<Integer, NQuest> quests = new HashMap<>();

    public void addQuest(int id) {
        synchronized (quests) {
            NQuest q = quests.put(id,new NQuest(id));
        }
    }

    static class NQuest
    {
        public boolean request = false;
        int id;
        ArrayList<Condition> conditions = new ArrayList<Condition>();
        String questGiver = null;
        public NQuest(int id) {
            this.id = id;
        }
    }

    static class Condition{
        boolean ready;
        String target;
        State state;
        int questId;
        enum State
        {
            TELL,
            KILL,
            PICK,
            BRING,
            VISIT,
            GREET,
            LAUGH,
            RAGE,
            WAVE,
            GAIN,
            CAVE,
            LIGHT,
            CREATE
        }

        public Condition(boolean ready, String target, int questId, String status) {
            this.ready = ready;
            this.target = target;
            this.questId = questId;

            if (target.contains("Bring"))
            {
                this.state = State.BRING;
                attrs.put(QuestsGiver.class, new QuestsGiver(target));
                attrs.put(BringItem.class, new BringItem(target));
//                bring_t.add(new Task(qid, c));
            }
            else if (target.contains("Pick"))
            {
                this.state = State.PICK;
                attrs.put(PickTarget.class, new PickTarget(target));
            }
            else if (target.contains("Kill") || target.contains("Raid") || target.contains("Defeat") ) {
                this.state = State.KILL;
                attrs.put(HuntTarget.class, new HuntTarget(target));
            }
            else if (target.contains("Catch"))
            {
                this.state = State.PICK;
                attrs.put(PickTarget.class, new PickTarget(target));
            }
            else if (target.contains("Greet") || (target.contains("Visit") && !target.contains("cave")) || target.contains("wave") || target.contains("laugh") || target.contains("rage"))
            {
                attrs.put(QuestsGiver.class, new QuestsGiver(target));
                if(target.contains("Greet") || (target.contains("Visit") && !target.contains("cave")))
                {
                    this.state = State.GREET;
                }
                else if(target.contains("wave"))
                {
                    this.state = State.WAVE;
                }
                else if(target.contains("laugh"))
                {
                    this.state = State.LAUGH;
                }
                else if(target.contains("rage"))
                {
                    this.state = State.RAGE;
                }
            }
            else if (target.contains("Gain"))
            {
                this.state = State.GAIN;
            }
            else if (target.contains("Create"))
            {
                this.state = State.CREATE;
            }
            else if (target.contains("Tell"))
            {
                this.state = State.TELL;
                attrs.put(QuestsGiver.class,new QuestsGiver(target));
            }
            else if (target.contains("cave"))
            {
                this.state = State.CAVE;
            }
            else if (target.contains("Light"))
            {
                this.state = State.LIGHT;
            }
            if(status!=null)
            {
                this.target += " " + status;
            }
        }

        class QuestsGiver
        {
            public String name;

            public QuestsGiver(String info) {
                if (info.contains("Tell") || (info.contains("Visit") && !info.contains("cave"))) {
                    name = info.contains("Tell") ? info.substring(5, info.indexOf(" ", 6)) : info.substring(6);
                }
                else
                {
                    if (info.contains("Greet") || (info.contains("Visit") && !info.contains("cave"))) {
                        name = info.substring(6);
                    } else if (info.contains(" to ")) {
                        name = info.substring(info.indexOf(" to ") + 4);
                    } else if (info.contains(" at ")) {
                        name = info.substring(info.indexOf(" at ") + 4);
                    }
                }
            }
        }

        class BringItem
        {
            public String name = null;

            public BringItem(String info) {
                if( info.contains("Bring")){

                    if(info.contains(" a ") || info.contains(" an ")) {
                        if(info.contains(" a "))
                            name = info.substring(info.indexOf(" a ") + 3, info.indexOf("to ") - 1);
                        else
                            name = info.substring(info.indexOf(" an ") + 4, info.indexOf("to ") - 1);
                    }
                    else
                    {
                        name = info.substring(6, info.indexOf("to ") - 1);
                    }
                }
            }
        }

        class PickTarget
        {
            public String name;

            public PickTarget(String info) {
                info = info.toLowerCase();
                String bufname;
                int ind = info.indexOf(" a ");
                if (ind != -1)
                    bufname = info.substring(info.indexOf(" a ") + 3);
                else {
                    ind = info.indexOf(" an ");
                    if (ind != -1)
                        bufname = info.substring(info.indexOf(" an ") + 4);
                    else
                        bufname = info.substring(info.indexOf(" ") + 1);
                }

                if (!bufname.isEmpty()) {
                    if (bufname.contains("blueberr"))
                        bufname = "blueberr";
                    else if (bufname.contains("lingon"))
                        bufname = "lingon";
                    else if (bufname.contains("woodgrouse hen"))
                        bufname = "woodgrouse-f";
                    else if (bufname.contains("morel"))
                        bufname = "lorchel";
                    else if (bufname.contains("yellowf"))
                        bufname = "yellowf";
                    else if (bufname.contains("hen"))
                        bufname = "chicken/chicken";
                    else if (bufname.contains("cock"))
                        bufname = "chicken/roast";
                    else if (bufname.contains("chantrell"))
                        bufname = "herbs/chantrell";
                    else if (bufname.contains("rat"))
                        bufname = "rat/rat";
                    name = (bufname.replaceAll("\\s+", "")).replaceAll("'+", "");
                }
            }
        }

        class HuntTarget {
            public String name;

            public HuntTarget(String info) {
                info = info.toLowerCase();
                String bufname;
                int ind = info.indexOf(" a ");
                if (ind != -1)
                    bufname = info.substring(info.indexOf(" a ") + 3);
                else {
                    ind = info.indexOf(" an ");
                    if (ind != -1)
                        bufname = info.substring(info.indexOf(" an ") + 4);
                    else
                        bufname = info.substring(info.indexOf(" ") + 1);
                }

                if (!bufname.isEmpty()) {
                    if (bufname.contains("mouflon"))
                        bufname = "sheep";
                    else if (bufname.contains("auroch"))
                        bufname = "cattle";
                    else if (bufname.contains("horse"))
                        bufname = "horse/horse";
                    else if (info.contains("raid a")) {
                        if (bufname.contains("bird"))
                            bufname = "birdsnest";
                        else
                            bufname = "anthill";
                    }
                    else {
                        bufname = "kritter/"+bufname;
                    }
                }
                name = (bufname.replaceAll("\\s+", "")).replaceAll("'+", "");
            }
        }


        public Map<Class<?>, Object> attrs = new HashMap<>();

        public <C> C getattr(Class<C> c) {
            Object attr = this.attrs.get(c);
            if(!c.isInstance(attr))
                return(null);
            return(c.cast(attr));
        }

    }

    public class MarkerInfo{
        public String name;
        public Coord2d coord;
        public long seg;
        public HashSet<String> prop;

        public MarkerInfo(String name, Coord2d coord, long seg) {
            this.name = name;
            this.coord = coord;
            this.seg = seg;
        }
    }

    final static HashSet<MarkerInfo> markers = new HashSet<>();
    public void addMarkerCoord(Coord2d tmp, String nm, long seg) {
        synchronized (markers) {
            for (MarkerInfo mi : markers) {
                if (mi.name.equals(nm)) {
                    mi.coord = tmp;
                    mi.seg = seg;
                    return;
                }
            }
            markers.add(new MarkerInfo(nm, tmp, seg));
        }
        lastUpdate.set(lastUpdate.get()+1);
    }

    public static MarkerInfo getMarkerInfo(Gob gob)
    {
        NGameUI gui = NUtils.getGameUI();
        if(gui != null && gui.mapfile != null) {
            synchronized (markers) {
                for (MarkerInfo mi : markers) {
                    if (gui.mapfile.playerSegmentId() == mi.seg) {
                        if (gob.rc.dist(mi.coord) < 1)
                            return (mi);
                    }
                }
            }
        }
        return(null);
    }

    void setMarkersProp(String name, HashSet<String> props)
    {
        if(name==null)
            return;
        synchronized (markers) {
            for (MarkerInfo mi : markers) {
                if (mi.name!=null && mi.name.equals(name)) {
                    mi.prop = props;
                    return;
                }
            }
            MarkerInfo mi = new MarkerInfo(name, null, -1);
            mi.prop = props;
            markers.add(mi);
            lastUpdate.set(lastUpdate.get()+1);
        }
    }

    public boolean isQuestedItem(NGItem item){
        for(String name : items)
        {
            if(item.name()!=null && item.name().toLowerCase().contains(name))
                return true;
        }

        return false;
    }
}