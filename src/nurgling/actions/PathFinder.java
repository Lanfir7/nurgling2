package nurgling.actions;

import haven.*;
import nurgling.*;
import nurgling.pf.*;
import nurgling.pf.Utils;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static nurgling.pf.Graph.getPath;

public class PathFinder implements Action {
    private final int VISIBLE_AREA = 41;
    public static double pfmdelta = 1.5;
    NPFMap pfmap = null;
    Coord start_pos = null;
    Coord end_pos = null;
    ArrayList<Coord> end_poses = null;
    public boolean isHardMode = false;
    public boolean waterMode = false;
    public boolean gatesAlwaysClosed = false;
    Coord2d begin;
    Coord2d end;
    long target_id = -2;
    public boolean isDynamic = false;
    Gob dummy;
    boolean dn = false;
    Mode mode = Mode.NEAREST;
    public boolean skipDN = false;
    public int maxMul = 200;
    private boolean alexandrPileBehavior = false;
    Gob gobInStartPos = null;
    private boolean startWasBlocked = false;
    double badDir = Double.MAX_VALUE;
    private final ArrayList<Gob> additionalObstacles = new ArrayList<>();
    /** Optional caller-owned abort; null preserves ordinary pathfinding. */
    private BooleanSupplier callerAbort = null;
    /** Sticky per-run state, so a quick resume cannot turn a pause into a path failure. */
    private boolean abortedByCaller = false;

    /** Opt-in live no-walk capsules used by Forager; null preserves ordinary pathfinding. */
    public Supplier<List<AvoidZone>> avoidZones = null;
    public boolean blockedByAvoidZones = false;
    public List<Coord2d> learnedBlocks = null;
    public int maxStalls = 3;
    private List<AvoidZone> plannedZones = Collections.emptyList();
    private boolean ignoreZones = false;
    private volatile long lastZoneReplanMs = 0;
    private final ArrayDeque<Long> zoneReplanTimes = new ArrayDeque<>();
    private int stalls = 0;
    protected boolean legZoneAborted = false;
    private static final long ZONE_CHECK_MS = 200, ZONE_REPLAN_MIN_MS = 1000, ZONE_STORM_WINDOW_MS = 15000;
    private static final int ZONE_STORM_MAX = 8;
    private static final double ZONE_START_CLEARANCE = MCache.tilesz.x, ZONE_ABORT_SLACK = MCache.tilesz.x;
    private static final double STALL_BLOCK_AHEAD = 7, STALL_BLOCK_R = MCache.tilesz.x / 2;

    /** A no-walk capsule: every point within r of core segment a-b. */
    public static final class AvoidZone {
        public final Coord2d a, b; public final double r; public final String label;
        public AvoidZone(Coord2d a, Coord2d b, double r, String label) { this.a=a; this.b=b; this.r=r; this.label=label; }
        public boolean contains(Coord2d p) { return dist(p) < r; }
        public static boolean anyContains(List<AvoidZone> zones, Coord2d p) { for(AvoidZone z:zones) if(z.contains(p)) return true; return false; }
        public double dist(Coord2d p) { return p.dist(closest(p,a,b)); }
        public double dist(Coord2d p, Coord2d q) {
            if(crosses(p,q,a,b)) return 0;
            return Math.min(Math.min(dist(p),dist(q)),Math.min(pointSegment(a,p,q),pointSegment(b,p,q)));
        }
        public Coord2d pushOut(Coord2d p, double margin) {
            Coord2d c=closest(p,a,b), away=p.sub(c);
            if(away.abs()<.01) { Coord2d ab=b.sub(a); away=ab.abs()<.01?Coord2d.of(1,0):Coord2d.of(-ab.y,ab.x); }
            return c.add(away.norm(r+margin));
        }
        private static Coord2d closest(Coord2d p, Coord2d s, Coord2d e) { Coord2d d=e.sub(s); double l=d.x*d.x+d.y*d.y; if(l<1e-9)return s; double t=Math.max(0,Math.min(1,((p.x-s.x)*d.x+(p.y-s.y)*d.y)/l)); return s.add(d.mul(t)); }
        private static double pointSegment(Coord2d p, Coord2d s, Coord2d e) { return p.dist(closest(p,s,e)); }
        private static boolean crosses(Coord2d p, Coord2d q, Coord2d s, Coord2d e) { double a=cross(s,e,p),b=cross(s,e,q),c=cross(p,q,s),d=cross(p,q,e); return ((a>0)!=(b>0))&&((c>0)!=(d>0)); }
        private static double cross(Coord2d o, Coord2d u, Coord2d v) { return (u.x-o.x)*(v.y-o.y)-(u.y-o.y)*(v.x-o.x); }
    }



    public enum Mode
    {
        NEAREST,
        Y_MAX,
        Y_MIN,
        X_MAX,
        X_MIN,
    }

    public PathFinder(Coord2d begin, Coord2d end) {
        this.begin = begin;
        this.end = end;
    }

    public PathFinder(Coord2d begin, Gob target) {
        this(target);
        this.begin = begin;
    }

    public PathFinder(Coord2d end) {
        this(NUtils.getGameUI().map.player().rc, end);
    }

    public PathFinder(Gob target) {
        this(target.rc);
        target_id = target.id;
        Gob targetg;
        if((targetg = Finder.findGob(target_id))!=null)
        {
            if(NParser.checkName(targetg.ngob.name,new NAlias("pow")))
            {
                badDir = targetg.a;
            }
        }
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public PathFinder withAdditionalObstacle(Gob obstacle) {
        if (obstacle != null) {
            additionalObstacles.add(obstacle);
        }
        return this;
    }

    public PathFinder withAlexandrPileBehavior() {
        alexandrPileBehavior = true;
        return this;
    }

    /** Adds an opt-in caller cancellation hook without changing ordinary pathfinding. */
    PathFinder withAbort(BooleanSupplier abort) {
        this.callerAbort = abort;
        return this;
    }

    boolean abortedByCaller() {
        return abortedByCaller;
    }

    static Gob resolveObstacle(long id, Gob targetDummy, List<Gob> extraObstacles,
                               LongFunction<Gob> liveLookup) {
        if (targetDummy != null && targetDummy.id == id) {
            return targetDummy;
        }
        for (Gob obstacle : extraObstacles) {
            if (obstacle != null && obstacle.id == id) {
                return obstacle;
            }
        }
        return liveLookup.apply(id);
    }

    private Gob resolveObstacle(long id) {
        return resolveObstacle(id, dummy, additionalObstacles, Finder::findGob);
    }

    /* ---------------------------------------------------------------------------------------
     * Extension points. Every one of these is a no-op here, chosen so that the base class keeps
     * exactly the behaviour it had before they existed. They exist so a specialised pathfinder
     * -- CartPathFinder is the only one today -- can adjust the search without this class, NPFMap,
     * Graph or GoTo having to know anything about it.
     * --------------------------------------------------------------------------------------- */

    /**
     * Called once the grid is built and the start and approach cells have been resolved, before
     * the search runs. Somewhere to adjust the map for an agent that is not a bare character.
     */
    protected void onMapReady(NPFMap map, Coord start) {
    }

    /** How a single leg of the computed path is walked. */
    protected Results walkTo(NGameUI gui, Coord2d target) throws InterruptedException {
        return new GoTo(target).run(gui);
    }

    /** Abort-aware walking remains opt-in, leaving subclass walkTo hooks untouched for normal paths. */
    protected Results walkTo(NGameUI gui, Coord2d target, BooleanSupplier abort) throws InterruptedException {
        if (abort == null) return walkTo(gui, target);
        GoTo go = new GoTo(target, abort);
        Results result = go.run(gui);
        legZoneAborted = go.aborted();
        return result;
    }

    /**
     * A leg failed and the path is about to be replanned from {@code at}. Return false to abandon
     * the route instead. The base class always retries, which is what it has always done.
     */
    protected boolean onLegFailed(NGameUI gui, Coord2d at) throws InterruptedException {
        return true;
    }

    static boolean shouldAdjustApproachWaypoint(boolean finalWaypoint, boolean hardMode, boolean hasDummy) {
        return finalWaypoint && (hardMode || hasDummy);
    }

    static boolean shouldAdjustApproachWaypoint(boolean finalWaypoint, boolean hardMode,
                                                boolean hasDummy, boolean alexandrPileBehavior) {
        return alexandrPileBehavior
                ? (finalWaypoint && hardMode) || hasDummy
                : shouldAdjustApproachWaypoint(finalWaypoint, hardMode, hasDummy);
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        blockedByAvoidZones = false;
        abortedByCaller = false;
        zoneReplanTimes.clear();
        stalls = 0;
        while (true) {
            if (callerAbortRequested()) return abortWalk(gui);
            LinkedList<Graph.Vertex> path = construct();

            if (path != null) {
                boolean needRestart = false;
                List<Coord2d> corners = new ArrayList<>();
                for(Graph.Vertex v : path) corners.add(Utils.pfGridToWorld(v.pos));
                int step = 0;
//                NUtils.getGameUI().msg(Utils.pfGridToWorld(path.getLast().pos).toString());
                //TODO syntetic points
                for (Graph.Vertex vert : path) {
                    Coord2d targetCoord = Utils.pfGridToWorld(vert.pos);

                    if(shouldAdjustApproachWaypoint(vert == path.getLast(), isHardMode,
                            dummy != null, alexandrPileBehavior)) {
                        Coord2d tcord = dummy != null ? dummy.rc : Finder.findGob(target_id).rc;
                        if (Math.abs(targetCoord.x - tcord.x) < 4) {
                            targetCoord.x = tcord.x;
                            targetCoord.y += tcord.y - targetCoord.y > 0 ? -2 : 2;
                        }
                        if (Math.abs(targetCoord.y - tcord.y) < 4) {
                            targetCoord.y = tcord.y;
                            targetCoord.x += tcord.x - targetCoord.x > 0 ? -2 : 2;
                        }
                    }

                    List<Coord2d> rest = corners.subList(Math.min(step++, corners.size()), corners.size());
                    AtomicBoolean callerAbortSeen = new AtomicBoolean(false);
                    BooleanSupplier callerLegAbort = callerAbort == null ? null : () -> {
                        if (!callerAbort.getAsBoolean()) return false;
                        callerAbortSeen.set(true);
                        return true;
                    };
                    BooleanSupplier abort = combineAbort(callerLegAbort,
                            avoidZones == null ? null : zoneAbort(gui, rest));
                    legZoneAborted = false;
                    Results walked = walkTo(gui, targetCoord, abort);
                    if (!walked.IsSuccess()) {
                        if (callerAbortSeen.get() || callerAbortRequested()) return abortWalk(gui);
                        if (legZoneAborted && zoneReplanStorm())
                            return zoneBlocked(gui, "Dangerous animals keep crossing the path");
                        Coord2d at = gui.map.player().rc;
                        if (shouldLearnStall(legZoneAborted, learnedBlocks) && noteStall(gui, targetCoord))
                            return Results.ERROR("Stuck: gave up after " + stalls + " stalls");
                        if (!onLegFailed(gui, at))
                            return Results.ERROR("Can't walk path");
                        this.begin = at;
                        needRestart = true;
                        break;
                    }
                }
                if (!needRestart)
                    return Results.SUCCESS();
            } else {
                if (dn) {
//                    if(start_pos == end_poses.get(0) && NUtils.player().rc.dist(Utils.pfGridToWorld(pfmap.cells[start_pos]))
                    return Results.SUCCESS();
                }
                if (!plannedZones.isEmpty() && pathExistsWithoutZones())
                    return zoneBlocked(gui, "No safe path around dangerous animals");
                if (waterMode && pfmap != null && start_pos != null && end_pos != null) {
                    NPFMap.Cell[][] cells = pfmap.getCells();
                    StringBuilder msg = new StringBuilder("Forager debug: water-mode path failed - size=" + pfmap.size + " lastMul=" + pfmap.lastMul + " ");
                    if (start_pos.x >= 0 && start_pos.x < pfmap.size && start_pos.y >= 0 && start_pos.y < pfmap.size) {
                        msg.append("start val=").append(cells[start_pos.x][start_pos.y].val)
                           .append(" content=").append(cells[start_pos.x][start_pos.y].content).append(" ");
                    } else {
                        msg.append("start OOB(").append(start_pos).append(") ");
                    }
                    if (end_pos.x >= 0 && end_pos.x < pfmap.size && end_pos.y >= 0 && end_pos.y < pfmap.size) {
                        msg.append("end val=").append(cells[end_pos.x][end_pos.y].val)
                           .append(" content=").append(cells[end_pos.x][end_pos.y].content);
                    } else {
                        msg.append("end OOB(").append(end_pos).append(")");
                    }
                    if (NUtils.getGameUI() != null) {
                        NUtils.getGameUI().msg(msg.toString());
                    }
                }
                return
                        Results.ERROR("Can't find path");

            }
        }
    }

    public LinkedList<Graph.Vertex> construct() throws InterruptedException {
        return construct(false);
    }

    private BooleanSupplier zoneAbort(NGameUI gui, List<Coord2d> rest) {
        final long[] next = {0};
        return () -> {
            long now=System.currentTimeMillis();
            if(now<next[0] || now-lastZoneReplanMs<ZONE_REPLAN_MIN_MS) return false;
            next[0]=now+ZONE_CHECK_MS;
            Gob player=gui.map.player(); if(player==null) return false;
            List<AvoidZone> zones=clearOf(avoidZones.get(), player.rc);
            Coord2d from=player.rc;
            for(Coord2d to:rest) { for(AvoidZone z:zones) if(z.r-z.dist(from,to)>ZONE_ABORT_SLACK) return true; from=to; }
            return false;
        };
    }

    private boolean zoneReplanStorm() {
        long now=System.currentTimeMillis(); lastZoneReplanMs=now;
        return noteZoneReplan(zoneReplanTimes, now);
    }

    static boolean noteZoneReplan(ArrayDeque<Long> replanTimes, long now) {
        replanTimes.addLast(now);
        while(!replanTimes.isEmpty() && now-replanTimes.peekFirst()>ZONE_STORM_WINDOW_MS) replanTimes.removeFirst();
        return replanTimes.size()>ZONE_STORM_MAX;
    }

    static boolean shouldLearnStall(boolean legZoneAborted, List<Coord2d> learnedBlocks) {
        return !legZoneAborted && learnedBlocks != null;
    }

    private Results zoneBlocked(NGameUI gui, String why) {
        blockedByAvoidZones=true; stopHere(gui); return Results.ERROR(why);
    }

    private boolean pathExistsWithoutZones() throws InterruptedException {
        ignoreZones=true;
        try { pfmap=null; dn=false; return construct(true)!=null || dn; }
        finally { ignoreZones=false; }
    }

    private static List<AvoidZone> clearOf(List<AvoidZone> zones, Coord2d from) {
        List<AvoidZone> out=new ArrayList<>();
        for(AvoidZone z:zones) { double d=z.dist(from); if(d>=z.r) out.add(z); else if(d>ZONE_START_CLEARANCE) out.add(new AvoidZone(z.a,z.b,d-ZONE_START_CLEARANCE,z.label)); }
        return out;
    }

    private boolean noteStall(NGameUI gui, Coord2d target) {
        Gob p=gui.map.player(); if(p==null)return false;
        double left=p.rc.dist(target); Coord2d spot=left>.01?p.rc.add(target.sub(p.rc).norm(Math.min(STALL_BLOCK_AHEAD,left))):p.rc;
        learnedBlocks.add(spot); return ++stalls>=maxStalls;
    }

    private Results abortWalk(NGameUI gui) { abortedByCaller=true; stopHere(gui); return Results.FAIL(); }
    private boolean callerAbortRequested() { return callerAbort != null && callerAbort.getAsBoolean(); }
    static BooleanSupplier combineAbort(BooleanSupplier a, BooleanSupplier b) {
        if (a == null) return b;
        if (b == null) return a;
        return () -> a.getAsBoolean() || b.getAsBoolean();
    }
    static void stopHere(NGameUI gui) {
        if (gui == null || gui.map == null) return;
        Gob player = gui.map.player();
        if (player != null) gui.map.wdgmsg("click", Coord.z, player.rc.floor(OCache.posres), 1, 0);
    }

    private void blockAvoided() {
        boolean zones=!plannedZones.isEmpty(), learned=learnedBlocks!=null&&!learnedBlocks.isEmpty();
        if(!zones&&!learned) return;
        NPFMap.Cell[][] cells=pfmap.getCells();
        for(int i=0;i<pfmap.size;i++) for(int j=0;j<pfmap.size;j++) {
            NPFMap.Cell cell=cells[i][j]; if(cell.val!=0) continue;
            Coord2d p=Utils.pfGridToWorld(cell.pos);
            boolean blocked=zones&&AvoidZone.anyContains(plannedZones,p);
            if(!blocked&&learned) for(Coord2d spot:learnedBlocks) if(p.dist(spot)<STALL_BLOCK_R){blocked=true;break;}
            if(blocked) cell.val=1;
        }
    }

    boolean startWasBlocked() {
        return startWasBlocked;
    }

    static Coord nearestFreeCell(Coord start, int width, int height, Predicate<Coord> isFree) {
        if (start == null || start.x < 0 || start.y < 0 || start.x >= width || start.y >= height) {
            return null;
        }

        Coord[] directions = {
                Coord.of(1, 0), Coord.of(0, 1), Coord.of(-1, 0), Coord.of(0, -1),
                Coord.of(1, 1), Coord.of(-1, 1), Coord.of(-1, -1), Coord.of(1, -1)
        };
        boolean[][] visited = new boolean[width][height];
        ArrayDeque<Coord> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start.x][start.y] = true;

        while (!queue.isEmpty()) {
            Coord current = queue.removeFirst();
            if (isFree.test(current)) {
                return current;
            }
            for (Coord direction : directions) {
                Coord next = current.add(direction);
                if (next.x < 0 || next.y < 0 || next.x >= width || next.y >= height ||
                        visited[next.x][next.y]) {
                    continue;
                }
                visited[next.x][next.y] = true;
                queue.addLast(next);
            }
        }
        return null;
    }

    static Coord selectBlockedStart(List<Coord> adjacent, Coord remoteFallback,
                                    boolean alexandrPileBehavior) {
        if (adjacent != null && !adjacent.isEmpty()) {
            return adjacent.get(0);
        }
        return alexandrPileBehavior ? null : remoteFallback;
    }

    public LinkedList<Graph.Vertex> construct(boolean test) throws InterruptedException {
        LinkedList<Graph.Vertex> path = new LinkedList<>();
        startWasBlocked = false;
        gobInStartPos = null;
        plannedZones = (avoidZones != null && !ignoreZones) ? clearOf(avoidZones.get(), begin) : Collections.emptyList();
        int mul = 1;
        while (path.isEmpty() && mul < maxMul) {
            if(pfmap!=null && pfmap.lastMul)
                return null;
            pfmap = new NPFMap(begin, end, mul);
            pfmap.getBegin();
            pfmap.getEnd();
            if(pfmap.bad) {
                if (test || !plannedZones.isEmpty()) {
                    return null;
                } else {
                    NUtils.getGameUI().error("Unable to build grid of required size");
                    throw new InterruptedException();
                }
            }
            pfmap.waterMode = waterMode;
            pfmap.gatesAlwaysClosed = gatesAlwaysClosed;
            pfmap.build();
            blockAvoided();
            for (Gob obstacle : additionalObstacles) {
                pfmap.addGob(obstacle);
            }
            CellsArray dca = null;
            if (dummy != null)
                dca = pfmap.addGob(dummy);

            start_pos = Utils.toPfGrid(begin).sub(pfmap.getBegin());
            end_pos = Utils.toPfGrid(end).sub(pfmap.getBegin());
            if (!plannedZones.isEmpty() && AvoidZone.anyContains(plannedZones, end)) {
                return null;
            }
            // Находим свободные начальные и конечные точки

            if (!fixStartEnd(test)) {
//                dn = true; //start == end
                return null;
            }

            onMapReady(pfmap, start_pos);

            if (dca != null)
                pfmap.setCellArray(dca);
            if(!test)
                NPFMap.print(pfmap.getSize(), pfmap.getCells());


            Graph res = null;
            if (pfmap.getCells()[end_pos.x][end_pos.y].val == 7) {
                Thread th = new Thread(res = new Graph(pfmap, start_pos, end_pos));
                th.start();
                th.join();
            } else {
                switch (mode) {
                    case NEAREST:
                    {
                        LinkedList<Graph> graphs = new LinkedList<>();
                        for (Coord ep : end_poses) {
                            graphs.add(new Graph(pfmap, start_pos, ep));
                        }
                        LinkedList<Thread> threads = new LinkedList<>();
                        for (Graph graph : graphs) {
                            Thread th;
                            threads.add(th = new Thread(graph));
                            th.start();
                        }
                        for (Thread t : threads) {
                            t.join();
                        }

                        graphs.sort(new Comparator<Graph>() {
                            @Override
                            public int compare(Graph o1, Graph o2) {
                                return (Integer.compare(o1.getPathLen(), o2.getPathLen()));
                            }
                        });
                        if (!graphs.isEmpty())
                            res = graphs.get(0);
                        break;
                    }
                    case Y_MAX:
                    case Y_MIN:
                    case X_MAX:
                    case X_MIN:
                    {

                        Comparator comp = new Comparator<Coord>() {
                            @Override
                            public int compare(Coord o1, Coord o2) {
                                Coord2d t01 = Utils.pfGridToWorld(pfmap.cells[o1.x][o1.y].pos);
                                Coord2d t02 = Utils.pfGridToWorld(pfmap.cells[o2.x][o2.y].pos);
                                switch (mode)
                                {
                                    case Y_MAX:
                                        return Double.compare(t02.y, t01.y);
                                    case Y_MIN:
                                        return Double.compare(t01.y, t02.y);
                                    case X_MAX:
                                        return Double.compare(t02.x, t01.x);
                                    case X_MIN:
                                        return Double.compare(t01.x, t02.x);
                                }
                                return 0;
                            }
                        };

                        end_poses.sort(comp);
                        Thread th = new Thread(res = new Graph(pfmap, start_pos, end_poses.get(0)));
                        th.start();
                        th.join();
                    }
                }
            }
//                for (Graph g: graphs)
//                {
//                    NPFMap.print(pfmap.getSize(), g.getVert());
//                }


            if (res != null) {
                if (!isDynamic)
                    path = getPath(pfmap, res.path);
                else
                    path = res.path;
//                NPFMap.print(pfmap.getSize(), res.getVert());
                if (!path.isEmpty()) {
                    return path;
                }
            }
            mul++;
        }
        return null;
    }

    private boolean fixStartEnd(boolean test) {
        NPFMap.Cell[][] cells = pfmap.getCells();
        if(start_pos.x < pfmap.size && start_pos.y<pfmap.size && start_pos.x>=0 && start_pos.y>=0) {
            if (cells[start_pos.x][start_pos.y].val != 0) {
                if (target_id >= 0 && cells[start_pos.x][start_pos.y].content.contains(target_id) && !test && !skipDN) {
                    dn = true;
                    return false;
                }
                ArrayList<Coord> st_poses = findFreeNear(start_pos, true);
                if (st_poses.isEmpty()) {
                    Coord remoteFallback = nearestFreeCell(start_pos, pfmap.size, pfmap.size,
                            cell -> cells[cell.x][cell.y].val == 0);
                    Coord freeStart = selectBlockedStart(
                            st_poses, remoteFallback, alexandrPileBehavior);
                    if (freeStart == null)
                        return false;
                    st_poses.add(freeStart);
                }
                startWasBlocked = true;
                if (cells[start_pos.x][start_pos.y].content.size() == 1 || (cells[start_pos.x][start_pos.y].content.size() == 2 && cells[start_pos.x][start_pos.y].content.contains((long) -1)))
                    for (Long id : cells[start_pos.x][start_pos.y].content) {
                        if (id != -1) {
                            gobInStartPos = Finder.findGob(id);
                        }
                    }
                start_pos = st_poses.get(0);
            }
            if (start_pos.equals(end_pos) && dummy == null && !skipDN) {
                dn = true;
                return false;
            }
            if (end_pos.x < pfmap.size && end_pos.y < pfmap.size && end_pos.x >= 0 && end_pos.y >= 0) {
                if (cells[end_pos.x][end_pos.y].val != 0) {
                    end_poses = findFreeNear(end_pos, false);
                    if(!hasEndCandidates(end_poses))
                        return false;
                    if (dummy != null || (isHardMode && target_id != -2 && Finder.findGob(target_id) != null)) {
                        Coord2d tcoord = (dummy != null) ? dummy.rc : Finder.findGob(target_id).rc;
                        ArrayList<Coord> best_poses = new ArrayList<>();
                        for (Coord coord : end_poses) {
                            Coord2d coord2d = Utils.pfGridToWorld(cells[coord.x][coord.y].pos);
                            if (coord2d.x + MCache.tileqsz.x > tcoord.x && coord2d.x - MCache.tileqsz.x < tcoord.x ||
                                    coord2d.y + MCache.tileqsz.y > tcoord.y && coord2d.y - MCache.tileqsz.y < tcoord.y)
                                best_poses.add(coord);
                        }
                        if (!best_poses.isEmpty())
                            end_poses = best_poses;
                    }

                    if (badDir != Double.MAX_VALUE && target_id > 0) {
                        Coord2d orientation = new Coord2d(1, 0).rot(badDir);
                        Coord2d tcoord = Finder.findGob(target_id).rc;
                        ArrayList<Coord> best_poses = new ArrayList<>();
                        for (Coord coord : end_poses) {
                            Coord2d coord2d = Utils.pfGridToWorld(cells[coord.x][coord.y].pos).sub(tcoord).norm();
                            if (coord2d.dot(orientation) >= -0.2)
                                best_poses.add(coord);
                            else
                                cells[coord.x][coord.y].val = 0;
                        }
                        end_poses = best_poses;
                    }
                    for (Coord coord : end_poses) {
                        if (start_pos.equals(coord) && dummy == null && !skipDN) {
                            dn = true;
                            return false;
                        }
                        cells[coord.x][coord.y].val = 7;
                    }

                } else {
                    cells[end_pos.x][end_pos.y].val = 7;
                }
            } else {
                return false;
            }
            return true;
        }
        return false;
    }

    static boolean hasEndCandidates(Collection<Coord> candidates) {
        return candidates != null;
    }

    private ArrayList<Coord> findFreeNear(Coord pos, boolean start) {
        if (!start) {
            if (target_id != -2) {
                Gob target = dummy;
                if (target == null) {
                    target = Finder.findGob(target_id);
                }
                if(target == null)
                    return null;
                CellsArray ca = target.ngob.getCA();
                ArrayList<Coord> res = findFreeNearByHB(ca, target_id, dummy, start);
                if (res == null || res.isEmpty()) {
                    // Target has no collision box of its own (e.g. a pick-gob like a mushroom
                    // rendered attached to a tree) - findFreeNearByHB only ever searches around a
                    // real hitbox, so with none it always comes back empty even though free tiles
                    // exist nearby (just outside whatever else - the tree - is actually blocking
                    // this cell). Fall back to scanning directly around the target's own position.
                    res = findFreeNearByPos(pos);
                }
                return res;
            }
        } else {
            if (pfmap.cells[pos.x][pos.y].val!=0 && pfmap.cells[pos.x][pos.y].val!=7) {
                ArrayList<Coord> targets = null;
                if(pfmap.cells[pos.x][pos.y].content.contains((long)-1)) {
                    Gob obstacle = resolveObstacle(-1);
                    if (obstacle == null) {
                        return new ArrayList<>();
                    }
                    CellsArray ca = obstacle.ngob.getCA();
                    return findFreeNearByHB(ca, target_id, dummy, start);
                }
                else {
                    for (Long cand : pfmap.cells[pos.x][pos.y].content) {
                        Gob obstacle = resolveObstacle(cand);
                        if (obstacle == null) {
                            continue;
                        }
                        CellsArray ca = obstacle.ngob.getCA();
                        ArrayList<Coord> cords = findFreeNearByHB(ca, cand, dummy, start);
                        if (targets == null) {
                            targets = cords;
                        } else {
                            ArrayList<Coord> forRemove = new ArrayList<>();
                            for (Coord cord1 : targets) {
                                boolean found = false;
                                for (Coord coord2 : cords) {
                                    if (cord1.equals(coord2.x, coord2.y)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found)
                                    forRemove.add(cord1);
                            }
                            targets.removeAll(forRemove);
                        }
                    }
                }
                return targets == null ? new ArrayList<>():targets;
            }
        }
        return new ArrayList<>(Arrays.asList(pos));
    }

    private void checkAndAdd(Coord pos, ArrayList<Coord> coords, AtomicBoolean check) {
        //debug method
        if (pfmap.getCells()[pos.x][pos.y].val == 0) {
            pfmap.getCells()[pos.x][pos.y].val = 7;
            coords.add(pos);
        } else if (target_id != -2 && check != null) {
            if (!pfmap.getCells()[pos.x][pos.y].content.contains(target_id))
                check.set(false);
        }
    }

    public static ArrayList<Coord2d> getNearestHardPoints(Gob target) throws InterruptedException  {
        PathFinder pf = new PathFinder(target);
        pf.isHardMode = true;
        pf.construct(true);
        ArrayList<Coord2d> res = new ArrayList<>();
        for(Coord ep : pf.end_poses)
        {
            Coord2d coord2d = Utils.pfGridToWorld(pf.pfmap.cells[ep.x][ep.y].pos);
            if(Math.abs(coord2d.x-target.rc.x)>Math.abs(coord2d.y-target.rc.y))
            {
                coord2d.y = target.rc.y;
            }
            else
            {
                coord2d.x = target.rc.x;
            }
            res.add(coord2d);
        }
        return res;
    }

    public static boolean isAvailable(Gob target) throws InterruptedException {
        if(NUtils.player() == null || target == null)
            return false;
        PathFinder pf = new PathFinder(target);
        LinkedList<Graph.Vertex> res = pf.construct(true);
        return res != null || pf.dn;
    }

    public static boolean isAvailableForAlexandrPile(Gob target) throws InterruptedException {
        if(NUtils.player() == null || target == null)
            return false;
        PathFinder pf = new PathFinder(target).withAlexandrPileBehavior();
        LinkedList<Graph.Vertex> res = pf.construct(true);
        return res != null || pf.dn;
    }

    public static boolean isAvailable(Coord2d target) throws InterruptedException {
        if(NUtils.player() == null)
            return false;
        PathFinder pf = new PathFinder(target);
        LinkedList<Graph.Vertex> res = pf.construct(true);
        return res != null || pf.dn;
    }

    public static boolean isAvailable(Gob target, boolean hardMode) throws InterruptedException {
        if(NUtils.player() == null || target == null)
            return false;
        PathFinder pf = new PathFinder(target);
        pf.isHardMode = true;
        return pf.construct(true) != null;
    }

    public static boolean isAvailable(Coord2d begin, Coord2d target, boolean gatesAlwaysClosed) throws InterruptedException {
        if(NUtils.player() == null)
            return false;
        PathFinder pf = new PathFinder(begin, target);
        pf.gatesAlwaysClosed = gatesAlwaysClosed;
        LinkedList<Graph.Vertex> res = pf.construct(true);
        return res != null || pf.dn;
    }

    public static boolean isAvailableWithObstacle(Coord2d begin, Coord2d target, Gob obstacle)
            throws InterruptedException {
        if (NUtils.player() == null || obstacle == null) {
            return false;
        }
        PathFinder pf = new PathFinder(begin, target).withAdditionalObstacle(obstacle);
        LinkedList<Graph.Vertex> res = pf.construct(true);
        return res != null || pf.dn;
    }

    public static boolean isAvailable(Coord2d begin, Gob target, boolean gatesAlwaysClosed) throws InterruptedException {
        if(NUtils.player() == null)
            return false;
        PathFinder pf = new PathFinder(begin, target);
        pf.gatesAlwaysClosed = gatesAlwaysClosed;
        LinkedList<Graph.Vertex> res = pf.construct(true);
        return res != null || pf.dn;
    }

    /**
     * Get the cost (length) of path to the target coordinate.
     * @param target target coordinate
     * @return path cost, or -1 if unreachable
     */
    public static int getPathCost(Coord2d target) throws InterruptedException {
        if(NUtils.player() == null)
            return -1;
        PathFinder pf = new PathFinder(target);
        LinkedList<Graph.Vertex> res = pf.construct(true);
        if (res != null) {
            return res.size();
        }
        return pf.dn ? 0 : -1;
    }

    public PathFinder(Gob dummy, boolean virtual) {
        this(dummy);
        this.dummy = dummy;
        assert virtual;
    }

    public PathFinder(Coord2d begin, Gob dummy, boolean virtual) {
        this(dummy, virtual);
        this.begin = begin;
    }


    private ArrayList<Coord> findFreeNearByHB(CellsArray ca, long target_id, Gob dummy, boolean isStart) {
        ArrayList<Coord> res = new ArrayList<>();
        if (ca != null) {
            for (int i = 0; i < ca.x_len; i++)
                for (int j = 0; j < ca.y_len; j++) {
                    int ii = i + ca.begin.x - pfmap.begin.x;
                    int jj = j + ca.begin.y - pfmap.begin.y;
                    Coord npfpos = new Coord(ii, jj);
                    if (ii > 0 && ii < pfmap.size && jj > 0 && jj < pfmap.size) {
                        if (ca.cells[i][j] != 0) {
                            for (int d = 0; d < 4; d++) {
                                Coord test_coord = npfpos.add(Coord.uecw[d]);
                                if (test_coord.x < pfmap.size && test_coord.x >= 0 && test_coord.y < pfmap.size && test_coord.y >= 0)
                                    if (pfmap.cells[test_coord.x][test_coord.y].val == 0 || pfmap.cells[test_coord.x][test_coord.y].val == 7) {
                                        if (isStart || pfmap.cells[npfpos.x][npfpos.y].content.size() == 1) {
                                            pfmap.getCells()[test_coord.x][test_coord.y].val = 7;
                                            res.add(test_coord);
                                        } else if (pfmap.cells[npfpos.x][npfpos.y].content.size() > 1) {
                                            Coord2d test2d_coord = Utils.pfGridToWorld(pfmap.cells[test_coord.x][test_coord.y].pos);
                                            double dst = 9000, testdst;
                                            long res_id = -2;
                                            for (long id : pfmap.cells[npfpos.x][npfpos.y].content) {
                                                Gob obstacle = resolveObstacle(id);
                                                if (obstacle != null &&
                                                        (testdst = obstacle.rc.dist(test2d_coord)) < dst) {
                                                    res_id = id;
                                                    dst = testdst;
                                                }
                                            }
                                            if (res_id == target_id) {
                                                pfmap.getCells()[test_coord.x][test_coord.y].val = 7;
                                                res.add(test_coord);
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
        }

        if(isStart) {
            Coord2d player = NUtils.player().rc;
            if(Finder.findGob(target_id)!=null) {
                Coord2d targerc = Finder.findGob(target_id).rc;
                Coord2d playerdir = player.sub(targerc).norm();


                Comparator comp = new Comparator<Coord>() {
                    @Override
                    public int compare(Coord o1, Coord o2) {
                        Coord2d t01 = Utils.pfGridToWorld(pfmap.cells[o1.x][o1.y].pos).sub(targerc).norm();
                        Coord2d t02 = Utils.pfGridToWorld(pfmap.cells[o2.x][o2.y].pos).sub(targerc).norm();

                        return Double.compare(t02.dot(playerdir), t01.dot(playerdir));
                    }
                };

                res.sort(comp);
            }
//            Gob target = (dummy==null|| target_id!=-1)?Finder.findGob(target_id):dummy;
//            System.out.println("+++++++++++++++++++++++");
//            System.out.println("Target" + ((target!=dummy)?target.ngob.name:"") + "rc" + target.rc.toString() + " id " + target.id);
//            System.out.println("targetrc " + targerc);
//            System.out.println("Player" + " rc " + player.toString());
//            for(Coord coord: res)
//            {
//                Coord2d pos = Utils.pfGridToWorld(pfmap.cells[coord.x][coord.y].pos);
//                System.out.println(pos.toString() + "|" + " cos " + pos.sub(targerc).norm().dot(playerdir));
//            }
        }

        return res;
    }

    /** Expanding-ring scan for the nearest free pfmap cell around pos, used as a fallback when the
     *  target has no collision box of its own for findFreeNearByHB to search around. */
    private ArrayList<Coord> findFreeNearByPos(Coord pos) {
        ArrayList<Coord> res = new ArrayList<>();
        for (int radius = 1; radius <= 20 && res.isEmpty(); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    Coord test = pos.add(dx, dy);
                    if (test.x >= 0 && test.x < pfmap.size && test.y >= 0 && test.y < pfmap.size) {
                        if (pfmap.cells[test.x][test.y].val == 0) {
                            pfmap.getCells()[test.x][test.y].val = 7;
                            res.add(test);
                        }
                    }
                }
            }
        }
        return res;
    }

    boolean getDNStatus() {
        return dn;
    }

    /**
     * Finds common approach points for two objects (e.g., workstation and barrel).
     * Returns approach points that are adjacent to BOTH objects.
     * 
     * @param gob1 First object (e.g., workstation)
     * @param gob2 Second object (e.g., barrel)
     * @return ArrayList of common approach points, or empty list if none found
     */
    public static ArrayList<Coord2d> findCommonApproachPoints(Gob gob1, Gob gob2) throws InterruptedException {
        ArrayList<Coord2d> result = new ArrayList<>();
        
        if (gob1 == null || gob2 == null) {
            NUtils.getGameUI().msg("findCommonApproachPoints: gob1 or gob2 is null");
            return result;
        }
        
        // Get approach points for both objects
        ArrayList<Coord2d> points1 = getNearestHardPoints(gob1);
        ArrayList<Coord2d> points2 = getNearestHardPoints(gob2);
        
        NUtils.getGameUI().msg("findCommonApproachPoints: gob1 has " + 
                (points1 != null ? points1.size() : 0) + " approach points, gob2 has " + 
                (points2 != null ? points2.size() : 0) + " approach points");
        
        if (points1 == null || points1.isEmpty() || points2 == null || points2.isEmpty()) {
            NUtils.getGameUI().msg("findCommonApproachPoints: One of the objects has no approach points");
            return result;
        }
        
        // Find common points (within tolerance)
        // Tolerance of 14 units to cover typical diagonal placement distances (~12-13 units)
        double tolerance = 14.0;
        for (Coord2d p1 : points1) {
            for (Coord2d p2 : points2) {
                double dist = p1.dist(p2);
                if (dist < tolerance) {
                    // Use the midpoint as the common point
                    Coord2d midpoint = new Coord2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
                    result.add(midpoint);
                    NUtils.getGameUI().msg("findCommonApproachPoints: Found common point at " + midpoint + 
                            " (p1=" + p1 + ", p2=" + p2 + ", dist=" + String.format("%.2f", dist) + ")");
                }
            }
        }
        
        if (result.isEmpty()) {
            NUtils.getGameUI().msg("findCommonApproachPoints: No common points found within tolerance " + tolerance);
            // Log some distances for debugging
            if (!points1.isEmpty() && !points2.isEmpty()) {
                double minDist = Double.MAX_VALUE;
                for (Coord2d p1 : points1) {
                    for (Coord2d p2 : points2) {
                        minDist = Math.min(minDist, p1.dist(p2));
                    }
                }
                NUtils.getGameUI().msg("findCommonApproachPoints: Minimum distance between approach points: " + 
                        String.format("%.2f", minDist));
            }
        }
        
        // Sort by distance to player
        Coord2d playerPos = NUtils.player().rc;
        result.sort((a, b) -> Double.compare(a.dist(playerPos), b.dist(playerPos)));
        
        return result;
    }
    
    /**
     * Finds the nearest common approach point for two objects.
     * This is useful when character needs to interact with both objects (e.g., crafting with barrel at workstation).
     * Applies hardMode alignment rule: aligns one coordinate (X or Y) with the workstation.
     * 
     * @param gob1 First object (workstation)
     * @param gob2 Second object (barrel)
     * @return Nearest common approach point with hardMode alignment applied, or null if none found
     */
    public static Coord2d findNearestCommonApproachPoint(Gob gob1, Gob gob2) throws InterruptedException {
        ArrayList<Coord2d> commonPoints = findCommonApproachPoints(gob1, gob2);
        if (commonPoints.isEmpty()) {
            return null;
        }
        
        // Use gob1 (workstation) as the reference for hardMode alignment
        Coord2d wsCoord = gob1.rc;
        
        for (Coord2d point : commonPoints) {
            // Apply hardMode rule: align one coordinate with workstation
            Coord2d alignedPoint = applyHardModeAlignment(point, wsCoord);
            
            NUtils.getGameUI().msg("findNearestCommonApproachPoint: Point " + point + 
                    " aligned to " + alignedPoint + " (hardMode rule applied)");
            
            return alignedPoint;
        }
        
        NUtils.getGameUI().msg("findNearestCommonApproachPoint: No common points found");
        return null;
    }
    
    /**
     * Apply hardMode alignment rule: replace one coordinate (X or Y) with the target's coordinate.
     * Replaces whichever coordinate is closer to the target.
     */
    private static Coord2d applyHardModeAlignment(Coord2d point, Coord2d targetCoord) {
        Coord2d result = new Coord2d(point.x, point.y);
        
        // Check which coordinate is closer to target - replace that one
        if (Math.abs(result.x - targetCoord.x) < Math.abs(result.y - targetCoord.y)) {
            // X is closer - replace X with target's X
            result.x = targetCoord.x;
        } else {
            // Y is closer - replace Y with target's Y
            result.y = targetCoord.y;
        }
        
        return result;
    }

//    boolean gridIsBiggerThanVisibleArea(NPFMap map) {
//            Utils.pfGridToWorld(map.getBegin())
//
//
//            int size = VISIBLE_AREA;
//            Coord2d a = new Coord2d(Math.min(begin.x, end.x), Math.min(begin.y, end.y));
//            Coord2d b = new Coord2d(Math.max(begin.x, end.x), Math.max(begin.y, end.y));
//            int dsize = Math.min(size, Math.max(8,((int) Math.ceil(b.dist(a) / MCache.tilehsz.x)) * mul));
//            return dsize == VISIBLE_AREA;
//    }
}
