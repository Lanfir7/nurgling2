package nurgling.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
}
