package nurgling.actions;

import haven.*;
import nurgling.*;
import nurgling.actions.bots.SelectArea;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.NTask;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.NEquipory;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

/**
 * Fills waterskins from a water source (barrel, cistern, well).
 * Two modes:
 * - useGlobalZone=false (default): prompts user to select a water zone
 * - useGlobalZone=true: uses NContext water specialisation area (local then global), errors if not found
 */
public class FillWaterskins implements Action {
    private static final long FILL_TIMEOUT_NS = 8_000_000_000L;

    protected final boolean useGlobalZone;

    public FillWaterskins() { this.useGlobalZone = false; }
    public FillWaterskins(boolean useGlobalZone) { this.useGlobalZone = useGlobalZone; }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Pair<Coord2d, Coord2d> area = null;

        if (useGlobalZone) {
            NContext context = new NContext(gui);
            NArea nArea = context.goToArea(Specialisation.SpecName.water);
            if (nArea == null) {
                return Results.ERROR("No water area found! Please create an area with 'water' specialization.");
            }
            if (!NUtils.navigateToArea(nArea, true)) {
                return Results.ERROR("Cannot reach water area");
            }
            area = nArea.getRCArea();
        } else {
            SelectArea insa;
            NUtils.getGameUI().msg("Please, select area with cistern or barrel");
            (insa = new SelectArea(Resource.loadsimg("baubles/waterRefiller"))).run(gui);
            area = insa.getRCArea();
        }

        if (area == null) {
            return Results.ERROR("no water area");
        }
        ArrayList<Gob> targets = new ArrayList<>();
        for (Gob cand : Finder.findGobs(area, new NAlias("barrel", "cistern", "well"))) {
            if (!NParser.isIt(cand, new NAlias("barrel")) ||
                    (NUtils.barrelHasContent(cand) && NParser.checkName(NUtils.getContentsOfBarrel(cand), "water")))
                targets.add(cand);
        }
        if (targets.isEmpty())
            return Results.ERROR("No containers with water");
        WItem wbelt = NUtils.getEquipment().findItem (NEquipory.Slots.BELT.idx);
        if(wbelt!=null)
        {
            if(wbelt.item.contents instanceof NInventory)
            {
                ArrayList<WItem> witems = ((NInventory) wbelt.item.contents).getItems(new NAlias("Waterskin"));
                for(WItem item : witems)
                {
                    NGItem ngItem = ((NGItem)item.item);
                    if(ngItem.content().isEmpty())
                    {
                        NUtils.takeItemToHand(item);
                        boolean filled = fillFromSources(gui, targets);
                        NUtils.transferToBelt();
                        if (!waitForFreeHand(gui))
                            return Results.ERROR("Could not return waterskin to belt");
                        if (!filled)
                            return Results.ERROR("Water source did not fill the waterskin");
                    }
                }
            }
        }
        if (!refillItemInEquip(gui,NUtils.getEquipment().findItem(NEquipory.Slots.LFOOT.idx),targets) ||
            !refillItemInEquip(gui,NUtils.getEquipment().findItem(NEquipory.Slots.RFOOT.idx),targets))
            return Results.ERROR("Water source did not fill the waterskin");
        // Refill buckets in hands
        if (!refillBucketInHand(gui,NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx),targets) ||
            !refillBucketInHand(gui,NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx),targets))
            return Results.ERROR("Water source did not fill the bucket");
        return Results.SUCCESS();
    }

    private boolean fillFromSources(NGameUI gui, ArrayList<Gob> targets) throws InterruptedException {
        WItem held = gui.vhand;
        if (held == null)
            return false;
        for (Gob target : targets) {
            if (NParser.isIt(target, new NAlias("barrel")) &&
                    (!NUtils.barrelHasContent(target) || !NParser.checkName(NUtils.getContentsOfBarrel(target), "water")))
                continue;
            if (!new PathFinder(target).run(gui).IsSuccess())
                continue;
            NUtils.activateItem(target);
            if (waitForWater(held))
                return true;
        }
        return false;
    }

    private boolean waitForWater(WItem held) throws InterruptedException {
        long deadline = System.nanoTime() + FILL_TIMEOUT_NS;
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return !((NGItem) held.item).content().isEmpty() || System.nanoTime() >= deadline;
            }
        });
        return !((NGItem) held.item).content().isEmpty();
    }

    private boolean waitForFreeHand(NGameUI gui) throws InterruptedException {
        long deadline = System.nanoTime() + FILL_TIMEOUT_NS;
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return gui.vhand == null || System.nanoTime() >= deadline;
            }
        });
        return gui.vhand == null;
    }

    boolean refillItemInEquip(NGameUI gui, WItem item, ArrayList<Gob> targets) throws InterruptedException
    {
        if(item!=null && item.item instanceof NGItem && NParser.checkName(((NGItem)item.item).name(), new NAlias("Waterskin", "Glass Jug"))) {
            NGItem ngItem = ((NGItem) item.item);
            if (ngItem.content().isEmpty()) {
                NUtils.takeItemToHand(item);
                boolean filled = fillFromSources(gui, targets);
                NUtils.getEquipment().wdgmsg("drop", -1);
                return waitForFreeHand(gui) && filled;
            }
        }
        return true;
    }

    boolean refillBucketInHand(NGameUI gui, WItem item, ArrayList<Gob> targets) throws InterruptedException
    {
        if(item!=null && item.item instanceof NGItem && NParser.checkName(((NGItem)item.item).name(), "Bucket")) {
            NGItem ngItem = ((NGItem) item.item);
            // Refill if bucket is empty or has water but not full (not "10l")
            boolean needRefill = ngItem.content().isEmpty();
            if (!needRefill) {
                String contentName = ngItem.content().get(0).name();
                // Has water but not full (full bucket shows "10l of Water")
                if (contentName.contains("Water") && !contentName.contains("10l")) {
                    needRefill = true;
                }
            }
            if (needRefill) {
                NUtils.takeItemToHand(item);
                boolean filled = fillFromSources(gui, targets);
                NUtils.getEquipment().wdgmsg("drop", -1);
                return waitForFreeHand(gui) && filled;
            }
        }
        return true;
    }


    public static boolean checkIfNeed() throws InterruptedException {
        boolean hasWaterskin = false;
        boolean hasWaterInWaterskin = false;
        
        WItem wbelt = NUtils.getEquipment().findItem(NEquipory.Slots.BELT.idx);
        if (wbelt != null) {
            if (wbelt.item.contents instanceof NInventory) {
                ArrayList<WItem> witems = ((NInventory) wbelt.item.contents).getItems(new NAlias("Waterskin"));
                if (!witems.isEmpty()) {
                    hasWaterskin = true;
                    for (WItem item : witems) {
                        NGItem ngItem = ((NGItem) item.item);
                        if (!ngItem.content().isEmpty()) {
                            if (ngItem.content().get(0).name().contains("Water")) {
                                hasWaterInWaterskin = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Check buckets in hands
        boolean hasBucket = false;
        boolean hasWaterInBucket = false;
        WItem bucket = NUtils.getEquipment().findBucket("Water");
        if (bucket != null) {
            hasBucket = true;
            NGItem ngItem = ((NGItem) bucket.item);
            if (!ngItem.content().isEmpty() && ngItem.content().get(0).name().contains("Water")) {
                hasWaterInBucket = true;
            }
        }
        
        // Need refill if we have containers but none of them have water
        if (hasWaterskin || hasBucket) {
            return !hasWaterInWaterskin && !hasWaterInBucket;
        }
        return false;
    }
}
