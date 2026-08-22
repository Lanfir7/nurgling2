package nurgling.tasks;

import haven.Gob;
import nurgling.NUtils;
import nurgling.conf.NChipperProp;
import nurgling.tools.Finder;

public class WaitChipperState extends NTask
{
    public WaitChipperState(Gob bumling, NChipperProp prop)
    {
        this.player = NUtils.player();
        this.bumling = bumling;
        this.prop = prop;
    }

    public WaitChipperState(Gob bumling)
    {
        this.player = NUtils.player();
        this.bumling = bumling;
        this.prop = null;
    }

    public WaitChipperState(Gob bumling, boolean ignoreInventoryFull)
    {
        this.player = NUtils.player();
        this.bumling = bumling;
        this.prop = null;
        this.ignoreInventoryFull = ignoreInventoryFull;
    }



    Gob player;
    Gob bumling;
    NChipperProp prop;
    boolean ignoreInventoryFull = false;

    public enum State
    {
        WORKING,
        BUMLINGNOTFOUND,
        BUMLINGFORDRINK,
        BUMLINGFOREAT,
        DANGER,
        TIMEFORPILE
    }

    State state = State.WORKING;
    @Override
    public boolean check()
    {
        boolean bumlingGone = Finder.findGob(bumling.id) == null;
        String pose = player != null ? player.pose() : null;
        boolean idle = pose != null && pose.contains("gfx/borka/idle");
        int space = -1;
        if (NUtils.getGameUI() != null && NUtils.getGameUI().getInventory() != null)
            space = NUtils.getGameUI().getInventory().calcFreeSpace();
        state = resolve(
                bumlingGone,
                NUtils.getEnergy(),
                NUtils.getStamina(),
                space,
                idle,
                prop != null && prop.autoeat,
                ignoreInventoryFull);
        return state != State.WORKING;
    }

    /**
     * Inventory dump takes priority over drink/eat: otherwise a full pack plus low stamina
     * retries Chip stone, and with a pickaxe the pose wait never ends.
     */
    static State resolve(
            boolean bumlingGone,
            double energy,
            double stamina,
            int freeSpace,
            boolean idle,
            boolean autoeat,
            boolean ignoreInventoryFull)
    {
        if (bumlingGone)
            return State.BUMLINGNOTFOUND;
        if (energy < 0.23)
            return State.DANGER;
        if (!ignoreInventoryFull && ((freeSpace >= 0 && freeSpace <= 1) || idle))
            return State.TIMEFORPILE;
        if (stamina <= 0.45)
            return State.BUMLINGFORDRINK;
        if (energy < 0.36 && autoeat)
            return State.BUMLINGFOREAT;
        return State.WORKING;
    }

    public State getState() {
        return state;
    }
}