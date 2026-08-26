package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import haven.Resource;
import nurgling.NFlowerMenu;
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
import nurgling.tasks.NFlowerMenuIsClosed;
import nurgling.tasks.NTask;
import nurgling.tasks.NoGob;
import nurgling.tasks.WaitButcherState;
import nurgling.tasks.WaitFreeHand;
import nurgling.tasks.WaitTicks;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.VSpec;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.HashMap;

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
            Results equipped = new Equip(
                    VSpec.getNamesInCategory("Sharp Tool"),
                    new NAlias("Traveller's Sack", "Wanderer's Bindle", "Traveler's Sack"),
                    NInventory.QualityType.High
            ).run(gui);
            if (!equipped.IsSuccess()) {
                return equipped;
            }

            NArea.Specialisation kritter_corpse = new NArea.Specialisation(Specialisation.SpecName.deadkritter.toString());
            NArea zone = NContext.findSpec(kritter_corpse);
            ButcherTarget.Mode mode = ButcherTarget.resolve(target != null, zone != null);

            if (mode == ButcherTarget.Mode.SINGLE) {
                Gob gob = Finder.findGob(target.id);
                if (gob == null || !ButcherTarget.isCarcass(gob)) {
                    return Results.ERROR("No carcass");
                }
                return butcherGobs(gui, listOf(gob), null, false);
            }

            if (mode == ButcherTarget.Mode.ZONE) {
                ArrayList<NArea.Specialisation> req = new ArrayList<>();
                req.add(kritter_corpse);
                if (!new Validator(req, new ArrayList<>()).run(gui).IsSuccess()) {
                    return Results.ERROR("No carcass area");
                }
                NUtils.navigateToArea(zone);
                return butcherGobs(gui, getGobs(zone), zone, true);
            }

            SelectArea insa = new SelectArea(Resource.loadsimg("baubles/inputArea"));
            if (!insa.run(gui).IsSuccess() || insa.getRCArea() == null) {
                return Results.ERROR("No area selected");
            }
            return butcherGobs(gui, getGobs(insa.getRCArea()), null, false);
        } finally {
            HandLoadout.restore(gui, before);
        }
    }

    private Results butcherGobs(NGameUI gui, ArrayList<Gob> gobs, NArea area, boolean dumpInventory) throws InterruptedException {
        while (!gobs.isEmpty()) {
            gobs.sort(NUtils.d_comp);
            Gob gob = followCarcass(gobs.get(0), gobs.get(0) != null ? gobs.get(0).rc : null, false);
            if (gob == null) {
                gobs.remove(0);
                continue;
            }
            gobs.set(0, gob);
            NContext context = dumpInventory ? new NContext(gui) : null;
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
            }
        }
        if (dumpInventory) {
            new FreeInventory2(new NContext(gui)).run(gui);
        }
        return Results.SUCCESS();
    }

    private Results butcherOne(NGameUI gui, Gob gob, NArea area, NContext context, boolean dumpInventory) throws InterruptedException {
        Coord2d lastRc = gob.rc;
        int emptyMenus = 0;
        while (true) {
            Gob next = followCarcass(gob, lastRc, emptyMenus > 0);
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
                        new FreeInventory2(context).run(gui);
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
                                    new FreeInventory2(context).run(gui);
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
                Gob replaced = followCarcass(gob, lastRc, true);
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
}
