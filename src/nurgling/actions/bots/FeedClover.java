package nurgling.actions.bots;

import haven.Gob;
import haven.WItem;
import haven.res.ui.tt.leashed.Leashed;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.PathFinder;
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

    public static boolean isWildHorse(String name) {
        return name != null && name.contains("kritter/horse/horse");
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

        Results walk = new PathFinder(gob).run(gui);
        if (!walk.IsSuccess())
            return walk;
        gob = Finder.findGob(gob.id);
        if (gob == null)
            return targetAnimal != null ? Results.ERROR("Animal disappeared") : Results.SUCCESS();

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
