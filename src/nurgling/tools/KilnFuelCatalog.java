package nurgling.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Kiln firing table from the Ring of Brodgar wiki (Kiln page, fetched 2026-08-29).
 * Fuel units are branch counts; existing kiln bots must keep matching these values.
 */
public final class KilnFuelCatalog {
    public static final class Entry {
        public final String item;
        public final String realTime;
        public final String inGameTime;
        public final int fuelUnits;

        Entry(String item, String realTime, String inGameTime, int fuelUnits) {
            this.item = item;
            this.realTime = realTime;
            this.inGameTime = inGameTime;
            this.fuelUnits = fuelUnits;
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("Ashes (Pitbaked Goods)", "0:54:43", "3:00:01", 12),
            new Entry("Ashes (Board)", "0:13:41", "0:45:00", 3),
            new Entry("Ashes (Block of Wood)", "0:36:29", "2:00:00", 8),
            new Entry("Ashes (Branch)", "0:36:29", "2:00:00", 8),
            new Entry("Ashes (Bark)", "0:36:29", "2:00:00", 8),
            new Entry("Bone Ash", "0:25:12", "1:22:54", 6),
            new Entry("Branding Iron", "0:04:33", "0:14:58", 1),
            new Entry("Brick", "0:08:58", "0:29:30", 2),
            new Entry("Brick (Coade Clay)", "1:49:25", "6:00:00", 23),
            new Entry("Ceramic Knife", "0:54:33", "2:59:28", 12),
            new Entry("Clay Jar", "0:54:33", "2:59:28", 12),
            new Entry("Clay Pipe", "0:21:07", "1:09:28", 5),
            new Entry("Earthenware Platter", "0:36:18", "1:59:26", 8),
            new Entry("Fishwrap", "0:18:07", "0:59:36", 4),
            new Entry("Fruitroast", "0:18:07", "0:59:36", 4),
            new Entry("Garden Pot", "1:49:25", "6:00:00", 23),
            new Entry("Hand Impression", "0:21:10", "1:09:38", 5),
            new Entry("Malted Barley", "0:04:33", "0:14:58", 1),
            new Entry("Malted Wheat", "0:04:33", "0:14:58", 1),
            new Entry("Mushroom-Burst Glutton", "0:18:07", "0:59:36", 4),
            new Entry("Mug", "0:54:33", "2:59:28", 12),
            new Entry("Nutjerky", "0:18:07", "0:59:36", 4),
            new Entry("Pot", "1:49:25", "6:00:00", 23),
            new Entry("Porcelain Plate", "0:36:18", "1:59:26", 8),
            new Entry("Stoneware Vase", "0:36:18", "1:59:26", 8),
            new Entry("Stuffed Bird", "0:18:07", "0:59:36", 4),
            new Entry("Teapot", "0:54:33", "2:59:28", 12),
            new Entry("Toy Chariot", "0:21:10", "1:09:38", 5),
            new Entry("Treeplanter's Pot", "0:36:18", "1:59:26", 8),
            new Entry("Urn", "1:49:25", "6:00:00", 23)
    ));

    private KilnFuelCatalog() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static Entry find(String item) {
        if (item == null)
            return null;
        for (Entry entry : ENTRIES) {
            if (entry.item.equals(item))
                return entry;
        }
        return null;
    }

    /** Precursor names from {@code VSpec} Clay; Coade Clay fires as Brick (Coade Clay). */
    private static final Set<String> CLAYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "Acre Clay", "Ball Clay", "Sea Clay", "Gray Clay", "Cave Clay",
            "Pit Clay", "Soap Clay", "Coade Clay", "Bone Clay", "Potter's Clay",
            "Riverbed Clay"
    )));

    /** Precursor names from {@code VSpec} Bone Material, as used by BoneAshAction. */
    private static final Set<String> BONES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "Bone Material", "Adder Skeleton", "Crabshell", "Mammoth Tusk", "Moose Antlers",
            "Troll Skull", "Troll Tusks", "Trollbone", "Walrus Tusk", "Whale Bone Material",
            "Wishbone"
    )));

    /**
     * Fuel units (branches) for a kiln inventory item. Precursors are mapped to catalog
     * products; unknown names return empty — callers must not guess.
     */
    public static OptionalInt fuelUnitsFor(String itemName) {
        if (itemName == null || itemName.isEmpty())
            return OptionalInt.empty();
        String name = itemName;
        if (name.startsWith("Unfired "))
            name = name.substring("Unfired ".length());
        if (name.isEmpty())
            return OptionalInt.empty();

        if ("Coade Clay".equals(name))
            return unitsOf("Brick (Coade Clay)");
        if (CLAYS.contains(name))
            return unitsOf("Brick");
        if ("Board".equals(name))
            return unitsOf("Ashes (Board)");
        if ("Block of Wood".equals(name))
            return unitsOf("Ashes (Block of Wood)");
        if ("Branch".equals(name))
            return unitsOf("Ashes (Branch)");
        if ("Bark".equals(name))
            return unitsOf("Ashes (Bark)");
        if (BONES.contains(name))
            return unitsOf("Bone Ash");

        return unitsOf(name);
    }

    /**
     * Max fuel among items in one kiln (mixed load). Empty list is 0.
     * Empty optional if any name cannot be resolved.
     */
    public static OptionalInt maxFuelUnitsFor(Iterable<String> itemNames) {
        if (itemNames == null)
            return OptionalInt.empty();
        int max = 0;
        for (String itemName : itemNames) {
            OptionalInt units = fuelUnitsFor(itemName);
            if (!units.isPresent())
                return OptionalInt.empty();
            if (units.getAsInt() > max)
                max = units.getAsInt();
        }
        return OptionalInt.of(max);
    }

    private static OptionalInt unitsOf(String catalogItem) {
        Entry entry = find(catalogItem);
        return entry == null ? OptionalInt.empty() : OptionalInt.of(entry.fuelUnits);
    }
}
