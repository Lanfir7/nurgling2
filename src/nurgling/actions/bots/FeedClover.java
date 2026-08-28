package nurgling.actions.bots;

import haven.Composite;
import haven.Coord2d;
import haven.Drawable;
import haven.Gob;
import haven.MCache;
import haven.WItem;
import haven.res.ui.tt.leashed.Leashed;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NHitBox;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.DynamicPf;
import nurgling.actions.Results;
import nurgling.overlays.NCustomResult;
import nurgling.pf.NHitBoxD;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitPoseOrMsg;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;
import java.util.Arrays;

import static nurgling.NUtils.getGameUI;

public class FeedClover implements Action {
    static ArrayList<Long> feeded = new ArrayList<>();
    NAlias krtters =new NAlias(new ArrayList<String>(Arrays.asList("horse", "cattle", "boar", "goat", "sheep")), new ArrayList<String>(Arrays.asList("stallion", "mare")));
    NAlias clover = new NAlias("Clover");
    static final NAlias WALK_POSES = new NAlias("borka/walking", "borka/running", "borka/wading");
    static final int CLOSE_IN_STEPS = 30;

    private final Gob targetAnimal;

    public FeedClover() {
        this(null);
    }

    public FeedClover(Gob targetAnimal) {
        this.targetAnimal = targetAnimal;
    }

    public static boolean isWildHorse(String name) {
        return name != null && name.contains("kritter/horse/horse");
    }

    public static boolean hitboxesTouch(NHitBox player, Coord2d playerRc, double playerA,
                                        NHitBox animal, Coord2d animalRc, double animalA) {
        if (player == null || animal == null || playerRc == null || animalRc == null)
            return false;
        return new NHitBoxD(player.begin, player.end, playerRc, playerA)
                .intersects(new NHitBoxD(animal.begin, animal.end, animalRc, animalA), true);
    }

    static boolean hitboxesTouch(Gob player, Gob animal) {
        if (player == null || animal == null || player.ngob == null || animal.ngob == null)
            return false;
        return hitboxesTouch(player.ngob.hitBox, player.rc, player.a, animal.ngob.hitBox, animal.rc, animal.a);
    }

    public static boolean hitboxesWithinFeedRange(NHitBox player, Coord2d playerRc, double playerA,
                                                   NHitBox animal, Coord2d animalRc, double animalA) {
        if (player == null || animal == null || playerRc == null || animalRc == null)
            return false;
        Coord2d[] playerCorners = hitboxCorners(player, playerRc, playerA);
        Coord2d[] animalCorners = hitboxCorners(animal, animalRc, animalA);
        return rectanglesOverlap(playerCorners, animalCorners)
                || rectangleGap(playerCorners, animalCorners) <= MCache.tilehsz.x + 1e-9;
    }

    private static Coord2d[] hitboxCorners(NHitBox box, Coord2d rc, double angle) {
        double minX = Math.min(box.begin.x, box.end.x);
        double maxX = Math.max(box.begin.x, box.end.x);
        double minY = Math.min(box.begin.y, box.end.y);
        double maxY = Math.max(box.begin.y, box.end.y);
        if (minX != -maxX)
            angle += Math.PI;
        Coord2d[] local = {
                Coord2d.of(minX, minY), Coord2d.of(maxX, minY),
                Coord2d.of(maxX, maxY), Coord2d.of(minX, maxY)
        };
        Coord2d[] world = new Coord2d[local.length];
        for (int i = 0; i < local.length; i++) {
            Coord2d rotated = local[i].rot(angle);
            world[i] = Coord2d.of(rotated.x + rc.x, rotated.y + rc.y);
        }
        return world;
    }

    private static boolean rectanglesOverlap(Coord2d[] first, Coord2d[] second) {
        return !hasSeparatingAxis(first, second) && !hasSeparatingAxis(second, first);
    }

    private static boolean hasSeparatingAxis(Coord2d[] axesFrom, Coord2d[] other) {
        for (int i = 0; i < axesFrom.length; i++) {
            Coord2d start = axesFrom[i];
            Coord2d end = axesFrom[(i + 1) % axesFrom.length];
            double axisX = -(end.y - start.y);
            double axisY = end.x - start.x;
            double firstMin = Double.POSITIVE_INFINITY;
            double firstMax = Double.NEGATIVE_INFINITY;
            double secondMin = Double.POSITIVE_INFINITY;
            double secondMax = Double.NEGATIVE_INFINITY;
            for (Coord2d point : axesFrom) {
                double projection = point.x * axisX + point.y * axisY;
                firstMin = Math.min(firstMin, projection);
                firstMax = Math.max(firstMax, projection);
            }
            for (Coord2d point : other) {
                double projection = point.x * axisX + point.y * axisY;
                secondMin = Math.min(secondMin, projection);
                secondMax = Math.max(secondMax, projection);
            }
            if (firstMax < secondMin || secondMax < firstMin)
                return true;
        }
        return false;
    }

    private static double rectangleGap(Coord2d[] first, Coord2d[] second) {
        double gap = Double.POSITIVE_INFINITY;
        gap = Math.min(gap, verticesToEdgesGap(first, second));
        gap = Math.min(gap, verticesToEdgesGap(second, first));
        return gap;
    }

    private static double verticesToEdgesGap(Coord2d[] vertices, Coord2d[] edges) {
        double gap = Double.POSITIVE_INFINITY;
        for (Coord2d vertex : vertices) {
            for (int i = 0; i < edges.length; i++) {
                gap = Math.min(gap, pointToSegmentDistance(vertex, edges[i], edges[(i + 1) % edges.length]));
            }
        }
        return gap;
    }

    private static double pointToSegmentDistance(Coord2d point, Coord2d start, Coord2d end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0)
            return Math.hypot(point.x - start.x, point.y - start.y);
        double projection = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared;
        projection = Math.max(0, Math.min(1, projection));
        double closestX = start.x + projection * dx;
        double closestY = start.y + projection * dy;
        return Math.hypot(point.x - closestX, point.y - closestY);
    }

    static boolean hitboxesWithinFeedRange(Gob player, Gob animal) {
        if (player == null || animal == null || player.ngob == null || animal.ngob == null)
            return false;
        return hitboxesWithinFeedRange(player.ngob.hitBox, player.rc, player.a,
                animal.ngob.hitBox, animal.rc, animal.a);
    }

    static boolean isWalking(Gob gob) {
        if (gob == null)
            return false;
        Drawable drawable = gob.getattr(Drawable.class);
        if (!(drawable instanceof Composite))
            return false;
        String pose = ((Composite) drawable).current_pose;
        return pose != null && NParser.checkName(pose, WALK_POSES);
    }

    enum FeedContactResult {
        ACTIVATED,
        ANIMAL_GONE,
        PLAYER_GONE,
        EXHAUSTED
    }

    interface FeedContactDriver {
        boolean animalPresent() throws InterruptedException;
        boolean playerPresent();
        boolean activateIfTouching() throws InterruptedException;
        void approach() throws InterruptedException;
    }

    static FeedContactResult pursueAndActivate(FeedContactDriver driver) throws InterruptedException {
        for (int i = 0; i <= CLOSE_IN_STEPS; i++) {
            if (!driver.animalPresent())
                return FeedContactResult.ANIMAL_GONE;
            if (!driver.playerPresent())
                return FeedContactResult.PLAYER_GONE;
            if (driver.activateIfTouching())
                return FeedContactResult.ACTIVATED;
            if (i == CLOSE_IN_STEPS)
                return FeedContactResult.EXHAUSTED;
            driver.approach();
        }
        return FeedContactResult.EXHAUSTED;
    }

    static class WaitFeedContact extends NTask {
        final long animalId;
        int idle;
        boolean sawWalk;

        WaitFeedContact(long animalId) {
            this.animalId = animalId;
            this.infinite = false;
            this.maxCounter = 400;
        }

        @Override
        public boolean check() {
            Gob player = NUtils.player();
            Gob gob = Finder.findGob(animalId);
            if (player == null || gob == null)
                return true;
            if (hitboxesWithinFeedRange(player, gob))
                return true;
            if (isWalking(player)) {
                sawWalk = true;
                idle = 0;
                return false;
            }
            idle++;
            return sawWalk || idle > 20;
        }
    }

    FeedContactResult feedAtContact(final long animalId) throws InterruptedException {
        return pursueAndActivate(new FeedContactDriver() {
            @Override
            public boolean animalPresent() throws InterruptedException {
                return Finder.findGob(animalId) != null;
            }

            @Override
            public boolean playerPresent() {
                return NUtils.player() != null;
            }

            @Override
            public boolean activateIfTouching() throws InterruptedException {
                Gob player = NUtils.player();
                Gob animal = Finder.findGob(animalId);
                if (player == null || animal == null)
                    return false;
                if (!hitboxesWithinFeedRange(player, animal))
                    return false;
                NUtils.activateItem(animal, false);
                return true;
            }

            @Override
            public void approach() throws InterruptedException {
                Gob animal = Finder.findGob(animalId);
                if (animal == null)
                    return;
                NUtils.clickGob(animal);
                NUtils.getUI().core.addTask(new WaitFeedContact(animalId));
            }
        });
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        WItem item = gui.getInventory().getItem(clover);
        if(item==null)
        {
            return Results.ERROR("No clover");
        }
        Gob gob = resolveAnimal();
        if(gob==null) {
            if (targetAnimal != null)
                return Results.ERROR("Animal disappeared");
            return Results.SUCCESS();
        }

        Gob player = NUtils.player();
        if (player == null || !hitboxesWithinFeedRange(player, gob)) {
            Results walk = new DynamicPf(gob).run(gui);
            if (!walk.IsSuccess())
                return walk;
        }

        item = gui.getInventory().getItem(clover);
        if(item==null)
            return Results.ERROR("No clover");
        NUtils.takeItemToHand(item);

        long animalId = gob.id;
        FeedContactResult contact = feedAtContact(animalId);
        if (contact != FeedContactResult.ACTIVATED) {
            dropHand(gui);
            if (contact == FeedContactResult.ANIMAL_GONE)
                return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();
            if (contact == FeedContactResult.PLAYER_GONE)
                return Results.ERROR("Player disappeared");
            return Results.ERROR("Could not get close enough to animal");
        }
        gob = Finder.findGob(animalId);
        if (gob == null)
            return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();

            WaitPoseOrMsg wpom1 = new WaitPoseOrMsg(NUtils.player(),"gfx/borka/animaltease", new NAlias("The animal eye"));
            NUtils.addTask(wpom1);
            if(wpom1.isError())
            {
                gui.getInventory().dropOn(gui.getInventory().findFreeCoord(getGameUI().vhand));
            }
            else {
                WaitPoseOrMsg wpom2 = new WaitPoseOrMsg(NUtils.player(), "gfx/borka/idle", new NAlias("The animal loses"));
                NUtils.addTask(wpom2);
                if (wpom2.isError()) {
                    gui.getInventory().dropOn(gui.getInventory().findFreeCoord(getGameUI().vhand));
                    NUtils.player().addcustomol(new NCustomResult(NUtils.player(), "fail"));
                } else {
                    gob.addcustomol(new NCustomResult(gob, "success"));
                    if((Boolean) NConfig.get(NConfig.Key.ropeAfterFeeding))
                    {
                        WItem rope = gui.getInventory().getItem(new NAlias("Rope"), Leashed.class);
                        if(rope!=null)
                        {
                            NUtils.takeItemToHand(rope);
                            NUtils.activateItem(gob, false);
                            NUtils.addTask(new NTask() {
                                @Override
                                public boolean check() {
                                    if(getGameUI().vhand!=null)
                                    {
                                        return (((NGItem)getGameUI().vhand.item).getInfo(Leashed.class)!=null);
                                    }
                                    return false;
                                }
                            });
                            gui.getInventory().dropOn(gui.getInventory().findFreeCoord(getGameUI().vhand));
                        }
                    }
                }
            }
            feeded.add(gob.id);

        return Results.SUCCESS();
    }

    private static void dropHand(NGameUI gui) throws InterruptedException {
        if (getGameUI() != null && getGameUI().vhand != null)
            gui.getInventory().dropOn(gui.getInventory().findFreeCoord(getGameUI().vhand));
    }

    private Gob resolveAnimal() throws InterruptedException {
        if (targetAnimal != null)
            return Finder.findGob(targetAnimal.id);
        return Finder.findGob(krtters, feeded);
    }
}
