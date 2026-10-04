package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.MCache;
import haven.MapView;
import haven.Pair;
import haven.Resource;
import haven.WItem;
import nurgling.NConfig;
import nurgling.NFlowerMenu;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Equip;
import nurgling.actions.FreeInventory2;
import nurgling.actions.GoTo;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.actions.SelectFlowerAction;
import nurgling.actions.Validator;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.areas.NGlobalCoord;
import nurgling.overlays.QualityOl;
import nurgling.tasks.GetCurs;
import nurgling.tasks.NFlowerMenuIsClosed;
import nurgling.tasks.NTask;
import nurgling.tasks.NoGob;
import nurgling.tasks.WaitButcherState;
import nurgling.tasks.WaitFreeHand;
import nurgling.tasks.WaitTicks;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.VSpec;
import nurgling.widgets.NEquipory;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Butcher implements Action {

    static HashMap<String,Req> options = new HashMap<>();
    static ArrayList<String> order = new ArrayList<>();

    static {
        order.add("Skin");
        order.add("Scale");
        order.add("Crack");
        order.add("Clean");
        order.add("Butcher");
        order.add("Collect bones");
        options.put("Skin", new Req(new Coord(2,2),1));
        options.put("Scale", new Req(new Coord(1,1),3));
        options.put("Clean", new Req(new Coord(1,1),1));
        options.put("Butcher", new Req(new Coord(1,1),2));
        options.put("Collect bones", new Req(new Coord(2,2),1));
        options.put("Crack", new Req(new Coord(2,2),1));
    }

    static class Req{
        public Req(Coord size, int num) {
            this.size = size;
            this.num = num;
        }

        public Coord size;
        public int num;
    }

    private final Gob target;

    public Butcher() {
        this(null);
    }

    public Butcher(Gob target) {
        this.target = target;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        HandLoadout before = HandLoadout.capture();
        try {
            boolean useKnife = ButcherKnifePolicy.useKnifeEnabled(NConfig.get(NConfig.Key.butcherUseKnife));
            boolean alwaysKnife = ButcherKnifePolicy.alwaysKnifeEnabled(NConfig.get(NConfig.Key.butcherKnifeAlways));
            ButcherKnifePolicy.Loadout loadout = scanTools();
            boolean inspect = ButcherKnifePolicy.inspectEach(useKnife, alwaysKnife, loadout);
            if (!inspect) {
                Results equipped = equipChoice(gui, ButcherKnifePolicy.choose(useKnife, alwaysKnife, loadout, null));
                if (!equipped.IsSuccess()) {
                    return equipped;
                }
            }

            NArea.Specialisation kritter_corpse = new NArea.Specialisation(Specialisation.SpecName.deadkritter.toString());
            NArea zone = NContext.findSpec(kritter_corpse);
            ButcherTarget.Mode mode = ButcherTarget.resolve(target != null, zone != null);

            if (mode == ButcherTarget.Mode.SINGLE) {
                Gob gob = Finder.findGob(target.id);
                if (gob == null || !ButcherTarget.isCarcass(gob)) {
                    return Results.ERROR("No carcass");
                }
                return butcherGobs(gui, listOf(gob), null,
                        ButcherTarget.dumpInventory(mode, ButcherTarget.hasOutAreas(playerOutAreas(gui))),
                        inspect, useKnife, alwaysKnife, loadout);
            }

            if (mode == ButcherTarget.Mode.ZONE) {
                ArrayList<NArea.Specialisation> req = new ArrayList<>();
                req.add(kritter_corpse);
                if (!new Validator(req, new ArrayList<>()).run(gui).IsSuccess()) {
                    return Results.ERROR("No carcass area");
                }
                NUtils.navigateToArea(zone);
                return butcherGobs(gui, getGobs(zone), zone, true, inspect, useKnife, alwaysKnife, loadout);
            }

            SelectArea insa = new SelectArea(Resource.loadsimg("baubles/inputArea"));
            if (!insa.run(gui).IsSuccess() || insa.getRCArea() == null) {
                return Results.ERROR("No area selected");
            }
            return butcherGobs(gui, getGobs(insa.getRCArea()), null, false, inspect, useKnife, alwaysKnife, loadout);
        } finally {
            HandLoadout.restore(gui, before);
        }
    }

    private Results butcherGobs(NGameUI gui, ArrayList<Gob> gobs, NArea area, boolean dumpInventory,
                                boolean inspect, boolean useKnife, boolean alwaysKnife,
                                ButcherKnifePolicy.Loadout loadout) throws InterruptedException {
        HashSet<Long> done = new HashSet<>();
        while (!gobs.isEmpty()) {
            gobs.sort(NUtils.d_comp);
            Gob gob = followCarcass(gobs.get(0), gobs.get(0) != null ? gobs.get(0).rc : null, false);
            if (gob == null) {
                gobs.remove(0);
                continue;
            }
            gobs.set(0, gob);
            if (inspect) {
                approach(gui, gob);
                Double quality = inspectCarcass(gui, gob);
                Results equipped = equipChoice(gui, ButcherKnifePolicy.choose(useKnife, alwaysKnife, loadout, quality));
                if (!equipped.IsSuccess())
                    return equipped;
            }
            NContext context = dumpInventory ? new NContext(gui) : null;
            done.add(gob.id);
            Coord2d origin = gob.rc;
            Results one = butcherOne(gui, gob, area, context, dumpInventory);
            if (!one.IsSuccess()) {
                return one;
            }
            if (area != null) {
                context.goToArea(Specialisation.SpecName.deadkritter);
                gobs = getGobs(area);
            } else {
                gobs.remove(0);
                for (int i = gobs.size() - 1; i >= 0; i--) {
                    Gob left = followCarcass(gobs.get(i), gobs.get(i).rc, false);
                    if (left == null) {
                        gobs.remove(i);
                    } else {
                        gobs.set(i, left);
                    }
                }
                if (target != null) {
                    Gob more = nextPileCarcass(origin, done);
                    if (more != null)
                        gobs.add(more);
                }
            }
        }
        if (dumpInventory) {
            Results dumped = freeInventory(gui, new NContext(gui), area == null);
            if (!dumped.IsSuccess())
                return dumped;
        }
        return Results.SUCCESS();
    }

    private static Results freeInventory(NGameUI gui, NContext context, boolean returnToCarcass)
            throws InterruptedException {
        NGlobalCoord origin = returnToCarcass ? NUtils.bookmarkHere() : null;
        if (returnToCarcass && origin == null)
            return Results.ERROR("Cannot remember butchering location");
        Results dumped = new FreeInventory2(context).run(gui);
        if (returnToCarcass) {
            boolean navigated = NUtils.navigateTo(origin);
            Coord2d target = origin.getCurrentCoord();
            Gob player = NUtils.player();
            if (!navigated || target == null || player == null
                    || player.rc.dist(target) > MCache.tilesz.x * 2)
                return Results.ERROR("Cannot return to butchering location");
        }
        return dumped;
    }

    private Results butcherOne(NGameUI gui, Gob gob, NArea area, NContext context, boolean dumpInventory) throws InterruptedException {
        Coord2d lastRc = gob.rc;
        int emptyMenus = 0;
        while (true) {
            Gob next = continueCarcass(gob, lastRc, emptyMenus > 0);
            if (next != null && gob != null && next.id != gob.id) {
                emptyMenus = 0;
            }
            gob = next;
            if (gob == null) {
                if (ButcherTarget.giveUpOnEmptyMenu(++emptyMenus)) {
                    break;
                }
                NUtils.addTask(new WaitTicks(8));
                continue;
            }
            lastRc = gob.rc;
            NUtils.rclickGob(gob);
            NFlowerMenu fm = NUtils.getFlowerMenu();
            if (fm == null) {
                if (ButcherTarget.giveUpOnEmptyMenu(++emptyMenus)) {
                    break;
                }
                NUtils.addTask(new WaitTicks(8));
                continue;
            }
            emptyMenus = 0;
            String optForSelect = null;
            for (String option : order) {
                for (NFlowerMenu.NPetal petal : fm.nopts) {
                    if (petal.name.equals(option)) {
                        optForSelect = option;
                        fm.wdgmsg("cl", -1);
                        NUtils.getUI().core.addTask(new NFlowerMenuIsClosed());
                        break;
                    }
                }
                if (optForSelect != null)
                    break;
            }
            if (optForSelect == null) {
                break;
            }
            boolean optFound = true;
            while (optFound && gob!=null) {

                if (NUtils.getGameUI().getInventory().getNumberFreeCoord(options.get(optForSelect).size) < options.get(optForSelect).num) {
                    if (dumpInventory) {
                        Results dumped = freeInventory(gui, context, area == null);
                        if (!dumped.IsSuccess())
                            return dumped;
                        if (area == null)
                            gob = continueCarcass(gob, lastRc, false);
                    }
                }
                if (NUtils.getGameUI().getInventory().getNumberFreeCoord(options.get(optForSelect).size) < options.get(optForSelect).num) {
                    return Results.ERROR("No free coord found for: " + optForSelect + "|" + options.get(optForSelect).size.toString() + "| target size: " + options.get(optForSelect).num);
                }

                if (area != null && NUtils.navigateToArea(area)) {
                    if(gob!=null)
                        gob = Finder.findGob(gob.id);
                }
                if (gob != null) {
                    approach(gui, gob);

                    if (new SelectFlowerAction(optForSelect, gob).run(gui).IsSuccess()) {

                        if (!optForSelect.equals("Collect bones")) {
                            NUtils.addTask(new NTask() {
                                int ticks;
                                @Override
                                public boolean check() {
                                    Gob pl = NUtils.player();
                                    return WaitButcherState.workStarted(
                                            pl != null ? pl.pose() : null,
                                            WaitButcherState.isMounted(pl),
                                            ticks++);
                                }
                            });
                            WaitButcherState wbs = new WaitButcherState(options.get(optForSelect).size);
                            NUtils.addTask(wbs);
                            if (wbs.getState() == WaitButcherState.State.READY) {
                                optFound = false;
                            }
                        } else {
                            NUtils.addTask(new NoGob(gob.id));
                            if (gui.vhand != null) {
                                NUtils.drop(gui.vhand);
                                NUtils.addTask(new WaitFreeHand());
                                if (dumpInventory) {
                                    Results dumped = freeInventory(gui, context, area == null);
                                    if (!dumped.IsSuccess())
                                        return dumped;
                                    if (area == null)
                                        gob = continueCarcass(gob, lastRc, false);
                                }
                            }
                            optFound = false;
                        }
                    }
                    else
                        optFound = false;
                }
            }
            NUtils.addTask(new WaitTicks(8));
            if (gob != null && Finder.findGob(gob.id) == null) {
                Gob replaced = continueCarcass(gob, lastRc, true);
                if (replaced != null) {
                    gob = replaced;
                    lastRc = gob.rc;
                }
            }
        }
        return Results.SUCCESS();
    }

    private static void approach(NGameUI gui, Gob gob) throws InterruptedException {
        if (WaitButcherState.isMounted(NUtils.player())) {
            Coord2d stop = ButcherTarget.mountedApproach(NUtils.player().rc, gob.rc);
            if (stop != null) {
                new GoTo(stop).run(gui);
            }
        } else {
            new PathFinder(gob).run(gui);
        }
    }

    /** Same carcass, including a new id after Skin. A different animal nearby is left for the next inspect. */
    private static Gob continueCarcass(Gob gob, Coord2d lastRc, boolean skipSameId) throws InterruptedException {
        Gob next = followCarcass(gob, lastRc, skipSameId);
        if (next != null && ButcherTarget.adoptCarcass(gob != null, gob != null ? gob.id : 0L, lastRc, next.id, next.rc))
            return next;
        return gob != null ? Finder.findGob(gob.id) : null;
    }

    /** Next carcass in a Ctrl+click pile. Already finished bodies are skipped so each new one is inspected. */
    private static Gob nextPileCarcass(Coord2d origin, HashSet<Long> done) throws InterruptedException {
        if (origin == null)
            return null;
        ArrayList<Gob> nearby = Finder.findGobs(origin, new NAlias("kritter"), new NAlias("knock", "dead"),
                ButcherTarget.FOLLOW_RADIUS);
        Gob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Gob gob : nearby) {
            if (gob == null || done.contains(gob.id) || !ButcherTarget.isCarcass(gob))
                continue;
            double dist = origin.dist(gob.rc);
            if (dist < bestDist) {
                best = gob;
                bestDist = dist;
            }
        }
        return best;
    }

    /** After Skin a horse often respawns with a new gob id at the same spot. */
    private static Gob followCarcass(Gob gob, Coord2d lastRc, boolean skipSameId) throws InterruptedException {
        Coord2d from = lastRc != null ? lastRc : (gob != null ? gob.rc : null);
        if (from != null) {
            ArrayList<Long> skip = new ArrayList<>();
            if (skipSameId && gob != null) {
                skip.add(gob.id);
            }
            Gob nearby = Finder.findGob(from, new NAlias("kritter"), new NAlias("knock", "dead"),
                    ButcherTarget.FOLLOW_RADIUS, skip);
            if (nearby != null) {
                return nearby;
            }
        }
        if (skipSameId || gob == null) {
            return null;
        }
        return Finder.findGob(gob.id);
    }

    private static ArrayList<Gob> listOf(Gob gob) {
        ArrayList<Gob> gobs = new ArrayList<>();
        gobs.add(gob);
        return gobs;
    }

    /** Player overlay NAreas with OUT tags — same nols set FreeInventory2 / NContext uses. */
    private static ButcherTarget.OutArea[] playerOutAreas(NGameUI gui) {
        if (gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
            return new ButcherTarget.OutArea[0];
        }
        ArrayList<ButcherTarget.OutArea> out = new ArrayList<>();
        for (Integer id : gui.map.nols.keySet()) {
            if (id == null || id <= 0) {
                continue;
            }
            NArea cand = gui.map.glob.map.areas.get(id);
            if (cand == null) {
                continue;
            }
            int outs = cand.jout == null ? 0 : cand.jout.length();
            boolean visible = cand.isVisible() && cand.getRCArea() != null;
            out.add(new ButcherTarget.OutArea(cand.isDisabled(), outs, visible));
        }
        return out.toArray(new ButcherTarget.OutArea[0]);
    }

    private static ArrayList<Gob> getGobs(NArea area) throws InterruptedException {
        return getGobs(area.getRCArea());
    }

    private static ArrayList<Gob> getGobs(Pair<Coord2d, Coord2d> space) throws InterruptedException {
        ArrayList<Gob> result = new ArrayList<>();
        ArrayList<Gob> gobs = Finder.findGobs(space, new NAlias("kritter"));
        for(Gob gob: gobs)
        {
            if(ButcherTarget.isCarcass(gob) && PathFinder.isAvailable(gob))
            {
                result.add(gob);
            }
        }
        return result;
    }

    private static final NAlias SACKS = new NAlias("Traveller's Sack", "Wanderer's Bindle", "Traveler's Sack");

    private static Results equipChoice(NGameUI gui, ButcherKnifePolicy.Choice choice) throws InterruptedException {
        NAlias tool = choice == ButcherKnifePolicy.Choice.KNIFE
                ? ButcherKnifePolicy.KNIFE
                : VSpec.getNamesInCategory("Sharp Tool");
        return new Equip(tool, SACKS, NInventory.QualityType.High).run(gui);
    }

    private static ButcherKnifePolicy.Loadout scanTools() throws InterruptedException {
        return ButcherKnifePolicy.Loadout.of(
                bestQuality(ButcherKnifePolicy.KNIFE),
                bestOtherQuality(ButcherKnifePolicy.KNIFE));
    }

    /** Highest sharp tool that is not the cleaver. The cleaver is itself a sharp tool. */
    private static Double bestOtherQuality(NAlias cleaver) throws InterruptedException {
        return bestQuality(VSpec.getNamesInCategory("Sharp Tool"), cleaver);
    }

    private static Double bestQuality(NAlias name) throws InterruptedException {
        return bestQuality(name, null);
    }

    private static Double bestQuality(NAlias name, NAlias skip) throws InterruptedException {
        if (NUtils.getEquipment() == null)
            return null;
        double best = Double.NaN;
        WItem left = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx);
        WItem right = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx);
        awaitQuality(left, right);
        best = maxOf(best, left, name, skip);
        if (right != left)
            best = maxOf(best, right, name, skip);
        WItem belt = NUtils.getEquipment().findItem(NEquipory.Slots.BELT.idx);
        if (belt != null && belt.item.contents instanceof NInventory) {
            ArrayList<WItem> items = ((NInventory) belt.item.contents).getItems(name);
            awaitQuality(items.toArray(new WItem[0]));
            for (WItem item : items)
                best = maxOf(best, item, name, skip);
        }
        return Double.isNaN(best) ? null : best;
    }

    private static void awaitQuality(WItem... items) throws InterruptedException {
        NTask wait = new NTask() {
            {
                infinite = false;
                criticalOnTimeout = false;
                maxCounter = 40;
            }

            @Override
            public boolean check() {
                for (WItem item : items) {
                    if (item != null && item.item instanceof NGItem) {
                        NGItem gi = (NGItem) item.item;
                        if (gi.name() != null && gi.quality == null)
                            return false;
                    }
                }
                return true;
            }
        };
        NUtils.addTask(wait);
    }

    private static double maxOf(double best, WItem item, NAlias name, NAlias skip) {
        if (item == null || !(item.item instanceof NGItem))
            return best;
        NGItem gi = (NGItem) item.item;
        if (gi.name() == null || !NParser.checkName(gi.name(), name))
            return best;
        if (skip != null && NParser.checkName(gi.name(), skip))
            return best;
        if (gi.quality == null || gi.quality <= 0)
            return best;
        return Double.isNaN(best) ? gi.quality : Math.max(best, gi.quality);
    }

    private static Double overlayQuality(Gob gob) {
        if (gob == null)
            return null;
        Gob.Overlay ol = gob.findol(QualityOl.class);
        if (ol == null || !(ol.spr instanceof QualityOl))
            return null;
        return ((QualityOl) ol.spr).quality;
    }

    /** Loupe inspect. An overlay already on the carcass is reused. */
    private static Double inspectCarcass(NGameUI gui, Gob gob) throws InterruptedException {
        Double known = overlayQuality(gob);
        if (known != null)
            return known;
        try {
            gui.ui.rcvr.rcvmsg(NUtils.getUI().getMenuGridId(), "act", "inspect");
            GetCurs study = new GetCurs("study") {
                {
                    infinite = false;
                    criticalOnTimeout = false;
                    maxCounter = 80;
                }
            };
            NUtils.addTask(study);
            if (study.getResult() == null || !NParser.checkName(study.getResult(), "study"))
                return null;
            NUtils.clickGob(gob);
            gui.map.clickedGob = new MapView.ClickedGob(gob, 1);
            NTask waitOl = new NTask() {
                {
                    infinite = false;
                    criticalOnTimeout = false;
                    maxCounter = 120;
                }

                @Override
                public boolean check() {
                    return overlayQuality(gob) != null;
                }
            };
            NUtils.addTask(waitOl);
            return overlayQuality(gob);
        } finally {
            NUtils.getDefaultCur();
        }
    }
}
