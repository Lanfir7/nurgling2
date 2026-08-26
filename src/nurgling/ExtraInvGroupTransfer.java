package nurgling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extra-inventory grouping and transfer, matching Ender's ExtInventory:
 * unpack stacks, group by type/quality, transfer one item at a time (stockpile-safe).
 */
public final class ExtraInvGroupTransfer {
    public static final int TRANSFER_COUNT = 1;

    private ExtraInvGroupTransfer() {}

    public static final class Listed {
        public final String name;
        public final Double quality;
        public final List<Listed> contents;

        private Listed(String name, Double quality, List<Listed> contents) {
            this.name = name;
            this.quality = quality;
            this.contents = contents;
        }

        public static Listed item(String name, Double quality) {
            return new Listed(name, quality, List.of());
        }

        public static Listed stack(Listed... inner) {
            return new Listed(null, null, List.of(inner));
        }
    }

    public static List<Listed> unpack(List<Listed> items) {
        List<Listed> out = new ArrayList<>();
        unpackInto(items, out);
        return out;
    }

    private static void unpackInto(List<Listed> items, List<Listed> out) {
        for (Listed it : items) {
            if (it.contents != null && !it.contents.isEmpty()) {
                unpackInto(it.contents, out);
            } else {
                out.add(it);
            }
        }
    }

    /** Extra panel: never use stack-average overlay; only the item's own quality. */
    public static Double itemQuality(Double itemQuality, Double stackAverage) {
        if (itemQuality != null && itemQuality > 0) {
            return itemQuality;
        }
        return null;
    }

    public static String qualityLabel(double min, double max) {
        if (min <= 0 && max <= 0) {
            return "";
        }
        if (Math.abs(max - min) < 0.05) {
            return String.format(java.util.Locale.US, "q%.1f", min > 0 ? min : max);
        }
        return String.format(java.util.Locale.US, "q%.1f–%.1f", min, max);
    }

    public static String groupKey(String name, Double quality, NInventory.Grouping grouping) {
        if (name == null) {
            return "";
        }
        if (grouping == null || grouping == NInventory.Grouping.NONE) {
            return name;
        }
        double q = quality != null ? quantifyQuality(quality, grouping) : 0;
        return name + "@Q" + (int) q;
    }

    public static double quantifyQuality(double q, NInventory.Grouping grouping) {
        if (grouping == NInventory.Grouping.Q1) {
            return Math.floor(q);
        }
        if (grouping == NInventory.Grouping.Q5) {
            double floored = Math.floor(q);
            return floored - (floored % 5);
        }
        if (grouping == NInventory.Grouping.Q10) {
            double floored = Math.floor(q);
            return floored - (floored % 10);
        }
        return q;
    }

    public static Map<String, List<Listed>> group(List<Listed> unpacked, NInventory.Grouping grouping, Double minQuality) {
        Map<String, List<Listed>> map = new LinkedHashMap<>();
        for (Listed it : unpacked) {
            if (minQuality != null) {
                double q = it.quality != null ? it.quality : 0;
                if (q < minQuality) {
                    continue;
                }
            }
            String key = groupKey(it.name, it.quality, grouping);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }
        return map;
    }

    public static List<Listed> pick(List<Listed> items, boolean all, boolean reverse) {
        return pick(items, all, reverse, Comparator.comparing((Listed a) -> a.quality != null ? a.quality : 0.0).reversed());
    }

    public static <T> List<T> pick(List<T> items, boolean all, boolean reverse, Comparator<T> byQualityHighFirst) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(byQualityHighFirst);
        if (reverse) {
            Collections.reverse(sorted);
        }
        if (!all) {
            return List.of(sorted.get(0));
        }
        return sorted;
    }

    /**
     * One extra-list click: empty matching stacks via their wrappers first
     * (so they go to the stockpile instead of unpacking into inventory),
     * then send already-solo items.
     *
     * @param stackWrapper returns the stack identity for an item inside a stack, or null if solo
     */
    public static final class Op<T> {
        public final T target;
        public final int count;
        public final boolean fromStack;

        public Op(T target, int count, boolean fromStack) {
            this.target = target;
            this.count = count;
            this.fromStack = fromStack;
        }
    }

    public static List<Slot> matchingTopLevel(List<Slot> slots, String groupKey, NInventory.Grouping grouping) {
        if (slots == null || groupKey == null) {
            return List.of();
        }
        List<Slot> stacks = new ArrayList<>();
        List<Slot> solos = new ArrayList<>();
        for (Slot slot : slots) {
            if (!groupKey.equals(groupKey(slot.name, slot.quality, grouping))) {
                continue;
            }
            if (slot.stack) {
                stacks.add(slot);
            } else {
                solos.add(slot);
            }
        }
        List<Slot> out = new ArrayList<>(stacks.size() + solos.size());
        out.addAll(stacks);
        out.addAll(solos);
        return out;
    }

    /** After stacks empty, leftover last items sit in the grid as solos (or 1-item stacks). */
    public static boolean isLeftover(boolean stack, int stackSize) {
        return !stack || stackSize <= 1;
    }

    public static List<Slot> matchingLeftovers(List<Slot> slots, String groupKey, NInventory.Grouping grouping) {
        if (slots == null || groupKey == null) {
            return List.of();
        }
        List<Slot> out = new ArrayList<>();
        for (Slot slot : slots) {
            if (!groupKey.equals(groupKey(slot.name, slot.quality, grouping))) {
                continue;
            }
            if (!slot.stack) {
                out.add(slot);
            }
        }
        return out;
    }

    public static final class Slot {
        public final String name;
        public final Double quality;
        public final boolean stack;

        public Slot(String name, Double quality, boolean stack) {
            this.name = name;
            this.quality = quality;
            this.stack = stack;
        }

        public static Slot stack(String name, Double quality) {
            return new Slot(name, quality, true);
        }

        public static Slot solo(String name, Double quality) {
            return new Slot(name, quality, false);
        }
    }

    /** Ender ExtInventory.EXCLUDES — extra list is disabled for these window titles. */
    private static final java.util.Set<String> EXTRA_PANEL_EXCLUDES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Steelbox", "Pouch", "Frame", "Tub", "Fireplace", "Rack",
            "Pane mold", "Table", "Purse", "Archery Target", "Stack", "Belt"));

    /** Extra panel on containers: skip stack popups and Ender-excluded titles. */
    public static boolean shouldInstallExtraPanel(String windowTitle, boolean inContents) {
        if (inContents || windowTitle == null || windowTitle.isEmpty()) {
            return false;
        }
        return !EXTRA_PANEL_EXCLUDES.contains(windowTitle);
    }

    /** Ender ExtInventory.getTransferTargets(): flags, count=1, destination widget ids. */
    public static Object[] invxf2Args(int[] destWdgIds) {
        if (destWdgIds == null || destWdgIds.length == 0) {
            return null;
        }
        Object[] args = new Object[2 + destWdgIds.length];
        args[0] = 0;
        args[1] = 1;
        for (int i = 0; i < destWdgIds.length; i++) {
            args[2 + i] = destWdgIds[i];
        }
        return args;
    }

    public static <T> List<Op<T>> plan(List<T> items, java.util.function.Function<T, T> stackWrapper) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<T, Integer> stacks = new java.util.LinkedHashMap<>();
        List<T> solos = new ArrayList<>();
        for (T item : items) {
            T wrapper = stackWrapper.apply(item);
            if (wrapper != null) {
                stacks.merge(wrapper, 1, Integer::sum);
            } else {
                solos.add(item);
            }
        }
        List<Op<T>> out = new ArrayList<>();
        for (Map.Entry<T, Integer> e : stacks.entrySet()) {
            out.add(new Op<>(e.getKey(), e.getValue(), true));
        }
        for (T solo : solos) {
            out.add(new Op<>(solo, 1, false));
        }
        return out;
    }
}

