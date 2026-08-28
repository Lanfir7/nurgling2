package nurgling.actions.bots;

import haven.Composite;
import haven.Coord2d;
import haven.Drawable;
import haven.Gob;
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

    static boolean isWalking(Gob gob) {
        if (gob == null)
            return false;
        Drawable drawable = gob.getattr(Drawable.class);
        if (!(drawable instanceof Composite))
            return false;
        String pose = ((Composite) drawable).current_pose;
        return pose != null && NParser.checkName(pose, WALK_POSES);
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
            if (hitboxesTouch(player, gob))
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

    Results closeIn(Gob gob) throws InterruptedException {
        long id = gob.id;
        for (int i = 0; i < CLOSE_IN_STEPS; i++) {
            Gob player = NUtils.player();
            gob = Finder.findGob(id);
            if (gob == null)
                return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();
            if (player == null)
                return Results.SUCCESS();
            if (hitboxesTouch(player, gob))
                return Results.SUCCESS();
            NUtils.clickGob(gob);
            NUtils.getUI().core.addTask(new WaitFeedContact(id));
            player = NUtils.player();
            gob = Finder.findGob(id);
            if (gob == null)
                return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();
            if (player != null && (hitboxesTouch(player, gob) || !isWalking(player)))
                return Results.SUCCESS();
        }
        return Results.SUCCESS();
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
        if (player == null || !hitboxesTouch(player, gob)) {
            Results walk = new DynamicPf(gob).run(gui);
            if (!walk.IsSuccess())
                return walk;
        }

        item = gui.getInventory().getItem(clover);
        if(item==null)
            return Results.ERROR("No clover");
        NUtils.takeItemToHand(item);

        gob = Finder.findGob(gob.id);
        if (gob == null) {
            dropHand(gui);
            return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();
        }
        player = NUtils.player();
        if (player == null || !hitboxesTouch(player, gob)) {
            Results close = closeIn(gob);
            if (!close.IsSuccess()) {
                dropHand(gui);
                return close;
            }
            gob = Finder.findGob(gob.id);
            if (gob == null) {
                dropHand(gui);
                return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();
            }
        }

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
