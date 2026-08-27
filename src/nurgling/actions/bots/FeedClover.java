package nurgling.actions.bots;

import haven.Coord2d;
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
import nurgling.tasks.NTask;
import nurgling.tasks.WaitPoseOrMsg;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;
import java.util.Arrays;

import static nurgling.NUtils.getGameUI;

public class FeedClover implements Action {
    static ArrayList<Long> feeded = new ArrayList<>();
    NAlias krtters =new NAlias(new ArrayList<String>(Arrays.asList("horse", "cattle", "boar", "goat", "sheep")), new ArrayList<String>(Arrays.asList("stallion", "mare")));
    NAlias clover = new NAlias("Clover");

    private final Gob targetAnimal;

    public FeedClover() {
        this(null);
    }

    public FeedClover(Gob targetAnimal) {
        this.targetAnimal = targetAnimal;
    }

    public static final double FEED_REACH_FALLBACK = MCache.tilesz.x * 2;

    public static boolean isWildHorse(String name) {
        return name != null && name.contains("kritter/horse/horse");
    }

    public static double maxHalfExtent(NHitBox box) {
        if (box == null || box.begin == null || box.end == null)
            return 0;
        return Math.max(
                Math.max(Math.abs(box.begin.x), Math.abs(box.end.x)),
                Math.max(Math.abs(box.begin.y), Math.abs(box.end.y)));
    }

    public static double feedReach(NHitBox player, NHitBox animal) {
        if (player == null || animal == null)
            return FEED_REACH_FALLBACK;
        return maxHalfExtent(player) + maxHalfExtent(animal) + MCache.tilehsz.x;
    }

    public static boolean closeEnoughToFeed(Coord2d playerRc, Coord2d animalRc, NHitBox player, NHitBox animal) {
        return DynamicPf.isWithinReach(playerRc, animalRc, feedReach(player, animal));
    }

    static NHitBox hitBox(Gob gob) {
        return gob != null && gob.ngob != null ? gob.ngob.hitBox : null;
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
        NHitBox playerHb = hitBox(player);
        NHitBox animalHb = hitBox(gob);
        double reach = feedReach(playerHb, animalHb);
        if (player == null || !closeEnoughToFeed(player.rc, gob.rc, playerHb, animalHb)) {
            Results walk = new DynamicPf(gob).withReachDistance(reach).run(gui);
            if (!walk.IsSuccess())
                return walk;
            gob = Finder.findGob(gob.id);
            if (gob == null)
                return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();
        }

        item = gui.getInventory().getItem(clover);
        if(item==null)
            return Results.ERROR("No clover");
        NUtils.takeItemToHand(item);
        NUtils.activateItem(gob, false);
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

    private Gob resolveAnimal() throws InterruptedException {
        if (targetAnimal != null)
            return Finder.findGob(targetAnimal.id);
        return Finder.findGob(krtters, feeded);
    }
}
