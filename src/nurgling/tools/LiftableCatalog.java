package nurgling.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Known liftable gob resources for the Carry-many context action.
 * <p>
 * What <em>should</em> eventually be covered: RoB Category:Liftable_Structures (Aug 2026),
 * Category:Bush, and tree logs via {@link PrepQuota#isLog}. Paths here are only those already
 * present in this client (BuildCatalog, NContext.contcaps, NHitBox, MaterialFactory, VSpec, …).
 * Unmapped wiki names can be added later; do not invent resource paths.
 * <p>
 * Bushes in this client live under {@code gfx/terobjs/bushes/}, not {@code gfx/terobjs/plants/}
 * (crop plants are not liftable).
 */
public final class LiftableCatalog {
    private static final Set<String> STRUCTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "gfx/terobjs/anvil",
            "gfx/terobjs/barrel",
            "gfx/terobjs/beehive",
            "gfx/terobjs/birchbasket",
            "gfx/terobjs/bonechest",
            "gfx/terobjs/brazier",
            "gfx/terobjs/candelabrum",
            "gfx/terobjs/cauldron",
            "gfx/terobjs/cheeserack",
            "gfx/terobjs/chest",
            "gfx/terobjs/churn",
            "gfx/terobjs/coffer",
            "gfx/terobjs/compostbin",
            "gfx/terobjs/crate",
            "gfx/terobjs/cupboard",
            "gfx/terobjs/curdingtub",
            "gfx/terobjs/exquisitechest",
            "gfx/terobjs/furn/bed-sturdy",
            "gfx/terobjs/furn/cottagetable",
            "gfx/terobjs/furn/table-elegant",
            "gfx/terobjs/furn/table-rustic",
            "gfx/terobjs/furn/table-stone",
            "gfx/terobjs/gardenpot",
            "gfx/terobjs/grandstudydesk",
            "gfx/terobjs/htable",
            "gfx/terobjs/iconsign",
            "gfx/terobjs/lanternpost",
            "gfx/terobjs/largechest",
            "gfx/terobjs/leatherbasket",
            "gfx/terobjs/linencrate",
            "gfx/terobjs/loom",
            "gfx/terobjs/meatgrinder",
            "gfx/terobjs/metalcabinet",
            "gfx/terobjs/potterswheel",
            "gfx/terobjs/quern",
            "gfx/terobjs/still",
            "gfx/terobjs/stonecasket",
            "gfx/terobjs/strawbasket",
            "gfx/terobjs/studydesk",
            "gfx/terobjs/studydesk-big",
            "gfx/terobjs/swheel",
            "gfx/terobjs/thatchbasket",
            "gfx/terobjs/trough",
            "gfx/terobjs/ttub",
            "gfx/terobjs/vehicle/dugout",
            "gfx/terobjs/vehicle/plow",
            "gfx/terobjs/vehicle/rowboat",
            "gfx/terobjs/vehicle/skis-wilderness",
            "gfx/terobjs/vehicle/wheelbarrow",
            "gfx/terobjs/vehicle/wreckingball-fold",
            "gfx/terobjs/wbasket",
            "gfx/terobjs/woodbox"
    )));

    private LiftableCatalog() {
    }

    /**
     * @return true if {@code gobResName} is a tree log, a bush, a boulder, or a mapped liftable
     * structure. Unknown wiki names stay false until a known client path is added.
     */
    public static boolean isLiftable(String gobResName) {
        if (gobResName == null || gobResName.isEmpty())
            return false;
        if (PrepQuota.isLog(gobResName))
            return true;
        if (gobResName.startsWith("gfx/terobjs/bushes/"))
            return true;
        if (gobResName.startsWith("gfx/terobjs/bumlings"))
            return true;
        if (gobResName.startsWith("gfx/terobjs/furn/table"))
            return true;
        return STRUCTURES.contains(gobResName);
    }

    /**
     * Exact-name filter for the clicked gob so a barrel click moves only that barrel resource,
     * and an oak log click moves only oak logs.
     */
    public static NAlias objectFilter(String gobResName) {
        return new NAlias(gobResName);
    }
}
