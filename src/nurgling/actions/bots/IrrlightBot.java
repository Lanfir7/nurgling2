package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;
import haven.Loading;
import haven.MenuGrid;
import haven.Resource;
import haven.Session;
import haven.Widget;
import haven.WItem;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.PathFinder;
import nurgling.actions.LightGob;
import nurgling.actions.Results;
import nurgling.actions.SelectFlowerAction;
import nurgling.actions.TakeItems2;
import nurgling.actions.TransferItems2;
import nurgling.iteminfo.NFoodInfo;
import nurgling.areas.NContext;
import nurgling.areas.NGlobalCoord;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitDuration;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.FoodContainer;
import nurgling.widgets.NMakewindow;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Farms Irrlights at a crucible.
 * <p>
 * Smelting can call an Irrlight ({@code gfx/kritter/irrbloss}, "Irrlight" in the inventory), so the
 * bot rolls for one by cycling a single lump of metal back and forth: "Smelt Metal Nuggets"
 * ({@code paginae/craft/nuggify}) turns a bar into nuggets, "Smelt Metal Bar"
 * ({@code paginae/craft/denuggify}) turns them back into the bar, forever. The Irrlight flies fast
 * and does not wait, so every wait this bot performs also watches for it: the moment one shows up
 * the craft is abandoned, the bot chases the critter down, and only then walks back to the crucible
 * and picks the cycle back up.
 * <p>
 * Each pass right-clicks the crucible, which smelting needs and which running off after an Irrlight
 * drops. If the crucible has gone cold it is refuelled with branches from the Fuel/Branch zone and
 * relit, so the cycle survives a burnout unattended.
 * <p>
 * Catching fills the inventory, so once fewer than {@link #MIN_FREE_SLOTS} cells are left the
 * Irrlights - and only the Irrlights, never the metal the cycle runs on - are delivered to their
 * output zone, and the bot walks back to the exact spot it started from.
 * <p>
 * Preconditions: the crucible is lit, the player stands next to it with either one metal bar or a
 * set of nuggets (any metal - the recipes are generic), a zone somewhere has "Irrlight" marked
 * as an output, and - only needed once the crucible burns out - a Fuel zone with the "Branch"
 * subtype.
 */
public class IrrlightBot implements Action {
    /** Bar -> nuggets. */
    private static final String NUGGIFY = "paginae/craft/nuggify";
    /** Nuggets -> bar. */
    private static final String DENUGGIFY = "paginae/craft/denuggify";

    /** Every metal bar is gfx/invobjs/bar-<metal>, every nugget gfx/invobjs/nugget-<metal>. */
    private static final String BAR = "gfx/invobjs/bar-";
    private static final String NUGGET = "gfx/invobjs/nugget-";
    private static final String IRRLIGHT_ITEM = "gfx/invobjs/irrbloss";
    /** Tooltip of gfx/invobjs/irrbloss, and therefore the key the area system files it under. */
    private static final String IRRLIGHT_NAME = "Irrlight";

    private static final NAlias IRRLIGHT = new NAlias("gfx/kritter/irrbloss");
    private static final String CRUCIBLE_RES = "gfx/terobjs/crucible";
    /** "gfx/terobjs/steelcrucible" does not contain this, so no exception is needed. */
    private static final NAlias CRUCIBLE = new NAlias(CRUCIBLE_RES);

    private static final double CRUCIBLE_RANGE = 200;
    static final long RECIPE_TIMEOUT = 10000;
    private static final long CRAFT_TIMEOUT = 15000;
    private static final long CHASE_TIMEOUT = 30000;
    private static final long PICKUP_TIMEOUT = 2000;
    private static final long COUNT_TIMEOUT = 5000;
    private static final long DRINK_TIMEOUT = 30000;
    private static final long GO_HOME_TIMEOUT = 20000;
    private static final long USE_TIMEOUT = 10000;
    /** Inventory count is still resolving; must not be treated as zero. */
    static final int COUNT_LOADING = -1;
    /** Re-click the Irrlight this often: each click re-aims the chase at where it has moved to. */
    private static final long CHASE_CLICK_PERIOD = 200;
    /** The crucible burns branches - what players call sticks - not coal. */
    private static final String BRANCH = "Branch";
    /** Branches carried back per refuel trip. */
    private static final int FUEL_BATCH = 8;
    /** Give up if the crucible still reads as empty after this many branches. */
    private static final int MAX_FUEL_ITEMS = 8;
    /**
     * How long a branch may sit in the hand before we read it as "the crucible will take no more".
     * Only ever paid once per refuel, at the point the station fills up.
     */
    private static final long FEED_TIMEOUT = 2000;
    /**
     * Crucible model bits, taken from the one place that defines them. Fuel and fire are separate
     * and not interchangeable: 4 is the flame, while the low bits say what is loaded - 0 empty,
     * 1 branches, 2 coal.
     */
    private static final int FUEL_MASK = LightObject.getConfig(CRUCIBLE_RES).fuelFlag;
    private static final int FIRE_BIT = LightObject.getConfig(CRUCIBLE_RES).fireFlag;
    private static final int MAX_CRAFT_FAILS = 5;
    private static final int MAX_PREP_ATTEMPTS = 3;
    /** Deliver the catch once the inventory is down to fewer free cells than this. */
    private static final int MIN_FREE_SLOTS = 5;
    /** Close enough to the starting spot that walking back would be a no-op. */
    private static final double HOME_TOLERANCE = 3;
    /** How long to let the crucible stream back in after a trip before calling it gone. */
    static final long CRUCIBLE_RELOAD_TIMEOUT = 15000;
    /** Same cutoffs {@link nurgling.actions.RestoreResources} uses; drink() itself still tops up to 0.9. */
    static final double STAMINA_RESTORE_BELOW = 0.5;
    static final double ENERGY_RESTORE_BELOW = 0.35;

    /** Nuggets win so a leftover bar is spent before a fresh nuggify. Null if there is no metal. */
    static String nextCycleRecipe(int bars, int nuggets) {
        if (nuggets > 0)
            return DENUGGIFY;
        if (bars > 0)
            return NUGGIFY;
        return null;
    }

    static String nextCycleOutput(int bars, int nuggets) {
        if (nuggets > 0)
            return BAR;
        if (bars > 0)
            return NUGGET;
        return null;
    }

    static boolean matchesNormalCrucible(String gobName) {
        return gobName != null && CRUCIBLE.matches(gobName);
    }

    static boolean crucibleStillValid(String gobName) {
        return matchesNormalCrucible(gobName);
    }

    /**
     * Spatial re-find is last resort. {@code hasStoredHash} is whether a stable hash was ever
     * captured, not whether that hash currently hits a loaded gob.
     */
    static boolean shouldRefindWhenMissing(boolean idPresent, boolean hasStoredHash) {
        return !idPresent && !hasStoredHash;
    }

    static boolean allowsNearbyFallback(boolean hasStoredHash) {
        return !hasStoredHash;
    }

    enum CrucibleResolve { LIVE_ID, HASH, WAIT_STORED, NEARBY, MISSING }

    /**
     * Live id, then a current hash hit, then wait for the stored identity. Nearby is only legal
     * when a stable hash was never captured. Empty {@code hashHitId} is not a hash hit.
     */
    static CrucibleResolve resolveAfterReload(Long currentIdPresent, boolean hasStoredHash, String hashHitId, Long nearbyNormalId) {
        if (currentIdPresent != null)
            return CrucibleResolve.LIVE_ID;
        if (hashHitId != null && !hashHitId.isEmpty())
            return CrucibleResolve.HASH;
        if (hasStoredHash)
            return CrucibleResolve.WAIT_STORED;
        if (nearbyNormalId != null)
            return CrucibleResolve.NEARBY;
        return CrucibleResolve.MISSING;
    }

    static Long resolvedCrucibleId(Long currentIdPresent, boolean hasStoredHash, String hashHitId, Long nearbyNormalId) {
        switch (resolveAfterReload(currentIdPresent, hasStoredHash, hashHitId, nearbyNormalId)) {
            case LIVE_ID:
                return currentIdPresent;
            case HASH:
                try {
                    return Long.valueOf(hashHitId);
                } catch (NumberFormatException e) {
                    return null;
                }
            case NEARBY:
                return nearbyNormalId;
            default:
                return null;
        }
    }

    static boolean hasStableHash(String storedHash) {
        return storedHash != null && !storedHash.isEmpty();
    }

    static boolean shouldCaptureChosenHash(CrucibleResolve resolve) {
        return resolve == CrucibleResolve.LIVE_ID
                || resolve == CrucibleResolve.HASH
                || resolve == CrucibleResolve.NEARBY;
    }

    /**
     * Nearby may capture a hash only once. A later spatial miss must not replace the identity
     * that was already locked in.
     */
    static String nextStableHash(String storedHash, CrucibleResolve resolve, String chosenHash) {
        if (hasStableHash(storedHash) && resolve == CrucibleResolve.NEARBY)
            return storedHash;
        if (!shouldCaptureChosenHash(resolve) || chosenHash == null)
            return storedHash;
        return chosenHash;
    }

    static boolean needsStaminaRestore(double stamina) {
        return stamina >= 0 && stamina < STAMINA_RESTORE_BELOW;
    }

    static boolean needsEnergyRestore(double energy) {
        return energy >= 0 && energy < ENERGY_RESTORE_BELOW;
    }

    static boolean needsResourceRestore(double stamina, double energy) {
        return needsStaminaRestore(stamina) || needsEnergyRestore(energy);
    }

    static boolean isExactConfiguredFood(String itemName, Collection<String> configuredNames) {
        if (itemName == null || configuredNames == null)
            return false;
        for (String configured : configuredNames) {
            if (itemName.equals(configured))
                return true;
        }
        return false;
    }

    static String firstConfiguredFoodName(List<String> inventoryNames, Collection<String> configuredNames) {
        if (inventoryNames == null)
            return null;
        for (String name : inventoryNames) {
            if (isExactConfiguredFood(name, configuredNames))
                return name;
        }
        return null;
    }

    static boolean shouldRefuseFoodForOvershoot(double energy, double foodEnergyPercent) {
        if (needsEnergyRestore(energy))
            return false;
        return energy + foodEnergyPercent / 100.0 >= 0.81;
    }

    static boolean shouldEatConfiguredFood(double energy, Double foodEnergyPercent) {
        if (foodEnergyPercent == null)
            return false;
        return needsEnergyRestore(energy) && !shouldRefuseFoodForOvershoot(energy, foodEnergyPercent);
    }

    enum EnergyRestoreDecision { SKIP, EAT, NO_CONFIGURED_FOOD }

    static EnergyRestoreDecision decideEnergyRestore(double energy, boolean hasConfiguredFood) {
        if (!needsEnergyRestore(energy))
            return EnergyRestoreDecision.SKIP;
        if (!hasConfiguredFood)
            return EnergyRestoreDecision.NO_CONFIGURED_FOOD;
        return EnergyRestoreDecision.EAT;
    }

    static boolean energyWakeIsFatal(Wake wake) {
        return wake == Wake.ERROR;
    }

    static boolean energyBiteWakeIsFatal(Wake bite) {
        return bite != Wake.DONE && bite != Wake.IRRLIGHT;
    }

    static String noConfiguredFoodMessage() {
        return "No configured food in inventory to restore energy";
    }

    static String eatFailedMessage() {
        return "Eat action failed for configured food";
    }

    static WaitPhase resourceRestoreWaitPhase() {
        return WaitPhase.DRINK;
    }

    static WaitPhase crucibleReloadWaitPhase() {
        return WaitPhase.WATCH;
    }

    static boolean matchesIrrbloss(String gobName) {
        return gobName != null && IRRLIGHT.matches(gobName);
    }

    static boolean holdsFuel(long modelAttr) {
        return (modelAttr & FUEL_MASK) != 0;
    }

    static boolean isAlight(long modelAttr) {
        return (modelAttr & FIRE_BIT) != 0;
    }

    static String outputItemName() {
        return IRRLIGHT_NAME;
    }

    static String fuelSubtype() {
        return BRANCH;
    }

    static Specialisation.SpecName fuelSpec() {
        return Specialisation.SpecName.fuel;
    }

    enum WaitPhase { DRINK, GO_HOME, PREPARE_CRUCIBLE, USE_CRUCIBLE, WATCH, CHASE, RECIPE }

    enum DeliverDecision { SKIP, TRANSFER, NO_ZONE }

    static boolean observesIrrbloss(WaitPhase phase) {
        return phase != WaitPhase.CHASE;
    }

    static WaitPhase recipeResourceWaitPhase() {
        return WaitPhase.RECIPE;
    }

    static boolean recipeWaitAborted(Wake wake) {
        return wake == Wake.IRRLIGHT || wake == Wake.ERROR;
    }

    /**
     * Inventory-tight delivery: bars/nuggets filling the pack with no resolved Irrlights is a
     * normal startup state, not a missing-zone error.
     */
    static DeliverDecision decideDelivery(int freeSpace, int resolvedIrrlightCount, int acceptedCount) {
        if (freeSpace >= MIN_FREE_SLOTS)
            return DeliverDecision.SKIP;
        if (resolvedIrrlightCount <= 0)
            return DeliverDecision.SKIP;
        if (acceptedCount <= 0)
            return DeliverDecision.NO_ZONE;
        return DeliverDecision.TRANSFER;
    }

    static int accumulateCount(int acc, String resName, boolean loading, String prefix) {
        if (acc < 0 || loading)
            return COUNT_LOADING;
        if (resName != null && resName.startsWith(prefix))
            return acc + 1;
        return acc;
    }

    static boolean shouldRetryCount(int bars, int nuggets) {
        return bars < 0 || nuggets < 0;
    }

    static boolean nothingToSmelt(int bars, int nuggets) {
        return bars == 0 && nuggets == 0;
    }

    private NMakewindow mwnd = null;
    private String openError = null;
    private String energyError = null;
    private Wake lastRecipeWait = Wake.DONE;
    /**
     * The crucible's stable handle. A gob id is only valid while the object stays in the object
     * cache: leave long enough that it unloads and coming back either finds it under a new id or
     * not yet re-sent at all. The hash survives both.
     */
    private String crucibleHash = null;
    /** Irrlights we chased and could not catch; touched from the UI thread too. */
    private final java.util.Set<Long> ignored = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Gob crucible = Finder.findGob(NUtils.player().rc, CRUCIBLE, null, CRUCIBLE_RANGE);
        if (crucible == null)
            return Results.ERROR("No crucible nearby: stand next to a lit crucible before starting");
        long crucibleId = crucible.id;
        rememberCrucible(crucible, CrucibleResolve.LIVE_ID);
        NGlobalCoord home = NUtils.bookmarkHere();

        NContext context = new NContext(gui);

        // Checked up front rather than at the first delivery: the bot fills its inventory over
        // hours, and finding out only then that the catch has nowhere to go would waste the run.
        // findOutsGlobal is a pure configuration lookup - unlike addOutItem it does not also
        // require the zone to be routable right now, so a cold chunk-nav cannot fail the start.
        if (NContext.findOutsGlobal(IRRLIGHT_NAME).isEmpty())
            return Results.ERROR("No output zone for Irrlights: mark a zone with \"" + IRRLIGHT_NAME
                    + "\" as an output so the catch has somewhere to go");

        int total = 0;
        int fails = 0;
        int preps = 0;
        String lastCraftError = null;

        Results delivery = maybeDeliver(gui, context, home, crucibleId);
        if (!delivery.IsSuccess())
            return delivery;

        while (true) {
            // An Irrlight outranks whatever else we were about to do.
            if (catchable() != null) {
                int got = chase(gui);
                if (got > 0) {
                    total += got;
                    gui.msg("Irrlight caught (" + total + " this run)");
                }
                delivery = maybeDeliver(gui, context, home, crucibleId);
                if (!delivery.IsSuccess())
                    return delivery;
                if (goHome(gui, home, crucibleId) == Wake.IRRLIGHT)
                    continue;
                continue;
            }

            if (awaitCrucible(crucibleId) == null) {
                if (catchable() != null)
                    continue;
                return Results.ERROR("The crucible is gone");
            }
            if (!burning(crucibleId)) {
                if (++preps > MAX_PREP_ATTEMPTS)
                    return Results.ERROR("The crucible will not stay lit after " + MAX_PREP_ATTEMPTS
                            + " attempts");
                gui.msg("Crucible is not burning, refuelling and lighting it");
                Results prep = prepareCrucible(gui, context, crucibleId, home);
                if (prep.isCycle)
                    continue;
                if (goHome(gui, home, crucibleId) == Wake.IRRLIGHT)
                    continue;
                if (!prep.IsSuccess())
                    return prep;
                continue;
            }

            int bars = count(gui, BAR);
            int nuggets = count(gui, NUGGET);
            if (shouldRetryCount(bars, nuggets)) {
                NUtils.addTask(new WaitDuration(100));
                continue;
            }
            /* Whichever half of the cycle we are holding. Nuggets win, so a leftover bar is
             * spent before a fresh batch of nuggets is made and the two never pile up. How many
             * nuggets make a bar is the server's business - it says so plainly enough if the
             * inventory is short. */
            if (nothingToSmelt(bars, nuggets))
                return Results.ERROR("Nothing to smelt: carry a metal bar or its nuggets");
            String recipe = nextCycleRecipe(bars, nuggets);
            String output = nextCycleOutput(bars, nuggets);
            int before = nuggets > 0 ? bars : nuggets;

            Wake drinkWake = drink(gui);
            if (drinkWake == Wake.IRRLIGHT)
                continue;
            if (drinkWake == Wake.ERROR)
                return Results.ERROR("NO WATER");

            Wake energyWake = restoreEnergy(gui);
            if (energyWake == Wake.IRRLIGHT)
                continue;
            if (energyWakeIsFatal(energyWake))
                return Results.ERROR(energyError != null ? energyError : noConfiguredFoodMessage());

            Wake used = useCrucible(gui, crucibleId);
            if (used == Wake.IRRLIGHT)
                continue;
            if (used != Wake.DONE)
                return Results.ERROR("The crucible is gone");

            Wake wake = openRecipe(gui, recipe, output);
            if (wake == Wake.IRRLIGHT)
                continue;
            if (wake != Wake.DONE)
                return Results.ERROR(openError != null ? openError
                        : "Could not open recipe " + recipe + " (does this character know it?)");

            NUtils.getUI().dropLastError();
            mwnd.wdgmsg("make", 0);
            final int target = before;
            final String grows = output;
            Watch watch = watch(() -> countRes(gui.getInventory(), grows) > target, CRAFT_TIMEOUT);
            switch (watch.wake) {
                case DONE:
                    fails = 0;
                    preps = 0;
                    break;
                case IRRLIGHT:
                    break;
                default:
                    if (watch.error != null)
                        lastCraftError = watch.error;
                    System.out.println("IrrlightBot: craft attempt failed (" + watch.wake + ", "
                            + watch.error + "), retry " + (fails + 1) + "/" + MAX_CRAFT_FAILS);
                    if (++fails >= MAX_CRAFT_FAILS) {
                        if (++preps > MAX_PREP_ATTEMPTS)
                            return Results.ERROR("Smelting keeps failing"
                                    + (lastCraftError != null ? ": " + lastCraftError : ""));
                        // Last net under the fire-bit check at the top of the loop: whatever else
                        // stalls a craft, a crucible that quietly went out looks the same from
                        // here, so try a refuel and relight before giving up on it.
                        gui.msg("Smelting keeps failing, refuelling and relighting the crucible");
                        Results prep = prepareCrucible(gui, context, crucibleId, home);
                        if (prep.isCycle)
                            break;
                        if (goHome(gui, home, crucibleId) == Wake.IRRLIGHT)
                            break;
                        if (!prep.IsSuccess())
                            return prep;
                        fails = 0;
                    }
                    break;
            }
        }
    }

    /**
     * Refuels the crucible with branches and lights it again.
     * <p>
     * {@link nurgling.actions.PrepareWorkStation} is deliberately not used: its crucible branch is
     * hardwired to Coal, and it would additionally demand a zone carrying the "Crucible"
     * specialisation when we already know exactly which crucible we are standing at. The shape is
     * otherwise the one every fuel action here uses - fetch the fuel, then feed the gob one item at
     * a time with takeItemToHand + activateItem.
     */
    private Results prepareCrucible(NGameUI gui, NContext context, long crucibleId, NGlobalCoord home)
            throws InterruptedException {
        if (catchable() != null)
            return Results.CYCLE();
        if (fuelled(crucibleId))
            return lightCrucible(gui, crucibleId);
        if (gui.getInventory().getItems(new NAlias(BRANCH)).isEmpty()) {
            if (catchable() != null)
                return Results.CYCLE();
            new TakeItems2(context, BRANCH, FUEL_BATCH, Specialisation.SpecName.fuel, BRANCH).run(gui);
            if (goHome(gui, home, crucibleId) == Wake.IRRLIGHT)
                return Results.CYCLE();
            if (gui.getInventory().getItems(new NAlias(BRANCH)).isEmpty())
                return Results.ERROR("No branches to refuel the crucible: they come from a Fuel zone"
                        + " with the \"" + BRANCH + "\" subtype");
        }

        /* Straight take -> activate -> hand free per branch, the same tight loop FuelToContainers
         * and fillCrucible use. Nothing is polled in between: the fuel bit and the emptied hand are
         * two results of the same server action, so it has already arrived by the time the hand
         * clears, and waiting on it separately added a dead second to every single branch.
         *
         * It keeps feeding past the point the fuel marker appears, because that marker only says
         * "branches are in there", not how many - a full load burns longer, and burning longer is
         * the whole point of refuelling. The station itself says when it has had enough: it stops
         * taking what we hold, and the branch is simply put back. */
        for (int fed = 0; fed < MAX_FUEL_ITEMS; fed++) {
            if (catchable() != null) {
                if (gui.vhand != null)
                    NUtils.dropToInv();
                return Results.CYCLE();
            }
            Gob station = awaitCrucible(crucibleId);
            if (station == null)
                return catchable() != null ? Results.CYCLE() : Results.ERROR("The crucible is gone");
            ArrayList<WItem> branches = gui.getInventory().getItems(new NAlias(BRANCH));
            if (branches.isEmpty())
                break;
            NUtils.takeItemToHand(branches.get(0));
            NUtils.activateItem(station);
            // Bounded, unlike WaitFreeHand, which kills the bot on its counter instead of failing:
            // if the station will not take what we hold, the item simply stays there.
            Wake fedWake = waitDuring(WaitPhase.PREPARE_CRUCIBLE, () -> gui.vhand == null, FEED_TIMEOUT);
            if (fedWake == Wake.IRRLIGHT) {
                if (gui.vhand != null)
                    NUtils.dropToInv();
                return Results.CYCLE();
            }
            if (fedWake != Wake.DONE) {
                NUtils.dropToInv();
                break;
            }
        }

        if (!fuelled(crucibleId))
            return Results.ERROR("The crucible would not take any branches (model marker "
                    + modelAttr(crucibleId) + ")");
        if (catchable() != null)
            return Results.CYCLE();
        return lightCrucible(gui, crucibleId);
    }

    private Results lightCrucible(NGameUI gui, long crucibleId) throws InterruptedException {
        Gob station = awaitCrucible(crucibleId);
        if (station == null)
            return catchable() != null ? Results.CYCLE() : Results.ERROR("The crucible is gone");
        return new LightGob(new ArrayList<>(Collections.singletonList(station.ngob.hash)), FIRE_BIT).run(gui);
    }

    /** Holds fuel of any kind - branches or coal - as opposed to standing empty. */
    private boolean fuelled(long crucibleId) throws InterruptedException {
        return holdsFuel(modelAttr(crucibleId));
    }

    /** Actually alight, which is what smelting needs - fuel alone is not enough. */
    private boolean burning(long crucibleId) throws InterruptedException {
        return isAlight(modelAttr(crucibleId));
    }

    private long modelAttr(long crucibleId) throws InterruptedException {
        Gob station = findCrucible(crucibleId);
        return (station == null || station.ngob == null) ? 0 : station.ngob.getModelAttribute();
    }

    private static String gobName(Gob g) {
        return (g == null || g.ngob == null) ? null : g.ngob.name;
    }

    private void rememberCrucible(Gob g, CrucibleResolve resolve) {
        if (g == null || g.ngob == null || g.ngob.hash == null)
            return;
        crucibleHash = nextStableHash(crucibleHash, resolve, g.ngob.hash);
    }

    private Gob refindNearby() {
        Gob player = NUtils.player();
        if (player == null || player.rc == null)
            return null;
        try {
            Gob g = Finder.findGob(player.rc, CRUCIBLE, null, CRUCIBLE_RANGE);
            if (g != null && crucibleStillValid(gobName(g)))
                return g;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Live validated id, else stored {@code ngob.hash} if that gob is still a normal crucible,
     * else wait for the same identity. Spatial nearby is only used when a hash was never captured.
     */
    private Gob findCrucible(long crucibleId) {
        Gob byId = Finder.findGob(crucibleId);
        Long currentIdPresent = (byId != null && crucibleStillValid(gobName(byId))) ? byId.id : null;

        boolean hasStoredHash = hasStableHash(crucibleHash);
        Gob byHash = null;
        String hashHitId = null;
        if (hasStoredHash) {
            byHash = Finder.findGob(crucibleHash);
            if (byHash != null && crucibleStillValid(gobName(byHash)))
                hashHitId = Long.toString(byHash.id);
        }

        Gob nearby = null;
        Long nearbyNormalId = null;
        if (shouldRefindWhenMissing(currentIdPresent != null, hasStoredHash)) {
            nearby = refindNearby();
            if (nearby != null && crucibleStillValid(gobName(nearby)))
                nearbyNormalId = nearby.id;
        }

        CrucibleResolve resolve = resolveAfterReload(currentIdPresent, hasStoredHash, hashHitId, nearbyNormalId);
        Gob chosen;
        switch (resolve) {
            case LIVE_ID:
                chosen = byId;
                break;
            case HASH:
                chosen = byHash;
                break;
            case NEARBY:
                chosen = nearby;
                break;
            default:
                return null;
        }
        if (shouldCaptureChosenHash(resolve))
            rememberCrucible(chosen, resolve);
        return chosen;
    }

    /**
     * The crucible, giving it time to stream back in after a trip. Wait is Irrbloss-aware so a
     * spawn during reload does not look like "the crucible is gone".
     */
    private Gob awaitCrucible(long crucibleId) throws InterruptedException {
        Gob g = findCrucible(crucibleId);
        if (g != null)
            return g;
        Wake w = waitDuring(crucibleReloadWaitPhase(), () -> findCrucible(crucibleId) != null, CRUCIBLE_RELOAD_TIMEOUT);
        if (w == Wake.IRRLIGHT)
            return null;
        return findCrucible(crucibleId);
    }

    /**
     * Delivers the caught Irrlights to their output zone once the inventory is nearly full, then
     * comes back. Only Irrlights are handed over - the bar or nuggets the cycle runs on are named
     * nowhere in the transfer, so they stay in the inventory.
     */
    private Results maybeDeliver(NGameUI gui, NContext context, NGlobalCoord home, long crucibleId)
            throws InterruptedException {
        int freeBefore = gui.getInventory().getFreeSpace();
        if (freeBefore >= MIN_FREE_SLOTS)
            return Results.SUCCESS();

        HashSet<String> targets = new HashSet<>();
        ArrayList<WItem> lights = collect(gui, IRRLIGHT_ITEM);
        if (lights == null)
            return Results.SUCCESS();
        int resolved = 0;
        for (WItem item : lights) {
            NGItem ngi = (NGItem) item.item;
            String name = ngi.name();
            if (name == null)
                continue;
            resolved++;
            // Resolved per item: output zones can be split by quality.
            if (context.addOutItem(name, null, ngi.quality != null ? ngi.quality : 1))
                targets.add(name);
        }
        DeliverDecision decision = decideDelivery(freeBefore, resolved, targets.size());
        if (decision == DeliverDecision.SKIP)
            return Results.SUCCESS();
        if (decision == DeliverDecision.NO_ZONE)
            return Results.ERROR("Inventory is full and no output zone accepts the Irrlights");

        gui.msg("Inventory nearly full, delivering the Irrlights");
        new TransferItems2(context, targets).run(gui);
        if (goHome(gui, home, crucibleId) == Wake.IRRLIGHT)
            return Results.SUCCESS();

        if (gui.getInventory().getFreeSpace() <= freeBefore)
            return Results.ERROR("Delivering the Irrlights freed no space: is their storage full?");
        return Results.SUCCESS();
    }

    /**
     * Right-clicks the crucible to put it in use, which smelting requires. Sent before the recipe
     * is opened, so the server has already ordered the two by the time we press craft.
     */
    private Wake useCrucible(NGameUI gui, long crucibleId) throws InterruptedException {
        Gob crucible = awaitCrucible(crucibleId);
        if (crucible == null)
            return catchable() != null ? Wake.IRRLIGHT : Wake.ERROR;
        if (catchable() != null)
            return Wake.IRRLIGHT;
        NUtils.rclickGob(crucible);
        final Coord2d rc = crucible.rc;
        return waitDuring(WaitPhase.USE_CRUCIBLE, () -> {
            Gob player = NUtils.player();
            if (player == null)
                return false;
            String pose = player.pose();
            return pose != null && pose.contains("gfx/borka/idle")
                    && player.rc.dist(rc) <= 50;
        }, USE_TIMEOUT);
    }

    /**
     * Chases every Irrlight in sight until it is caught or gone.
     *
     * @return how many were caught this pass.
     */
    private int chase(NGameUI gui) throws InterruptedException {
        int got = 0;
        Gob target;
        while ((target = catchable()) != null) {
            long id = target.id;
            final int before = count(gui, IRRLIGHT_ITEM);
            gui.msg("Irrlight! Chasing...");
            long deadline = System.currentTimeMillis() + CHASE_TIMEOUT;
            for (Gob g = target; g != null; g = Finder.findGob(id)) {
                if (System.currentTimeMillis() >= deadline)
                    break;
                NUtils.rclickGob(g);
                NUtils.addTask(new WaitDuration(CHASE_CLICK_PERIOD));
            }
            // The gob is gone (or we gave up); the item shows up a beat later.
            waitFor(() -> countRes(gui.getInventory(), IRRLIGHT_ITEM) > before, PICKUP_TIMEOUT);
            int now = count(gui, IRRLIGHT_ITEM);
            if (now > before) {
                got += now - before;
            } else {
                // Either out of reach or gone. Either way stop counting it as a target, or a
                // stubborn one would keep the bot away from the crucible forever.
                ignored.add(id);
                gui.msg(Finder.findGob(id) != null
                        ? "Could not reach the Irrlight, giving up on it"
                        : "The Irrlight got away");
            }
        }
        NUtils.getUI().dropLastError();
        return got;
    }

    /** Nearest Irrlight we have not already given up on, or null. Safe on either thread. */
    private Gob catchable() {
        Gob player = NUtils.player();
        Gob best = null;
        double bestd = Double.MAX_VALUE;
        for (Gob g : Finder.findGobs(IRRLIGHT)) {
            if (ignored.contains(g.id))
                continue;
            double d = (player == null) ? 0 : g.rc.dist(player.rc);
            if (best == null || d < bestd) {
                bestd = d;
                best = g;
            }
        }
        return best;
    }

    /**
     * Walks back to where the player stood when the bot started. The bookmark is grid-relative, so
     * it survives the chunk-nav hops a delivery to a distant storage takes.
     */
    private Wake goHome(NGameUI gui, NGlobalCoord home, long crucibleId) throws InterruptedException {
        Gob player = NUtils.player();
        Coord2d spot = (home != null) ? home.getCurrentCoord() : null;
        if (player != null && spot != null && player.rc.dist(spot) < HOME_TOLERANCE)
            return Wake.DONE;
        if (catchable() != null)
            return Wake.IRRLIGHT;
        if (spot != null) {
            NUtils.lclick(spot);
            Wake arrived = waitDuring(WaitPhase.GO_HOME, () -> {
                Gob p = NUtils.player();
                Coord2d s = home.getCurrentCoord();
                return p != null && s != null && p.rc.dist(s) < HOME_TOLERANCE;
            }, GO_HOME_TIMEOUT);
            if (arrived == Wake.IRRLIGHT || arrived == Wake.DONE)
                return arrived;
        }
        if (NUtils.navigateTo(home))
            return catchable() != null ? Wake.IRRLIGHT : Wake.DONE;
        Gob crucible = findCrucible(crucibleId);
        if (crucible != null)
            new PathFinder(crucible).run(gui);
        return catchable() != null ? Wake.IRRLIGHT : Wake.DONE;
    }

    private Wake drink(NGameUI gui) throws InterruptedException {
        double stamina = NUtils.getStamina();
        if (stamina < 0 || stamina >= 0.9)
            return Wake.DONE;
        if (catchable() != null)
            return Wake.IRRLIGHT;
        NUtils.getUI().dropLastError();
        MenuGrid.PagButton drinkBtn = null;
        for (MenuGrid.Pagina pag : NUtils.getGameUI().menu.paginae) {
            try {
                if (pag.button() != null && pag.button().name().equals("Drink")) {
                    drinkBtn = pag.button();
                    break;
                }
            } catch (Loading l) {
                // Recipe button still loading; skip this tick.
            }
        }
        if (drinkBtn == null)
            return Wake.DONE;
        while ((stamina = NUtils.getStamina()) >= 0 && stamina < 0.9) {
            if (catchable() != null)
                return Wake.IRRLIGHT;
            drinkBtn.use(new MenuGrid.Interaction(1, 0));
            Wake sip = waitDuring(WaitPhase.DRINK, () -> {
                double s = NUtils.getStamina();
                return s < 0 || s >= 0.9;
            }, DRINK_TIMEOUT);
            if (sip == Wake.IRRLIGHT)
                return Wake.IRRLIGHT;
            if (sip == Wake.ERROR)
                return Wake.ERROR;
            if (sip != Wake.DONE)
                break;
        }
        return Wake.DONE;
    }

    /**
     * Inventory-only energy top-up. {@link nurgling.actions.RestoreResources} / {@code Eater} would
     * leave the crucible on an uninterruptible trip, so Irrbloss is watched here via
     * {@link #waitDuring} instead. Missing configured food is an error, not a zone walk.
     */
    private Wake restoreEnergy(NGameUI gui) throws InterruptedException {
        energyError = null;
        double energy = NUtils.getEnergy();
        if (!needsEnergyRestore(energy))
            return Wake.DONE;
        if (catchable() != null)
            return Wake.IRRLIGHT;
        Collection<String> configured = FoodContainer.getFoodNames();
        WItem food = firstConfiguredFoodItem(gui.getInventory().getItems(NFoodInfo.class), configured);
        if (decideEnergyRestore(energy, food != null) == EnergyRestoreDecision.NO_CONFIGURED_FOOD) {
            energyError = noConfiguredFoodMessage();
            return Wake.ERROR;
        }
        NUtils.getUI().dropLastError();
        Gob player = NUtils.player();
        if (player != null)
            NUtils.clickGob(player);
        Wake idle = waitDuring(resourceRestoreWaitPhase(), () -> {
            Gob p = NUtils.player();
            if (p == null)
                return false;
            String pose = p.pose();
            return pose != null && pose.contains("gfx/borka/idle");
        }, USE_TIMEOUT);
        if (idle == Wake.IRRLIGHT)
            return Wake.IRRLIGHT;
        if (idle == Wake.ERROR) {
            energyError = eatFailedMessage();
            return Wake.ERROR;
        }
        while (needsEnergyRestore(energy = NUtils.getEnergy())) {
            if (catchable() != null)
                return Wake.IRRLIGHT;
            food = firstConfiguredFoodItem(gui.getInventory().getItems(NFoodInfo.class), configured);
            if (food == null) {
                energyError = noConfiguredFoodMessage();
                return Wake.ERROR;
            }
            Results eaten = new SelectFlowerAction("Eat", food).run(gui);
            if (!eaten.IsSuccess()) {
                energyError = eatFailedMessage();
                return Wake.ERROR;
            }
            final double before = energy;
            Wake bite = waitDuring(resourceRestoreWaitPhase(), () -> {
                double e = NUtils.getEnergy();
                return e < 0 || e > before || !needsEnergyRestore(e);
            }, DRINK_TIMEOUT);
            if (bite == Wake.IRRLIGHT)
                return Wake.IRRLIGHT;
            if (bite == Wake.ERROR) {
                energyError = eatFailedMessage();
                return Wake.ERROR;
            }
            if (energyBiteWakeIsFatal(bite)) {
                energyError = eatFailedMessage();
                return Wake.ERROR;
            }
        }
        return Wake.DONE;
    }

    private static WItem firstConfiguredFoodItem(ArrayList<WItem> foods, Collection<String> configuredNames) {
        if (foods == null)
            return null;
        for (WItem item : foods) {
            if (item == null || !(item.item instanceof NGItem))
                continue;
            if (isExactConfiguredFood(((NGItem) item.item).name(), configuredNames))
                return item;
        }
        return null;
    }

    /**
     * Activates a craft recipe and waits for its window. Stores the window in {@link #mwnd}.
     *
     * @param output resource prefix the recipe must produce, so we never craft into the wrong window.
     */
    private Wake openRecipe(NGameUI gui, String recipe, String output) throws InterruptedException {
        MenuGrid.PagButton button = recipeButton(gui, recipe);
        if (button == null)
            return recipeWaitAborted(lastRecipeWait) ? lastRecipeWait : Wake.TIMEOUT;
        final NMakewindow old = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
        openError = null;
        NUtils.getUI().dropLastError();
        gui.menu.use(button, new MenuGrid.Interaction(), false);
        Watch watch = watch(() -> {
            NMakewindow m = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
            return m != null && m != old && !m.inputs.isEmpty() && specHas(m.outputs, output);
        }, RECIPE_TIMEOUT);
        if (watch.wake == Wake.DONE) {
            mwnd = gui.craftwnd.makeWidget;
            return Wake.DONE;
        }
        if (watch.wake == Wake.TIMEOUT) {
            NMakewindow m = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
            openError = (m == null || m == old)
                    ? "Recipe " + recipe + " did not open (does this character know it?)"
                    : "Recipe " + recipe + " opened as \"" + m.rcpnm + "\" producing "
                            + specNames(m.outputs) + ", expected " + output + "*";
        } else {
            openError = watch.error;
        }
        return watch.wake;
    }

    private static String specNames(List<NMakewindow.Spec> specs) {
        StringBuilder sb = new StringBuilder();
        for (NMakewindow.Spec s : specs) {
            if (sb.length() > 0)
                sb.append(", ");
            try {
                sb.append(s.res == null ? "?" : s.res.get().name);
            } catch (Loading l) {
                sb.append("<loading>");
            }
        }
        return sb.toString();
    }

    /**
     * Finds the recipe among the actions this character knows. Reads the cached resource name so a
     * pagina whose resource has not been fetched yet does not have to be loaded just to be skipped.
     */
    private MenuGrid.PagButton recipeButton(NGameUI gui, String recipe) throws InterruptedException {
        lastRecipeWait = Wake.DONE;
        long deadline = System.currentTimeMillis() + RECIPE_TIMEOUT;
        do {
            MenuGrid.Pagina found = null;
            synchronized (gui.menu.paginae) {
                for (MenuGrid.Pagina pag : gui.menu.paginae) {
                    if (recipe.equals(resnm(pag))) {
                        found = pag;
                        break;
                    }
                }
            }
            if (found != null) {
                try {
                    return found.button();
                } catch (Loading l) {
                    // Resource still on its way; fall through and retry.
                }
            }
            lastRecipeWait = waitDuring(recipeResourceWaitPhase(), () -> false, 100);
            if (recipeWaitAborted(lastRecipeWait))
                return null;
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    private static String resnm(MenuGrid.Pagina pag) {
        if (pag.res instanceof Session.CachedRes.Ref)
            return ((Session.CachedRes.Ref) pag.res).resnm();
        try {
            return pag.res().name;
        } catch (Loading l) {
            return null;
        }
    }

    private static boolean specHas(List<NMakewindow.Spec> specs, String prefix) {
        for (NMakewindow.Spec s : specs) {
            if (prefix.equals(specPrefix(s, prefix)))
                return true;
        }
        return false;
    }

    private static String specPrefix(NMakewindow.Spec s, String prefix) {
        try {
            if (s.res != null && s.res.get().name.startsWith(prefix))
                return prefix;
        } catch (Loading l) {
            // Not loaded yet: the caller polls, so it will match on a later tick.
        }
        return null;
    }

    enum Wake {DONE, IRRLIGHT, ERROR, TIMEOUT}

    /** Evaluated on the UI thread, so it may walk widget trees directly. */
    private interface Cond {
        boolean done();
    }

    /**
     * Waits for {@code cond}, but wakes up early for an Irrlight or a server error. Every wait the
     * bot performs goes through here, which is what keeps the catch responsive: the check runs once
     * per frame, so the critter is spotted the frame it spawns.
     */
    private static class Watch extends NTask {
        private final IrrlightBot bot;
        private final Cond cond;
        private final long deadline;
        Wake wake = Wake.TIMEOUT;
        String error = null;

        Watch(IrrlightBot bot, Cond cond, long timeoutMs) {
            this.bot = bot;
            this.cond = cond;
            this.deadline = System.currentTimeMillis() + timeoutMs;
            this.infinite = true;
        }

        @Override
        public boolean check() {
            if (bot.catchable() != null) {
                wake = Wake.IRRLIGHT;
                return true;
            }
            String err = NUtils.getUI().getLastError();
            if (err != null) {
                error = err;
                wake = Wake.ERROR;
                return true;
            }
            if (cond.done()) {
                wake = Wake.DONE;
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                wake = Wake.TIMEOUT;
                return true;
            }
            return false;
        }
    }

    private Watch watch(Cond cond, long timeoutMs) throws InterruptedException {
        Watch w = new Watch(this, cond, timeoutMs);
        NUtils.addTask(w);
        return w;
    }

    private Wake waitDuring(WaitPhase phase, Cond cond, long timeoutMs) throws InterruptedException {
        if (observesIrrbloss(phase))
            return watch(cond, timeoutMs).wake;
        return waitFor(cond, timeoutMs) ? Wake.DONE : Wake.TIMEOUT;
    }

    /** Plain wait, without the Irrlight short circuit: used while one is already being chased. */
    private static boolean waitFor(Cond cond, long timeoutMs) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        final boolean[] met = {false};
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                if (cond.done()) {
                    met[0] = true;
                    return true;
                }
                return System.currentTimeMillis() >= deadline;
            }
        });
        return met[0];
    }

    private static int count(NGameUI gui, String prefix) throws InterruptedException {
        final NInventory inv = gui.getInventory();
        final int[] result = {COUNT_LOADING};
        waitFor(() -> {
            result[0] = countRes(inv, prefix);
            return result[0] >= 0;
        }, COUNT_TIMEOUT);
        return result[0];
    }

    private static ArrayList<WItem> collect(NGameUI gui, String prefix) throws InterruptedException {
        final NInventory inv = gui.getInventory();
        final ArrayList<WItem> result = new ArrayList<>();
        final int[] n = {COUNT_LOADING};
        waitFor(() -> {
            result.clear();
            n[0] = collectRes(inv, prefix, result);
            return n[0] >= 0;
        }, COUNT_TIMEOUT);
        if (n[0] < 0)
            return null;
        return result;
    }

    private static int countRes(NInventory inv, String prefix) {
        return collectRes(inv, prefix, null);
    }

    /**
     * Counts inventory items by resource path, which is the only stable handle here: the display
     * names are not uniform ("Bar of Wrought Iron" but "Copper Nugget"). Must run on the UI thread.
     * <p>
     * A bundled stack is a container widget holding the real items and carries no quality of its
     * own, so it is descended into and never counted itself - which is what lets the bot run with
     * bundling left on, where ten nuggets sit in a single cell.
     */
    private static int collectRes(NInventory inv, String prefix, List<WItem> out) {
        if (inv == null)
            return 0;
        synchronized (inv.ui) {
            return collectRes(inv.child, prefix, out);
        }
    }

    private static int collectRes(Widget first, String prefix, List<WItem> out) {
        int n = 0;
        List<WItem> buf = (out != null) ? new ArrayList<WItem>() : null;
        for (Widget w = first; w != null; w = w.next) {
            if (!(w instanceof WItem))
                continue;
            WItem item = (WItem) w;
            if (item.item.contents != null) {
                if (item.item.contents instanceof ItemStack) {
                    int sub = collectRes(item.item.contents.child, prefix, buf != null ? buf : out);
                    if (sub < 0)
                        return COUNT_LOADING;
                    n += sub;
                }
                continue;
            }
            String resName = null;
            boolean loading = false;
            try {
                Resource res = item.item.getres();
                if (res != null)
                    resName = res.name;
            } catch (Loading l) {
                loading = true;
            }
            int next = accumulateCount(n, resName, loading, prefix);
            if (next < 0)
                return COUNT_LOADING;
            if (next > n && buf != null)
                buf.add(item);
            n = next;
        }
        if (out != null && buf != null)
            out.addAll(buf);
        return n;
    }
}
