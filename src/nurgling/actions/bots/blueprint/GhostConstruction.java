package nurgling.actions.bots.blueprint;

import haven.*;
import haven.res.gfx.terobjs.consobj.Consobj;
import nurgling.GhostAlpha;
import nurgling.NGameUI;
import nurgling.NGob;
import nurgling.NHitBox;
import nurgling.NISBox;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.actions.PathFinder;
import nurgling.NCore;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.pf.NHitBoxD;
import nurgling.planning.PlanningGhost;
import nurgling.planning.PlanningLayerManager;
import nurgling.tasks.NTask;
import nurgling.widgets.NBasePlannerWidget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static haven.OCache.posres;

/**
 * Builds one planner ghost: walk there, look at the spot, place the sign with the verified menu
 * button, then feed the construction window until the building stands.
 * <p>
 * Derived from {@link nurgling.actions.Build} (plob race guard, finish-sign loop, Build/drink
 * cycle) but keyed by the ghost instead of a hand-written recipe: the button comes from
 * {@link BuildPaginae}, the materials from the construction window itself. Unlike Build it
 * never waits blindly: a refused placement, a missing material or a sign that stops progressing
 * becomes a skip with a reason.
 */
final class GhostConstruction
{
    /** The run cannot continue without risking an unrelated action or item. */
    static final class FatalException extends Exception
    {
        FatalException(String msg) { super(msg); }
    }

    private static final double SPOT_TOLERANCE = 1.5;
    /** One map grid: farther than this, the player is somewhere else (a house, a mine) or far off. */
    private static final double NEAR = MCache.cmaps.x * MCache.tilesz.x;
    private static final long PLOB_TIMEOUT_MS = 5000;
    private static final long PLACE_TIMEOUT_MS = 10000;
    private static final long WINDOW_TIMEOUT_MS = 5000;
    private static final long PROG_TIMEOUT_MS = 15_000;
    private static final int MAX_PASSES = 60;
    private static final int MAX_MATERIAL_UNITS = 10_000;

    private final NGameUI gui;
    private final PlanningLayerManager plan;
    private final MaterialFetcher fetcher;
    private Loader.Future<MapView.Plob> ownedPlacing;
    private Window ownedWindow;
    private NCore.LastActions ownedWindowAction;
    private long ownedSignId = -1;

    GhostConstruction(NGameUI gui, PlanningLayerManager plan, MaterialFetcher fetcher)
    {
        this.gui = gui;
        this.plan = plan;
        this.fetcher = fetcher;
    }

    void build(GhostJob job) throws InterruptedException, FatalException
    {
        PlanningGhost g = job.ghost;
        closeOwnedUi();
        if (!plan.stillMatches(g) || gui.vhand != null || gui.map.placing != null
                || constructionWindow() != null)
            throw new FatalException(L10n.get("bbuilder.safe.changed"));
        // A building already standing on a loaded ghost needs no walk.
        Coord2d pos = plan.worldPosOf(g);
        Gob there = (pos == null) ? null : uniqueAt(pos);
        if (standing(g, there, pos))
        {
            job.status = GhostJob.Status.ALREADY_BUILT;
            return;
        }
        if (!goTo(g))
        {
            job.skip(L10n.get("bbuilder.reason.unreachable"));
            return;
        }
        pos = plan.worldPosOf(g);
        if (pos == null)
        {
            job.skip(L10n.get("bbuilder.reason.not_loaded"));
            return;
        }

        if (!plan.stillMatches(g))
            throw new FatalException(L10n.get("bbuilder.safe.changed"));
        there = uniqueAt(pos);
        if (standing(g, there, pos))
        {
            job.status = GhostJob.Status.ALREADY_BUILT;
            return;
        }
        if (there != null && isSign(there))
        {
            if (!samePositionAndAngle(g, there, pos) || !g.resName.equals(waitSignTarget(there)))
            {
                job.skip(L10n.get("bbuilder.reason.other_sign"));
                return;
            }
            ownedSignId = there.id;
        } else if (there != null) {
            if (there.ngob == null || there.ngob.name == null)
                throw new FatalException(L10n.get("bbuilder.safe.site"));
            job.skip(L10n.get("bbuilder.reason.blocked", NBasePlannerWidget.shortName(there.ngob.name)));
            return;
        } else if (!place(job, pos))
        {
            return;
        }
        finish(job);
    }

    /* ---------------- navigation ---------------- */

    /**
     * Walk until the ghost's grid is loaded and its spot locally reachable. A spot already
     * holding a sign counts as reached: the sign is approached by its own path later.
     */
    private boolean goTo(PlanningGhost g) throws InterruptedException
    {
        Coord2d wp = plan.worldPosOf(g);
        if (near(wp) && (PathFinder.isAvailable(wp) || realGobAt(wp) != null))
            return true;
        ChunkNavManager nav = (gui.map instanceof NMapView) ? ((NMapView) gui.map).getChunkNavManager() : null;
        if (nav != null && nav.isInitialized())
        {
            ChunkPath path = planNear(nav, g);
            if (path != null)
                nav.navigateWithPath(path, null, gui);
        }
        return near(plan.worldPosOf(g));
    }

    /**
     * A chunk path to the ghost's tile or, once a sign covers it (a cabin's middle is several tiles
     * inside its footprint), to the nearest tile around it that has one.
     */
    private static ChunkPath planNear(ChunkNavManager nav, PlanningGhost g)
    {
        Coord mid = Coord2d.of(g.ox, g.oy).floor(MCache.tilesz);
        for (int r = 0; r <= 10; r += 2)
        {
            Coord[] ring = (r == 0) ? new Coord[] {Coord.z}
                    : new Coord[] {Coord.of(r, 0), Coord.of(-r, 0), Coord.of(0, r), Coord.of(0, -r)};
            for (Coord d : ring)
            {
                Coord t = mid.add(d);
                if (t.x < 0 || t.y < 0 || t.x >= MCache.cmaps.x || t.y >= MCache.cmaps.y) continue;
                ChunkPath path = nav.planToGridCoord(g.gridId, t);
                if (path != null) return path;
            }
        }
        return null;
    }

    /**
     * True when the player is out where the ghost is. Inside a house or a mine the ghost's grid can
     * still be in the map cache, so a known position alone does not mean the spot is near.
     */
    private static boolean near(Coord2d wp)
    {
        Gob player = NUtils.player();
        return wp != null && player != null && wp.dist(player.rc) < NEAR;
    }

    private boolean approach(Gob target) throws InterruptedException
    {
        PathFinder pf = new PathFinder(target);
        pf.isHardMode = true;
        return pf.run(gui).IsSuccess();
    }

    /* ---------------- placement ---------------- */

    private boolean place(GhostJob job, Coord2d pos) throws InterruptedException, FatalException
    {
        PlanningGhost g = job.ghost;
        List<MenuGrid.Pagina> candidates = BuildPaginae.candidates(gui, g.resName);
        if (candidates.isEmpty())
        {
            job.skip(L10n.get("bbuilder.reason.no_button", g.resName));
            return false;
        }
        MapView.Plob plob = null;
        for (MenuGrid.Pagina pag : candidates)
        {
            if (!plan.stillMatches(g) || gui.vhand != null || gui.map.placing != null)
                throw new FatalException(L10n.get("bbuilder.safe.changed"));
            pag.button().use(new MenuGrid.Interaction(1, 0));
            MapView.Plob p = waitPlob();
            if (p == null)
                throw new FatalException(L10n.get("bbuilder.safe.site"));
            if (p != null && g.resName.equals(plobRes(p)))
            {
                plob = p;
                job.buttonName = pag.button().name();
                BuildPaginae.learn(g.resName, BuildPaginae.paginaRes(pag));
                break;
            }
            if (plobRes(p) == null)
                throw new FatalException(L10n.get("bbuilder.safe.site"));
            cancelOwnedPlacing();
        }
        if (plob == null)
        {
            job.skip(L10n.get("bbuilder.reason.wrong_button", g.resName));
            return false;
        }
        double angle = normAngle(g.angleRadians());
        plob.a = angle;
        NHitBox hb = (plob.ngob != null && plob.ngob.hitBox != null) ? plob.ngob.hitBox : NHitBox.findCustom(g.resName);

        Gob blocker = blockerOf(hb, pos, angle);
        if (blocker != null)
        {
            cancelOwnedPlacing();
            job.skip(L10n.get("bbuilder.reason.blocked", NBasePlannerWidget.shortName(blocker.ngob.name)));
            return false;
        }

        PathFinder pf = new PathFinder(NGob.getDummy(pos, angle, hb), true);
        pf.isHardMode = true;
        if (!pf.run(gui).IsSuccess())
        {
            cancelOwnedPlacing();
            job.skip(L10n.get("bbuilder.reason.cant_walk"));
            return false;
        }

        if (!plan.stillMatches(g) || gui.vhand != null || gui.map.placing != ownedPlacing)
            throw new FatalException(L10n.get("bbuilder.safe.changed"));
        Set<Long> before = spotIds(pos);
        gui.ui.dropLastError();
        gui.map.wdgmsg("place", pos.floor(posres), (int) Math.round(angle * 32768 / Math.PI), 1, 0);
        final String[] error = {null};
        long deadline = System.currentTimeMillis() + PLACE_TIMEOUT_MS;
        NUtils.addTask(new NTask()
        {
            @Override
            public boolean check()
            {
                String err = gui.ui.getLastError();
                if (err != null) error[0] = err;
                Gob s = newSignAt(pos, before);
                return error[0] != null || s != null || System.currentTimeMillis() > deadline;
            }
        });
        Gob sign = newSignAt(pos, before);
        if ((sign == null || !isSign(sign)) && error[0] != null)
        {
            cancelOwnedPlacing();
            job.skip(L10n.get("bbuilder.reason.refused", error[0]));
            return false;
        }
        if (sign == null || !isSign(sign))
        {
            throw new FatalException(L10n.get("bbuilder.safe.busy"));
        }
        if (!samePositionAndAngle(g, sign, pos) || !g.resName.equals(waitSignTarget(sign)))
            throw new FatalException(L10n.get("bbuilder.safe.site"));
        ownedSignId = sign.id;
        cancelOwnedPlacing();
        long clearDeadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        NUtils.addTask(new NTask() {
            @Override public boolean check() {
                return gui.map.placing == null || System.currentTimeMillis() > clearDeadline;
            }
        });
        if (gui.map.placing != null)
            throw new FatalException(L10n.get("bbuilder.safe.site"));
        return true;
    }

    private MapView.Plob waitPlob() throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + PLOB_TIMEOUT_MS;
        for (int attempt = 0; attempt < 3; attempt++)
        {
            NUtils.addTask(new NTask()
            {
                @Override
                public boolean check()
                {
                    Loader.Future<MapView.Plob> p = gui.map.placing;
                    return (p != null && p.ready()) || System.currentTimeMillis() > deadline;
                }
            });
            // Snapshot: placing can be reassigned between the wait and the get (see Build.waitAndGetPlob).
            Loader.Future<MapView.Plob> snapshot = gui.map.placing;
            if (snapshot != null && snapshot.ready())
            {
                ownedPlacing = snapshot;
                return snapshot.get();
            }
            if (System.currentTimeMillis() > deadline)
                return null;
        }
        return null;
    }

    private static String plobRes(MapView.Plob p)
    {
        ResDrawable rd = p.getattr(ResDrawable.class);
        try
        {
            return (rd != null && rd.res != null) ? rd.res.get().name : null;
        } catch (Loading l)
        {
            return null;
        }
    }

    private void cancelOwnedPlacing()
    {
        if (ownedPlacing != null && gui.map.placing == ownedPlacing)
            gui.map.uimsg("unplace");
        ownedPlacing = null;
    }

    /* ---------------- construction ---------------- */

    private void finish(GhostJob job) throws InterruptedException, FatalException
    {
        PlanningGhost g = job.ghost;
        FinishPassBudget budget = new FinishPassBudget();
        while (budget.next())
        {
            if (!plan.stillMatches(g) || gui.vhand != null)
                throw new FatalException(L10n.get("bbuilder.safe.changed"));
            // Re-resolved every pass: trips for materials or water can move the map origin.
            Coord2d pos = plan.worldPosOf(g);
            if (pos == null)
                throw new FatalException(L10n.get("bbuilder.safe.unconfirmed"));
            Gob sign = findOwnedSign(pos, g);
            if (sign == null)
            {
                Gob standing = waitBuiltAt(pos, g);
                if (standing != null)
                    job.status = GhostJob.Status.BUILT;
                else
                    throw new FatalException(L10n.get("bbuilder.safe.site"));
                return;
            }
            Window wnd = openWindow(sign);
            if (wnd == null)
            {
                job.skip(L10n.get("bbuilder.reason.cant_open"));
                return;
            }
            Map<String, int[]> slots = readSlots(wnd);
            if (slots == null)
                throw new FatalException(L10n.get("bbuilder.safe.material"));
            budget.observeInitialSlots(slots);
            Map<String, Integer> required = new LinkedHashMap<>();
            Map<String, Integer> missing = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> e : slots.entrySet())
            {
                int in = e.getValue()[0], free = e.getValue()[1];
                if (in < 0 || free < 0 || in > 10000 || free > 10000)
                    throw new FatalException(L10n.get("bbuilder.safe.material"));
                required.put(e.getKey(), in + free);
                if (free > 0)
                    missing.put(e.getKey(), free);
            }
            BuildRecipes.rememberFromWindow(g.resName, required);
            if (!missing.isEmpty()) {
                String material = missing.keySet().iterator().next();
                int before = slots.get(material)[0];
                if (MaterialFetcher.countInInventory(gui, material) <= 0) {
                    closeOwnedWindow();
                    awaitNoConstructionWindow();
                    if (!fetcher.fetchOne(gui, material)) {
                        job.skip(L10n.get("bbuilder.reason.no_material", material));
                        return;
                    }
                    if (!goTo(g) || findOwnedSign(plan.worldPosOf(g), g) == null)
                        throw new FatalException(L10n.get("bbuilder.safe.site"));
                    continue;
                }
                NISBox box = slotBox(wnd, material);
                if (box == null || !sameWindowAndSign(wnd, sign, g, pos))
                    throw new FatalException(L10n.get("bbuilder.safe.changed"));
                depositOne(new Deposit() {
                    @Override public boolean owned() { return sameWindowAndSign(wnd, sign, g, pos); }
                    @Override public int slotCount() { return box.calcCount(); }
                    @Override public int heldUnits() throws InterruptedException {
                        return MaterialFetcher.countInInventory(gui, material);
                    }
                    @Override public void sendOne() { box.put(1); }
                    @Override public void await(int oldCount) throws InterruptedException {
                        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
                        NUtils.addTask(new NTask() {
                            @Override public boolean check() {
                                return box.calcCount() > oldCount || System.currentTimeMillis() > deadline;
                            }
                        });
                    }
                });
                continue;
            }

            if (!sameWindowAndSign(wnd, sign, g, pos))
                throw new FatalException(L10n.get("bbuilder.safe.changed"));
            if (gui.prog != null)
                throw new FatalException(L10n.get("bbuilder.safe.progress"));
            float beforeDone = signDone(sign);
            NUtils.startBuild(wnd);
            long deadline = System.currentTimeMillis() + PROG_TIMEOUT_MS;
            NUtils.addTask(new NTask() {
                @Override public boolean check() {
                    return gui.prog != null || findOwnedSign(pos, g) == null
                            || signDone(sign) > beforeDone || System.currentTimeMillis() > deadline;
                }
            });
            if (gui.prog == null && findOwnedSign(pos, g) != null && signDone(sign) <= beforeDone)
                throw new FatalException(L10n.get("bbuilder.safe.progress"));
            if (gui.prog != null) {
                long completion = System.currentTimeMillis() + 60_000;
                NUtils.addTask(new NTask() {
                    @Override public boolean check() {
                        return gui.prog == null || System.currentTimeMillis() > completion;
                    }
                });
                if (gui.prog != null)
                    throw new FatalException(L10n.get("bbuilder.safe.progress"));
            }
        }
        throw new FatalException(L10n.get("bbuilder.safe.limit"));
    }

    /** One confirmed fetch and one deposit can be needed for every initially missing unit. */
    static final class FinishPassBudget {
        private int limit = MAX_PASSES;
        private int used;
        private boolean initialized;

        boolean next() { return used++ < limit; }

        void observeInitialSlots(Map<String, int[]> slots) throws FatalException {
            if (initialized) return;
            long missing = 0;
            for (int[] count : slots.values()) {
                if (count == null || count.length < 2 || count[1] < 0)
                    throw new FatalException(L10n.get("bbuilder.safe.material"));
                missing += count[1];
            }
            if (missing > MAX_MATERIAL_UNITS)
                throw new FatalException(L10n.get("bbuilder.safe.material"));
            limit = (int) Math.max(MAX_PASSES, missing * 2 + 10);
            initialized = true;
        }
    }

    interface Deposit {
        boolean owned();
        int slotCount();
        int heldUnits() throws InterruptedException;
        void sendOne();
        void await(int oldCount) throws InterruptedException;
    }

    static void depositOne(Deposit port) throws InterruptedException, FatalException {
        if (!port.owned()) throw new FatalException(L10n.get("bbuilder.safe.window"));
        int beforeSlot = port.slotCount(), beforeUnits = port.heldUnits();
        if (beforeSlot < 0 || beforeUnits <= 0)
            throw new FatalException(L10n.get("bbuilder.safe.material"));
        port.sendOne();
        port.await(beforeSlot);
        if (!port.owned() || port.slotCount() != beforeSlot + 1 || port.heldUnits() >= beforeUnits)
            throw new FatalException(L10n.get("bbuilder.safe.material"));
    }

    /** A vanished sign is only success once the exact final resource, position and angle appear. */
    private Gob waitBuiltAt(Coord2d pos, PlanningGhost ghost) throws InterruptedException, FatalException {
        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        NUtils.addTask(new NTask() {
            @Override public boolean check() {
                try { return standing(ghost, uniqueAt(pos), pos) || System.currentTimeMillis() > deadline; }
                catch (FatalException e) { return true; }
            }
        });
        Gob candidate = uniqueAt(pos);
        return standing(ghost, candidate, pos) ? candidate : null;
    }

    /** {@link #signTarget}, waiting while the sign's sprite or target resource is still loading. */
    private String waitSignTarget(Gob sign) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        NUtils.addTask(new NTask()
        {
            @Override
            public boolean check()
            {
                return signTarget(sign) != null || System.currentTimeMillis() > deadline;
            }
        });
        return signTarget(sign);
    }

    void closeOwnedUi() {
        cancelOwnedPlacing();
        closeOwnedWindow();
    }

    private void closeOwnedWindow() {
        if (ownedWindow != null && ownedWindow.parent != null && gui.ui.core != null
                && gui.ui.core.getLastActions() == ownedWindowAction)
            ownedWindow.wdgmsg("close");
        ownedWindow = null;
        ownedWindowAction = null;
    }

    private void awaitNoConstructionWindow() throws InterruptedException, FatalException {
        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        NUtils.addTask(new NTask() {
            @Override public boolean check() {
                return constructionWindow() == null || System.currentTimeMillis() > deadline;
            }
        });
        if (constructionWindow() != null)
            throw new FatalException(L10n.get("bbuilder.safe.window"));
    }

    /** The construction window for {@code sign}, opening it when needed. */
    private Window openWindow(Gob sign) throws InterruptedException, FatalException
    {
        if (ownedWindow != null && ownedWindow.parent != null)
            return ownedWindow;
        if (constructionWindow() != null)
            throw new FatalException(L10n.get("bbuilder.safe.window"));
        if (!approach(sign))
            return null;
        if (nurgling.tools.Finder.findGob(sign.id) != sign)
            throw new FatalException(L10n.get("bbuilder.safe.changed"));
        if (gui.vhand != null || gui.map.placing != null || constructionWindow() != null)
            throw new FatalException(L10n.get("bbuilder.safe.changed"));
        gui.ui.core.setLastAction(sign);
        NCore.LastActions marker = gui.ui.core.getLastActions();
        NUtils.rclickGob(sign);
        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        NUtils.addTask(new NTask()
        {
            @Override
            public boolean check()
            {
                return gui.ui.core.getLastActions() != marker || constructionWindow() != null
                        || System.currentTimeMillis() > deadline;
            }
        });
        if (gui.ui.core.getLastActions() != marker)
            throw new FatalException(L10n.get("bbuilder.safe.busy"));
        Window candidate = constructionWindow();
        if (candidate == null || constructionWindows() != 1)
            throw new FatalException(L10n.get("bbuilder.safe.window"));
        ownedWindow = candidate;
        ownedWindowAction = marker;
        return candidate;
    }

    /** An open window with a "Build" button and material boxes. */
    private Window constructionWindow()
    {
        for (Widget w = gui.lchild; w != null; w = w.prev)
        {
            if (!(w instanceof Window)) continue;
            boolean build = false;
            for (Widget c = w.lchild; c != null; c = c.prev)
                if (c instanceof Button && ((Button) c).text != null && "Build".equals(((Button) c).text.text)) build = true;
            if (build && !boxes(w).isEmpty()) return (Window) w;
        }
        return null;
    }

    private int constructionWindows() {
        int count = 0;
        for (Widget w = gui.lchild; w != null; w = w.prev) {
            if (!(w instanceof Window)) continue;
            boolean build = false;
            for (Widget c = w.lchild; c != null; c = c.prev)
                if (c instanceof Button && ((Button)c).text != null
                        && "Build".equals(((Button)c).text.text)) build = true;
            if (build && !boxes(w).isEmpty()) count++;
        }
        return count;
    }

    private static List<NISBox> boxes(Widget root)
    {
        List<NISBox> out = new ArrayList<>();
        for (Widget c = root.child; c != null; c = c.next)
        {
            if (c instanceof NISBox) out.add((NISBox) c);
            else out.addAll(boxes(c));
        }
        return out;
    }

    /** Material name to {in sign, still needed}; -1 where unreadable. Null while resources load. */
    private Map<String, int[]> readSlots(Window wnd) throws InterruptedException
    {
        final Map<String, int[]> out = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        final boolean[] ok = {false};
        NUtils.addTask(new NTask()
        {
            @Override
            public boolean check()
            {
                out.clear();
                try
                {
                    for (NISBox box : boxes(wnd))
                    {
                        Resource res = box.itemres.get();
                        Resource.Tooltip tt = res.layer(Resource.tooltip);
                        if (tt == null || tt.t == null || tt.t.isEmpty())
                            return System.currentTimeMillis() > deadline;
                        String name = tt.t;
                        if (out.containsKey(name))
                            return true; // ambiguous same-name boxes are rejected below
                        out.put(name, new int[]{box.calcCount(), box.calcFreeSpace()});
                    }
                    ok[0] = true;
                    return true;
                } catch (Loading l)
                {
                    return System.currentTimeMillis() > deadline;
                }
            }
        });
        return ok[0] && !out.isEmpty() && out.size() == boxes(wnd).size() ? out : null;
    }

    private NISBox slotBox(Window wnd, String material) {
        NISBox found = null;
        for (NISBox box : boxes(wnd)) {
            try {
                Resource.Tooltip tip = box.itemres.get().layer(Resource.tooltip);
                if (tip != null && material.equals(tip.t)) {
                    if (found != null) return null;
                    found = box;
                }
            } catch (Loading loading) { return null; }
        }
        return found;
    }

    private boolean sameWindowAndSign(Window wnd, Gob sign, PlanningGhost ghost, Coord2d pos) {
        return wnd != null && wnd == ownedWindow && wnd.parent != null
                && gui.ui.core.getLastActions() == ownedWindowAction && constructionWindow() == wnd
                && constructionWindows() == 1
                && findOwnedSign(pos, ghost) == sign && plan.stillMatches(ghost)
                && gui.vhand == null && gui.map.placing == null;
    }

    private Gob findOwnedSign(Coord2d pos, PlanningGhost ghost) {
        if (pos == null || ownedSignId <= 0) return null;
        Gob sign = nurgling.tools.Finder.findGob(ownedSignId);
        return sign != null && isSign(sign) && samePositionAndAngle(ghost, sign, pos)
                && ghost.resName.equals(signTarget(sign)) ? sign : null;
    }

    private static boolean samePositionAndAngle(PlanningGhost ghost, Gob gob, Coord2d pos) {
        if (gob == null || gob.rc == null || gob.rc.dist(pos) > SPOT_TOLERANCE) return false;
        double delta = normAngle(gob.a - ghost.angleRadians());
        return Math.min(delta, 2 * Math.PI - delta) <= 0.10;
    }

    private static boolean standing(PlanningGhost ghost, Gob gob, Coord2d pos) {
        return gob != null && gob.ngob != null && ghost.resName.equals(gob.ngob.name)
                && samePositionAndAngle(ghost, gob, pos);
    }

    private Gob uniqueAt(Coord2d pos) throws FatalException {
        Gob only = null;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob instanceof OCache.Virtual || gob instanceof MapView.Plob || gob.id <= 0
                        || gob.id == NUtils.playerID() || gob.getattr(GhostAlpha.class) != null
                        || gob.rc == null || gob.rc.dist(pos) > SPOT_TOLERANCE) continue;
                if (only != null && only.id != gob.id)
                    throw new FatalException(L10n.get("bbuilder.safe.site"));
                only = gob;
            }
        }
        return only;
    }

    private Set<Long> spotIds(Coord2d pos) {
        Set<Long> ids = new HashSet<>();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc)
                if (gob.id > 0 && gob.rc != null && gob.rc.dist(pos) <= SPOT_TOLERANCE)
                    ids.add(gob.id);
        }
        return ids;
    }

    private Gob newSignAt(Coord2d pos, Set<Long> before) {
        Gob result = null;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob.id <= 0 || before.contains(gob.id) || gob.rc == null
                        || gob.rc.dist(pos) > SPOT_TOLERANCE || !isSign(gob)) continue;
                if (result != null) return null;
                result = gob;
            }
        }
        return result;
    }

    /* ---------------- spot inspection ---------------- */

    /** The real (non-ghost, non-cursor) gob standing on {@code pos}, if any. */
    private Gob realGobAt(Coord2d pos)
    {
        return realGobAt(gui, pos);
    }

    static Gob realGobAt(NGameUI gui, Coord2d pos)
    {
        Gob best = null;
        double bestDist = SPOT_TOLERANCE;
        synchronized (gui.ui.sess.glob.oc)
        {
            for (Gob gob : gui.ui.sess.glob.oc)
            {
                if (gob instanceof OCache.Virtual || gob instanceof MapView.Plob || gob.id <= 0) continue;
                if (gob.id == NUtils.playerID()) continue;
                if (gob.getattr(GhostAlpha.class) != null || gob.ngob == null || gob.ngob.name == null) continue;
                double d = gob.rc.dist(pos);
                if (d < bestDist)
                {
                    bestDist = d;
                    best = gob;
                }
            }
        }
        return best;
    }

    /** A real gob whose hitbox overlaps the building's footprint, or null. */
    private Gob blockerOf(NHitBox hb, Coord2d pos, double angle)
    {
        if (hb == null) return null;
        NHitBoxD box = new NHitBoxD(hb.begin, hb.end, pos, angle);
        Gob player = NUtils.player();
        synchronized (gui.ui.sess.glob.oc)
        {
            for (Gob gob : gui.ui.sess.glob.oc)
            {
                if (gob instanceof OCache.Virtual || gob instanceof MapView.Plob || gob.id <= 0) continue;
                if (player != null && gob.id == player.id) continue;
                if (gob.getattr(GhostAlpha.class) != null || gob.getattr(Following.class) != null) continue;
                if (gob.ngob == null || gob.ngob.hitBox == null) continue;
                if (new NHitBoxD(gob).intersects(box, false)) return gob;
            }
        }
        return null;
    }

    static boolean isSign(Gob gob)
    {
        return gob.ngob != null && "gfx/terobjs/consobj".equals(gob.ngob.name);
    }

    /** What the construction sign will become, or null while unknown. */
    static String signTarget(Gob sign)
    {
        ResDrawable rd = sign.getattr(ResDrawable.class);
        if (rd == null || !(rd.spr instanceof Consobj)) return null;
        Consobj c = (Consobj) rd.spr;
        try
        {
            return (c.built != null) ? c.built.res.get().name : null;
        } catch (Loading l)
        {
            return null;
        }
    }

    private static float signDone(Gob sign)
    {
        ResDrawable rd = sign.getattr(ResDrawable.class);
        return (rd != null && rd.spr instanceof Consobj) ? ((Consobj) rd.spr).done : -1;
    }

    /** NHitBoxD.move_ortho switches on quarter turns; a negative angle hits no case. */
    static double normAngle(double a)
    {
        a %= 2 * Math.PI;
        return (a < 0) ? a + 2 * Math.PI : a;
    }
}
