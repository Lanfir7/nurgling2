package nurgling.actions;

import haven.*;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NGob;
import nurgling.NHitBox;
import nurgling.NISBox;
import nurgling.NUtils;
import nurgling.tasks.NTask;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.VSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static haven.MCache.tilesz;
import static haven.OCache.posres;

/**
 * Adds a post to a milestone road: right-clicks the road's last milestone, presses Extend,
 * carries the placement to its spot and builds the new milestone there (5 stones). A
 * half-built milestone already standing there is finished instead. After SUCCESS, built()
 * is the new milestone, which is the road's new last post.
 *
 * The road doesn't run between the milestones themselves. The placement carries a
 * routeindicator overlay (loftar's Route sprite) whose line goes from the last milestone
 * plus srcoff to the new one plus dstoff turned by the new one's facing, and the game
 * refuses the post if that line crosses rock. dstoff is (33, 0): the road ends three tiles
 * in front of a milestone (measured 2026-10-21). So the caller names a tile on the open
 * line the road should follow (spot), the way along it and the side to stand on; the
 * milestone stands one tile out from spot and is turned so its road end falls back on the
 * line, behind spot. If the road's line or the milestone's tile is still rock, nothing is
 * placed and rockInTheWay() lists the tiles to dig before trying again.
 *
 * The build loop is copied from FrameTunneler.build(), which is itself a copy of
 * TunnelingBot's; check fixes against all three.
 */
public class ExtendRoad implements Action {

    public static final NAlias MILESTONES = new NAlias("gfx/terobjs/road/milestone");
    public static final int STONES_PER_POST = 5;

    private static final NAlias CONSTRUCTION = new NAlias("gfx/terobjs/consobj");
    private static final NAlias ROCK_TILES = new NAlias("rock", "tiles/cave");
    private static final String ROUTE_INDICATOR = "gfx/terobjs/road/routeindicator";
    private static final long WINDOW_WAIT_MS = 5_000;
    private static final long OVERLAY_WAIT_MS = 3_000;
    // Placing may still walk the last stretch before the construction window opens
    private static final long CONSTRUCTION_WAIT_MS = 15_000;
    private static final long BUILT_WAIT_MS = 5_000;
    private static final long BUILD_WAIT_MS = 15_000;
    private static final int BUILD_ATTEMPTS = 16;
    private static final double MAX_ROUTE_OFFSET = 64.0;
    private static final double MAX_ROUTE_LINE = 512.0;

    private final Gob tail;
    private final Coord2d spot;
    private final Coord2d along;
    private final Coord2d outward;
    private final double fallbackAngle;
    private Gob built = null;
    private List<Coord> rockInTheWay = new ArrayList<>();

    /* spot is on the line the road should follow, along points ahead on it, outward to the
     * side the milestone stands on; fallbackAngle is the facing used if the placement carries
     * no route offsets. */
    public ExtendRoad(Gob tail, Coord2d spot, Coord2d along, Coord2d outward, double fallbackAngle) {
        this.tail = tail;
        this.spot = spot;
        this.along = along.norm();
        this.outward = outward.norm();
        this.fallbackAngle = fallbackAngle;
    }

    public Gob built() {
        return built;
    }

    /* After a failed run: the rock tiles on the road line or under the milestone, nearest
     * the last milestone first; empty if rock wasn't the problem. */
    public List<Coord> rockInTheWay() {
        return rockInTheWay;
    }

    /* Where a milestone stands, how it faces, and where its road then ends. */
    public static final class Siting {
        public final Coord2d pos, attach;
        public final double angle;

        Siting(Coord2d pos, Coord2d attach, Coord2d dstoff) {
            this.pos = pos;
            this.attach = attach;
            this.angle = Math.atan2(attach.y - pos.y, attach.x - pos.x) - Math.atan2(dstoff.y, dstoff.x);
        }
    }

    /* The milestone one tile out from spot, turned so its road end (dstoff turned by the
     * facing) lands on the line through spot, behind it. A road end shorter than a tile
     * lands on spot itself. */
    public static Siting site(Coord2d spot, Coord2d along, Coord2d outward, Coord2d dstoff) {
        double reach = dstoff.abs();
        double out = tilesz.x;
        if (reach <= out) {
            return new Siting(spot.add(outward.norm().mul(reach)), spot, dstoff);
        }
        double back = Math.sqrt(reach * reach - out * out);
        return new Siting(spot.add(outward.norm().mul(out)), spot.sub(along.norm().mul(back)), dstoff);
    }

    /* The tiles the straight line from a to b passes over, from a's end. */
    public static Set<Coord> lineTiles(Coord2d a, Coord2d b) {
        if (!finite(a) || !finite(b) || !Double.isFinite(a.dist(b)) || a.dist(b) > MAX_ROUTE_LINE)
            throw new IllegalArgumentException("Road route is not bounded");
        Set<Coord> tiles = new LinkedHashSet<>();
        int steps = Math.max(1, (int) Math.ceil(a.dist(b)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            tiles.add(new Coord((int) Math.floor(x / tilesz.x), (int) Math.floor(y / tilesz.y)));
        }
        return tiles;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Results handEmpty = emptyHand(gui);
        if (!handEmpty.IsSuccess()) {
            return handEmpty;
        }
        if (gui.map.placing != null)
            return Results.ERROR("Road: finish the current placement before extending the road");
        NUtils.getDefaultCur();
        rockInTheWay = new ArrayList<>();
        Set<Long> before = milestoneIds();

        // Route offsets are only available after Extend. Without them, a nearby generic
        // construction cannot be proven to be ours; stop instead of duplicating or building it.
        if (!unfinishedAt(Finder.findGobs(CONSTRUCTION), spot).isEmpty())
            return Results.ERROR("Road: unfinished construction near the next post; finish it manually before restarting");
        Coord2d expected;
        Window cons;
            int stones = countStones(gui.getInventory().getItems(VSpec.getNamesInCategory("Stone")));
            if (stones < STONES_PER_POST) {
                return Results.ERROR("Road: need " + STONES_PER_POST + " stones for the next milestone, have " + stones);
            }

            Button extend = openExtend(gui, tail);
            if (extend == null) {
                return Results.ERROR("Road: can't reach the road's last milestone, or it has no Extend button");
            }
            Window info = windowOf(extend);

            NUtils.getUI().dropLastError();
            extend.click();
            WaitPlacing placing = new WaitPlacing(WINDOW_WAIT_MS);
            NUtils.addTask(placing);
            if (!placing.ready) {
                cancelPlacement(gui);
                closeWindow(gui, info);
                return Results.ERROR("Road: pressing Extend gave no milestone to place" + reason(placing.error));
            }

            ReadRouteOffsets offsets = new ReadRouteOffsets(OVERLAY_WAIT_MS);
            NUtils.addTask(offsets);
            Coord2d pos, attach;
            double angle;
            Coord2d from = tail.rc;
            if (validRouteOffsets(offsets.srcoff, offsets.dstoff) && offsets.source == tail.id) {
                Siting siting = site(spot, along, outward, offsets.dstoff);
                pos = siting.pos;
                angle = siting.angle;
                attach = siting.attach;
                from = tail.rc.add(offsets.srcoff);
            } else {
                cancelPlacement(gui);
                closeWindow(gui, info);
                return Results.ERROR("Road: milestone route geometry is unavailable or belongs to another post");
            }
            expected = pos;
            gui.map.placing.get().a = angle;

            // Dig first rather than have the game refuse the road
            Set<Coord> mustBeOpen = lineTiles(from, attach);
            mustBeOpen.add(pos.floor(tilesz));
            for (Coord tile : mustBeOpen) {
                Boolean rock = rockState(gui, tile);
                if (rock == null) {
                    cancelPlacement(gui);
                    closeWindow(gui, info);
                    return Results.ERROR("Road: terrain on the milestone route is not loaded");
                }
                if (rock) {
                    rockInTheWay.add(tile);
                }
            }
            if (!rockInTheWay.isEmpty()) {
                cancelPlacement(gui);
                closeWindow(gui, info);
                return Results.ERROR("Road: " + rockInTheWay.size() + " rock tiles in the way of the road");
            }

            // Carry the placement down the tunnel ourselves; the server only walks straight
            // lines. The milestone window stays open until the placement is down, in case
            // closing it ends the extension.
            NHitBox hitBox = gui.map.placing.get().ngob.hitBox;
            if (hitBox == null) {
                hitBox = new NHitBox(new Coord2d(-5.5, -5.5), new Coord2d(5.5, 5.5));
            }
            PathFinder pf = new PathFinder(NGob.getDummy(pos, angle, hitBox), true);
            pf.isHardMode = true;
            Results walked = pf.run(gui);
            if (!walked.IsSuccess()) {
                cancelPlacement(gui);
                closeWindow(gui, info);
                return Results.ERROR("Road: can't reach the spot for the next milestone");
            }
            if (gui.map.placing == null) {
                closeWindow(gui, info);
                return Results.ERROR("Road: the milestone placement was cancelled on the way");
            }

            Set<Window> open = windows(gui);
            NUtils.getUI().dropLastError();
            gui.map.wdgmsg("place", pos.floor(posres), (int) Math.round(angle * 32768 / Math.PI), 1, 0);
            WaitNewWindow wait = new WaitNewWindow(gui, open, "Build", CONSTRUCTION_WAIT_MS);
            NUtils.addTask(wait);
            closeWindow(gui, info);
            if (wait.window == null) {
                cancelPlacement(gui);
                return Results.ERROR("Road: the milestone can't be placed there" + reason(wait.error));
            }
            cons = wait.window;

        Results result = build(gui, cons);
        if (!result.IsSuccess()) {
            return result;
        }

        NUtils.addTask(new NTask() {
            final long deadline = System.currentTimeMillis() + BUILT_WAIT_MS;

            @Override
            public boolean check() {
                for (Gob gob : Finder.findGobs(MILESTONES)) {
                    if (!before.contains(gob.id) && gob.rc.dist(expected) < 1.5 * tilesz.x) {
                        built = gob;
                        return true;
                    }
                }
                return System.currentTimeMillis() > deadline;
            }
        });
        if (built == null) {
            return Results.ERROR("Road: the new milestone didn't appear");
        }
        return Results.SUCCESS();
    }

    /* Preserve a held player item; never ground-drop it to open a road window. */
    public static Results emptyHand(NGameUI gui) throws InterruptedException {
        if (gui.vhand == null) {
            return Results.SUCCESS();
        }
        if (gui.getInventory() == null || gui.vhand.item.spr == null)
            return Results.ERROR("Road: put the held item away before building");
        Coord free = gui.getInventory().findFreeCoord(gui.vhand);
        if (free == null)
            return Results.ERROR("Road: inventory is full; put the held item away before building");
        gui.getInventory().wdgmsg("drop", free);
        NUtils.addTask(new NTask() {
            final long deadline = System.currentTimeMillis() + 3_000;

            @Override
            public boolean check() {
                return gui.vhand == null || System.currentTimeMillis() > deadline;
            }
        });
        if (gui.vhand != null) {
            return Results.ERROR("Road: couldn't return the held item to inventory");
        }
        return Results.SUCCESS();
    }

    static int countStones(List<WItem> stones) {
        int count = 0;
        for (WItem item : stones) {
            if (!(item.item instanceof NGItem)) continue;
            GItem.Amount amount = ((NGItem) item.item).getInfo(GItem.Amount.class);
            count += amount == null ? 1 : Math.max(0, amount.itemnum());
        }
        return count;
    }

    static List<Gob> unfinishedAt(List<Gob> candidates, Coord2d expected) {
        List<Gob> matches = new ArrayList<>();
        for (Gob gob : candidates) {
            if (gob == null || gob.ngob == null || gob.ngob.name == null || gob.rc == null)
                continue;
            if (!"gfx/terobjs/consobj".equals(gob.ngob.name) || gob.rc.dist(expected) > tilesz.x * 1.05)
                continue;
            matches.add(gob);
        }
        return matches;
    }

    private static boolean finite(Coord2d coord) {
        return coord != null && Double.isFinite(coord.x) && Double.isFinite(coord.y);
    }

    static boolean validRouteOffsets(Coord2d source, Coord2d destination) {
        return finite(source) && finite(destination) && source.abs() <= MAX_ROUTE_OFFSET
                && destination.abs() > 0 && destination.abs() <= MAX_ROUTE_OFFSET;
    }

    public static final class OpenExtend {
        public final Button button;
        public final Window window;
        public final boolean pending;

        public OpenExtend(Button button, Window window, boolean pending) {
            this.button = button;
            this.window = window;
            this.pending = pending;
        }
    }

    /* A timed-out click may still open its window later; callers must stop, never issue
     * another candidate click that could mistake that response for its own. */
    public static OpenExtend inspectExtend(NGameUI gui, Gob milestone) throws InterruptedException {
        if (gui.map.placing != null) return new OpenExtend(null, null, false);
        NUtils.getDefaultCur();
        Results walked = new PathFinder(milestone).run(gui);
        if (!walked.IsSuccess()) return new OpenExtend(null, null, false);
        Set<Window> open = windows(gui);
        NUtils.getUI().dropLastError();
        NUtils.rclickGob(milestone);
        WaitNewWindow wait = new WaitNewWindow(gui, open, null, WINDOW_WAIT_MS);
        NUtils.addTask(wait);
        if (wait.window == null) {
            return new OpenExtend(null, null, true);
        }
        return new OpenExtend(button(wait.window, "Extend"), wait.window, false);
    }

    public static Button openExtend(NGameUI gui, Gob milestone) throws InterruptedException {
        return inspectExtend(gui, milestone).button;
    }

    public static Window windowOf(Widget w) {
        while (w != null && !(w instanceof Window)) {
            w = w.parent;
        }
        return (Window) w;
    }

    public static void closeWindow(NGameUI gui, Window wnd) throws InterruptedException {
        if (wnd != null && gui.isWindowExist(wnd)) {
            new CloseTargetWindow(wnd).run(gui);
        }
    }

    /* Presses Build with finite attempts; the caller still verifies the new milestone. */
    private Results build(NGameUI gui, Window wnd) throws InterruptedException {
        for (int attempt = 0; gui.isWindowExist(wnd) && attempt < BUILD_ATTEMPTS; attempt++) {
            NUtils.getUI().dropLastError();
            int available = countStones(gui.getInventory().getItems(VSpec.getNamesInCategory("Stone")));
            String fillError = fillAndStart(wnd, available, (slot, oldFree) -> {
                final boolean[] received = {false};
                NUtils.addTask(new NTask() {
                    final long deadline = System.currentTimeMillis() + 3_000;
                    @Override public boolean check() {
                        received[0] = gui.isWindowExist(wnd) && slot.calcFreeSpace() >= 0
                                && slot.calcFreeSpace() < oldFree;
                        return received[0] || !gui.isWindowExist(wnd) || System.currentTimeMillis() > deadline;
                    }
                });
                return received[0];
            }, () -> NUtils.startBuild(wnd));
            if (fillError != null) {
                closeWindow(gui, wnd);
                return Results.ERROR("Road: " + fillError);
            }

            final boolean[] started = {false};
            NUtils.addTask(new NTask() {
                final long deadline = System.currentTimeMillis() + BUILD_WAIT_MS;

                @Override
                public boolean check() {
                    started[0] = gui.prog != null;
                    return started[0] || !gui.isWindowExist(wnd) || System.currentTimeMillis() > deadline;
                }
            });
            if (!started[0]) {
                if (!gui.isWindowExist(wnd)) {
                    break;
                }
                String err = NUtils.getUI().getLastError();
                closeWindow(gui, wnd);
                return Results.ERROR("Road: building the milestone didn't start" + reason(err)
                        + " - it needs " + STONES_PER_POST + " stones");
            }

            final int[] state = {0}; // 1 drink, 2 low energy, 3 build ended, 4 timeout
            NUtils.addTask(new NTask() {
                final long deadline = System.currentTimeMillis() + BUILD_WAIT_MS;
                @Override public boolean check() {
                    if (!gui.isWindowExist(wnd) || gui.prog == null) state[0] = 3;
                    else if (NUtils.getEnergy() < 0.22) state[0] = 2;
                    else if (NUtils.getStamina() <= 0.45) state[0] = 1;
                    else if (System.currentTimeMillis() > deadline) state[0] = 4;
                    return state[0] != 0;
                }
            });
            if (state[0] == 1) {
                if (!new Drink(0.9, false).run(gui).IsSuccess()) {
                    closeWindow(gui, wnd);
                    return Results.ERROR("Cannot drink");
                }
            } else if (state[0] == 2) {
                closeWindow(gui, wnd);
                return Results.ERROR("Low energy");
            } else if (state[0] == 4) {
                closeWindow(gui, wnd);
                return Results.ERROR("Road: milestone build timed out");
            } else {
                // Finished; give the window a moment to close
                NUtils.addTask(new NTask() {
                    final long deadline = System.currentTimeMillis() + 2_000;

                    @Override
                    public boolean check() {
                        return !gui.isWindowExist(wnd) || System.currentTimeMillis() > deadline;
                    }
                });
            }
        }
        if (gui.isWindowExist(wnd)) return Results.ERROR("Road: milestone build made no progress");
        return Results.SUCCESS();
    }

    @FunctionalInterface
    interface SlotReceipt {
        boolean received(NISBox slot, int oldFree) throws InterruptedException;
    }

    /* Only a five-stone milestone construction is allowed to pull from the inventory.
     * Confirm each server count change before issuing Build. */
    static String fillAndStart(Window wnd, int available, SlotReceipt receipt, Runnable start)
            throws InterruptedException {
        if (!expectedWindow(wnd, "Build")) return "unexpected construction window";
        Set<NISBox> slots = wnd.children(NISBox.class);
        if (slots.size() != 1) return "milestone stone slot is missing or ambiguous";
        NISBox slot = slots.iterator().next();
        Resource resource;
        try { resource = slot.itemres.get(); }
        catch (Loading unavailable) { return "milestone stone slot is not loaded"; }
        if (resource == null || !"gfx/invobjs/stone".equals(resource.name))
            return "milestone requires an unexpected material";
        int count = slot.calcCount();
        int free = slot.calcFreeSpace();
        if (count < 0 || free < 0 || count + free != STONES_PER_POST)
            return "milestone stone count is unavailable or unexpected";
        if (available < free) return "not enough stones to fill the milestone";
        for (int attempt = 0; free > 0 && attempt < STONES_PER_POST; attempt++) {
            // Send one transfer per confirmed count change. Sending all remaining stones
            // again after the first asynchronous acknowledgement can queue duplicates.
            slot.put(1);
            if (!receipt.received(slot, free)) return "stone transfer made no progress";
            int next = slot.calcFreeSpace();
            if (next < 0 || next >= free) return "stone transfer made no progress";
            free = next;
        }
        if (free != 0) return "milestone stone slot is not full";
        start.run();
        return null;
    }

    /* Right-click while placing is how the game cancels; drop the cursor here too in case
     * the server never answers. */
    private static void cancelPlacement(NGameUI gui) throws InterruptedException {
        if (gui.map.placing == null) {
            return;
        }
        Gob player = NUtils.player();
        if (player != null) {
            gui.map.wdgmsg("place", player.rc.floor(posres), 0, 3, 0);
            NUtils.addTask(new NTask() {
                final long deadline = System.currentTimeMillis() + 2_000;

                @Override
                public boolean check() {
                    return gui.map.placing == null || System.currentTimeMillis() > deadline;
                }
            });
        }
        if (gui.map.placing != null) {
            gui.map.placing.cancel();
            gui.map.placing = null;
        }
    }

    private static Set<Long> milestoneIds() {
        Set<Long> ids = new HashSet<>();
        for (Gob gob : Finder.findGobs(MILESTONES)) {
            ids.add(gob.id);
        }
        return ids;
    }

    private static Set<Window> windows(NGameUI gui) throws InterruptedException {
        Set<Window> found = new HashSet<>();
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                for (Widget w = gui.lchild; w != null; w = w.prev) {
                    if (w instanceof Window) {
                        found.add((Window) w);
                    }
                }
                return true;
            }
        });
        return found;
    }

    private static Button button(Window wnd, String text) {
        for (Button b : wnd.children(Button.class)) {
            if (b.text != null && text.equals(b.text.text)) {
                return b;
            }
        }
        return null;
    }

    private static String reason(String err) {
        return (err == null) ? "" : ": " + err;
    }

    static boolean expectedWindow(Window wnd, String buttonText) {
        if (wnd == null || wnd.cap == null || !"Milestone".equalsIgnoreCase(wnd.cap))
            return false;
        return buttonText == null || button(wnd, buttonText) != null;
    }

    /* Only a newly opened milestone window with the expected action qualifies. */
    private static class WaitNewWindow extends NTask {
        final NGameUI gui;
        final Set<Window> before;
        final String buttonText;
        final long deadline;
        Window window = null;
        String error = null;

        WaitNewWindow(NGameUI gui, Set<Window> before, String buttonText, long timeoutMs) {
            this.gui = gui;
            this.before = before;
            this.buttonText = buttonText;
            this.deadline = System.currentTimeMillis() + timeoutMs;
        }

        @Override
        public boolean check() {
            for (Widget w = gui.lchild; w != null; w = w.prev) {
                if (w instanceof Window && !before.contains(w)
                        && expectedWindow((Window) w, buttonText)) {
                    window = (Window) w;
                    return true;
                }
            }
            error = NUtils.getUI().getLastError();
            return error != null || System.currentTimeMillis() > deadline;
        }
    }

    private static Boolean rockState(NGameUI gui, Coord tile) {
        try {
            Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tile));
            return res == null ? null : NParser.checkName(res.name, ROCK_TILES);
        } catch (Loading unavailable) {
            return null;
        }
    }

    /* The placement's routeindicator: the road's line runs from gob src plus srcoff to the
     * placement plus dstoff turned by its facing, all in world units. The sprite's class comes
     * from the resource, so its public fields are read by name. */
    public static final class RouteOffsets {
        public final long src;
        public final Coord2d srcoff, dstoff;

        private RouteOffsets(long src, Coord2d srcoff, Coord2d dstoff) {
            this.src = src;
            this.srcoff = srcoff;
            this.dstoff = dstoff;
        }

        /* Null if the placement has no routeindicator (yet), or it can't be read. */
        public static RouteOffsets of(Gob plob) {
            for (Gob.Overlay ol : plob.ols) {
                if (ol.spr != null && ol.spr.res != null && ROUTE_INDICATOR.equals(ol.spr.res.name)) {
                    try {
                        Class<?> cl = ol.spr.getClass();
                        Coord3f so = (Coord3f) cl.getField("srcoff").get(ol.spr);
                        Coord3f dof = (Coord3f) cl.getField("dstoff").get(ol.spr);
                        long src = cl.getField("src").getLong(ol.spr);
                        if (so == null || dof == null) return null;
                        Coord2d source = Coord2d.of(so.x, so.y);
                        Coord2d destination = Coord2d.of(dof.x, dof.y);
                        return validRouteOffsets(source, destination)
                                ? new RouteOffsets(src, source, destination) : null;
                    } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException e) {
                        System.out.println("ExtendRoad: can't read the route offsets: " + e);
                        return null;
                    }
                }
            }
            return null;
        }
    }

    private static class ReadRouteOffsets extends NTask {
        final long deadline;
        long source;
        Coord2d srcoff = null, dstoff = null;

        ReadRouteOffsets(long timeoutMs) {
            this.deadline = System.currentTimeMillis() + timeoutMs;
        }

        @Override
        public boolean check() {
            MapView map = NUtils.getGameUI().map;
            if (map == null || map.placing == null || !map.placing.ready()) {
                return true;
            }
            RouteOffsets offsets = RouteOffsets.of(map.placing.get());
            if (offsets != null) {
                source = offsets.src;
                srcoff = offsets.srcoff;
                dstoff = offsets.dstoff;
                return true;
            }
            return System.currentTimeMillis() > deadline;
        }
    }

    private static class WaitPlacing extends NTask {
        final long deadline;
        boolean ready = false;
        String error = null;

        WaitPlacing(long timeoutMs) {
            this.deadline = System.currentTimeMillis() + timeoutMs;
        }

        @Override
        public boolean check() {
            MapView map = NUtils.getGameUI().map;
            ready = map != null && map.placing != null && map.placing.ready();
            if (ready) {
                return true;
            }
            error = NUtils.getUI().getLastError();
            return error != null || System.currentTimeMillis() > deadline;
        }
    }
}
