package nurgling.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure rules for detecting stockpiles and attributing inventory
 * deltas to a pile gob. No UI, no DB.
 */
public final class StockpileStoragePolicy {
    private StockpileStoragePolicy() {}

    public static boolean isStockpileRes(String name) {
        return name != null && name.contains("stockpile");
    }

    public static final class Item {
        public final String name;
        public final double quality;

        public Item(String name, double quality) {
            this.name = name;
            this.quality = quality;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Item)) {
                return false;
            }
            Item item = (Item) o;
            return Double.compare(item.quality, quality) == 0 && Objects.equals(name, item.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, quality);
        }

        @Override
        public String toString() {
            return name + " q" + quality;
        }
    }

    public static final class FetchSplit {
        public final List<Item> keep;
        public final List<Item> restock;

        public FetchSplit(List<Item> keep, List<Item> restock) {
            this.keep = keep;
            this.restock = restock;
        }
    }

    public static List<Item> disappeared(List<Item> before, List<Item> after) {
        return unmatched(before, after);
    }

    public static List<Item> appeared(List<Item> before, List<Item> after) {
        return unmatched(after, before);
    }

    public static FetchSplit splitForFetch(List<Item> dumped, String name, double minQ, double maxQ, int count) {
        List<Item> keep = new ArrayList<>();
        List<Item> restock = new ArrayList<>();
        int kept = 0;
        for (Item item : dumped) {
            if (kept < count && name.equals(item.name) && item.quality >= minQ && item.quality <= maxQ) {
                keep.add(item);
                kept++;
            } else {
                restock.add(item);
            }
        }
        return new FetchSplit(keep, restock);
    }

    public static FetchSplit splitForFetch(List<Item> dumped, List<Item> needed) {
        List<Item> remainingNeeded = new ArrayList<>(needed);
        List<Item> keep = new ArrayList<>();
        List<Item> restock = new ArrayList<>();
        for (Item item : dumped) {
            int idx = remainingNeeded.indexOf(item);
            if (idx >= 0) {
                remainingNeeded.remove(idx);
                keep.add(item);
            } else {
                restock.add(item);
            }
        }
        return new FetchSplit(keep, restock);
    }

    /**
     * Turn one inventory slot into the items it actually holds.
     * Stack contents win over the shell; otherwise Amount is repeated.
     */
    public static List<Item> expandSlot(String name, double quality, int amount, List<Item> contents) {
        if (contents != null && !contents.isEmpty()) {
            return new ArrayList<>(contents);
        }
        if (name == null) {
            return List.of();
        }
        int n = Math.max(1, amount);
        List<Item> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Item(name, quality));
        }
        return out;
    }

    private static List<Item> unmatched(List<Item> source, List<Item> subtract) {
        List<Item> remaining = new ArrayList<>(subtract);
        List<Item> extra = new ArrayList<>();
        for (Item item : source) {
            int idx = remaining.indexOf(item);
            if (idx >= 0) {
                remaining.remove(idx);
            } else {
                extra.add(item);
            }
        }
        return extra;
    }

    /**
     * Stack contents finishing load looks like q0 items left and quality items arrived.
     * That is not a put/take against a stockpile.
     */
    public static boolean isStackResolution(List<Item> gone, List<Item> gained) {
        if (gone == null || gained == null || gone.isEmpty() || gained.isEmpty()) {
            return false;
        }
        if (gone.size() != gained.size()) {
            return false;
        }
        for (Item item : gone) {
            if (item.quality > 0) {
                return false;
            }
        }
        for (Item item : gained) {
            if (item.quality <= 0) {
                return false;
            }
        }
        List<String> goneNames = new ArrayList<>();
        List<String> gainedNames = new ArrayList<>();
        for (Item item : gone) {
            goneNames.add(item.name);
        }
        for (Item item : gained) {
            gainedNames.add(item.name);
        }
        goneNames.sort(String::compareTo);
        gainedNames.sort(String::compareTo);
        return goneNames.equals(gainedNames);
    }
}
